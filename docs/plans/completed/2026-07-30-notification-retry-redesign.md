# Notification Retry Mechanism Redesign

## Overview

Redesign the error-notification retry mechanism (`ErrorNotificationRepeater`) which currently
causes notification spam and has confusing configuration semantics.

**Problems being solved:**
- Permanent failures (invalid address, broken template) are retried up to ~145 times over 24h
  with a flat 10-minute interval (defaults: `TTL = 24h`, `DELAY = 10m`, `MIN_TRY_COUNT = 10`)
- `minTryCount` is a *minimum* that overrides TTL (TTL is not checked until `tryingCount > minTryCount`),
  while users expect a *maximum* attempts limit
- No distributed coordination: `synchronized` only protects within one JVM; multiple replicas
  re-send the same ERROR rows → duplicate emails
- Unordered pagination over a mutating result set (`findAllByState(ERROR, PageRequest)` in
  `while(true)`) can process the same row twice within one tick
- No backoff/jitter, no per-row scheduling (`lastTryingDate` is written but never read)
- One tick synchronously drains the whole ERROR backlog without limit → storm after SMTP recovery
- Bulk mail amplifies everything: cancellation does not stop retries of ERROR rows; one ERROR row
  keeps the whole bulk mail in TRYING_TO_DISPATCH; one EXPIRED row marks the whole bulk mail ERROR
- Config uses raw int milliseconds and magic `-1`

**Solution (design validated in brainstorming session):**
- Per-row scheduling via `next_retry_at` + exponential backoff with jitter
- Row claiming via `FOR UPDATE SKIP LOCKED` + lease (multi-replica safe, no new SENDING state)
- Full failure classification: `PERMANENT` → new terminal state `FAILED` immediately;
  `TRANSIENT` → retry budget up to 24h
- Explicit limits: `max-attempts` AND `retry-window` (whichever hits first → `EXPIRED`)
- Shared `NotificationRetryPolicy` used by both the synchronous path (`holdError`) and the repeater
- `retry.enabled: false` support (first failure goes straight to terminal state; manual re-drive still works)
- Bulk mail: cancellation atomically cancels ERROR rows too; status synchronizer rewritten to
  priority chain that understands FAILED
- New config block `ecos-notifications.retry` with `Duration` types; old
  `ecos-notifications.error-notification.*` properties kept as `@Deprecated`, ignored with WARN
  (`ignoreUnknownFields = false` would otherwise crash existing stands)

## Context (from discovery)

Files/components involved:
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/service/ErrorNotificationRepeater.kt` — full rewrite
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/service/NotificationCommandResultHolder.kt` — integrate retry policy
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/service/NotificationDao.kt` — claim method
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/repo/NotificationRepository.kt` — claim query
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/repo/NotificationEntity.kt` — new columns
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/dto/NotificationDto.kt` + `converter/NotificationConverter.kt` — new fields
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/NotificationState.kt` — new FAILED state
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/api/commands/UnsafeSendNotificationCommandExecutor.kt` — permanent exception at template resolution
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/service/NotificationSenderServiceImpl.kt` — permanent exception at render
- `src/main/java/ru/citeck/ecos/notifications/service/senders/EmailNotificationSender.kt` — partial SMTP failure handling
- `src/main/java/ru/citeck/ecos/notifications/domain/bulkmail/service/BulkMailDao.kt` — atomic cancellation incl. ERROR
- `src/main/java/ru/citeck/ecos/notifications/domain/bulkmail/service/BulkMailStatusSynchronizer.kt` — priority chain rewrite
- `src/main/java/ru/citeck/ecos/notifications/domain/notification/api/records/NotificationRecords.kt` — manual re-drive mutation
- `src/main/java/ru/citeck/ecos/notifications/config/ApplicationProperties.java` + `NotificationsDefault.java` — new retry config
- `src/main/resources/config/application.yml` — new defaults, mail timeouts
- `src/main/resources/db/changelog/` — Liquibase migration
- `src/test/java/ru/citeck/ecos/notifications/HandleErrorNotificationTest.kt` — rewrite

Related patterns found:
- `AwaitingNotificationDispatcher` already uses the bounded model (`batchSize` limit per tick,
  native query with limit) — the new repeater follows the same shape
- Tests extending `BaseMailTest` use GreenMail; `HandleErrorNotificationTest` calls
  `errorNotificationRepeater.handleErrors()` directly (no waiting for schedule)
- Liquibase migrations live in `src/main/resources/db/changelog/` (one changelog file per change,
  included from master changelog — follow existing naming convention in that directory)

Dependencies identified:
- `BulkMailStatusSynchronizer` reads notification states — MUST be updated in the same release
  as the new FAILED state, otherwise bulk mails hang in TRYING_TO_DISPATCH forever
- External consumers see states via Records API (`NotificationRecords`) — FAILED is additive
- PostgreSQL-specific SQL (`FOR UPDATE SKIP LOCKED`) — acceptable, project is PostgreSQL-only

## Development Approach

- **Testing approach**: Regular (code first, then tests in the same task)
- Complete each task fully before moving to the next
- Make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - tests are not optional - they are a required part of the checklist
  - write unit tests for new functions/methods
  - write unit tests for modified functions/methods
  - add new test cases for new code paths
  - update existing test cases if behavior changes
  - tests cover both success and error scenarios
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- Run tests after each change: `./mvnw test -Dtest=<TestClass>` for a single class,
  `./mvnw clean test` for the full suite
- Maintain backward compatibility: old config property names must not crash startup;
  existing ERROR rows must be picked up after migration

## Testing Strategy

- **Unit tests**: required for every task (see Development Approach above).
  Pure-logic components (`NotificationRetryPolicy`, `EmailSendFailureClassifier`) get plain
  JUnit tests without Spring context
- **Integration tests**: Spring Boot tests extending `BaseMailTest` (GreenMail SMTP) for the
  repeater, holder, bulk mail flows — same style as existing `HandleErrorNotificationTest`
- No UI e2e tests in this project

## Progress Tracking
- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix
- Update plan if implementation deviates from original scope
- Keep plan in sync with actual work done

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): tasks achievable within this codebase
- **Post-Completion** (no checkboxes): release notes, stand config migration, consumer notifications

## Implementation Steps

### Task 1: Schema migration — new columns and FAILED state groundwork
- [x] add Liquibase changelog (follow naming convention in `src/main/resources/db/changelog/`):
  columns `next_retry_at TIMESTAMP NULL`, `first_error_at TIMESTAMP NULL`,
  `failure_kind VARCHAR(20) NULL` on table `notification`
- [x] same changelog: partial index `idx_notification_next_retry_at` on `(next_retry_at)`
  `WHERE state = 'ERROR'`
- [x] same changelog: data migration for existing rows —
  `UPDATE notification SET next_retry_at = now(), first_error_at = COALESCE(last_trying_date, created_date) WHERE state = 'ERROR'`
- [x] add `FAILED` to `NotificationState` enum
- [x] create `FailureKind` enum (`TRANSIENT`, `PERMANENT`) in
  `domain/notification/` package
- [x] add `nextRetryAt`, `firstErrorAt`, `failureKind` to `NotificationEntity`,
  `NotificationDto`, and map them in `NotificationConverter`
- [x] expose new fields as attributes in `NotificationRecords` (read-only)
- [x] write tests: converter round-trip for new fields (entity → dto → entity)
- [x] run `./mvnw test -Dtest=HandleErrorNotificationTest` (existing behavior must still pass —
  new columns are nullable and unused yet); must pass before task 2

➕ note: Liquibase changelogs actually live in `src/main/resources/config/liquibase/changelog/`
(included from `config/liquibase/master.xml`), not `src/main/resources/db/changelog/` as stated
in Context — new changelog `20260730000000_add_notification_retry_columns.xml` follows the real
convention. New DTO fields added at the end of `NotificationDto` constructor (with defaults) to
keep existing positional constructor calls compiling.

### Task 2: New retry configuration with deprecation of old properties
- [x] add `Retry` nested class to `ApplicationProperties`: `enabled: boolean`,
  `pollInterval: Duration`, `batchSize: int`, `maxAttempts: int`, `initialInterval: Duration`,
  `multiplier: double`, `maxInterval: Duration`, `retryWindow: Duration`, `leaseTime: Duration`
- [x] add defaults to `NotificationsDefault`: enabled=true, pollInterval=30s, batchSize=50,
  maxAttempts=20, initialInterval=1m, multiplier=3.0, maxInterval=2h, retryWindow=24h, leaseTime=15m
- [x] keep `ErrorNotification` class (`ttl`/`delay`/`minTryCount`) as `@Deprecated`; fields are
  accepted but ignored (must stay because `ignoreUnknownFields = false` would crash stands
  that still set them)
- [x] add startup check (e.g. `@PostConstruct` in a config bean or `ApplicationRunner`):
  if any deprecated `error-notification` value differs from its old default, log
  `WARN: 'ecos-notifications.error-notification.*' is deprecated and ignored, use 'ecos-notifications.retry.*'`
- [x] add `ecos-notifications.retry` block to `src/main/resources/config/application.yml`
  (replace `error-notification` block) and to `src/test/resources/config/application-test.yml`
  (fast values for tests, e.g. initialInterval=1ms so existing sync-style tests keep working)
- [x] set SMTP timeouts in `application.yml`:
  `spring.mail.properties.mail.smtp.connectiontimeout/timeout/writetimeout: 10000`
  (currently infinite; also required for the lease invariant `leaseTime > batchSize × send timeout`)
- [x] write test: properties binding test — yaml with `retry` block binds to `Duration` fields correctly
- [x] write test: deprecated properties present in yaml do not fail startup
- [x] run binding tests + app context test; must pass before task 3

➕ note: startup check implemented as `config/RetryPropertiesDeprecationWarner.kt` (`@PostConstruct`);
detection logic exposed as `findDeprecatedOverrides()` for unit testing. Tests:
`RetryPropertiesBindingTest` (ApplicationContextRunner, also asserts `ignoreUnknownFields = false`
still rejects truly unknown properties) + `RetryPropertiesDeprecationWarnerTest`.
➕ note: `error-notification` block KEPT in `application-test.yml` (old repeater still reads
`ttl`/`minTryCount` and its `@Scheduled` delay until Task 8 rewrite; remove the block in Task 8).
The old `@Scheduled` placeholder got a default (`:600000`) so main `application.yml` could drop
the `error-notification` block without breaking startup — Task 8 replaces the annotation anyway.
➕ note: SMTP timeouts were already finite (5000) in `application.yml`, raised to 10000 per plan.

### Task 3: Failure classification framework
- [x] create `NotificationPermanentException : NotificationException` in
  `domain/notification/service/`
- [x] create `SendFailureClassifier` interface: `fun classify(e: Throwable): FailureKind?`
  (null = unknown; caller defaults to TRANSIENT — "unsure → TRANSIENT" is the safety rule)
- [x] create `EmailSendFailureClassifier` (Spring `@Component`), walking the JavaMail exception
  chain (`e.cause` / `MessagingException.nextException`):
  - `SendFailedException` with non-empty `invalidAddresses` and empty `validUnsentAddresses` → PERMANENT
  - `SMTPAddressFailedException` / return code 5xx (except 552 and quota-related) → PERMANENT
  - 4xx codes, connect exceptions, timeouts → TRANSIENT
  - anything unrecognized → null
- [x] create composite `NotificationFailureClassifier` service: checks
  `NotificationPermanentException` in the exception chain first, then delegates to
  per-type `SendFailureClassifier` beans, defaults to TRANSIENT
- [x] write unit tests for `EmailSendFailureClassifier`: invalid-address case → PERMANENT,
  550 → PERMANENT, 421/451 → TRANSIENT, connect timeout → TRANSIENT, unknown → null
- [x] write unit tests for composite classifier: permanent exception in chain wins;
  null from delegate → TRANSIENT
- [x] run classifier tests; must pass before task 4

➕ note: `NotificationException` made `open` with optional `cause` so
`NotificationPermanentException` can subclass it (existing single-arg callers unaffected).
SMTP exception types come from `org.eclipse.angus.mail.smtp.*` (angus jakarta.mail 2.0.5,
compile scope). 552 and 5xx quota-related replies map to TRANSIENT (full mailbox may clear),
per the "except 552 and quota-related" rule. Shared chain walker `sendFailureExceptionChain()`
(cause + `MessagingException.nextException`, cycle-safe) lives in `SendFailureClassifier.kt`
and is reused by the composite for permanent-exception detection.

### Task 4: Mark permanent failures at their origin
- [x] `UnsafeSendNotificationCommandExecutor.getTemplateMetaById`: template not found →
  `NotificationPermanentException`
- [x] template rendering (`NotificationSenderServiceImpl.prepareBody`/`prepareTitle` or
  `FreemarkerTemplateEngineService`): wrap FreeMarker `TemplateException` into
  `NotificationPermanentException` (IO/loading errors stay transient)
- [x] sender config parse failure in `NotificationSenderServiceImpl`
  (`sender.senderConfig.getAs(configClass)` error) → `NotificationPermanentException`
- [x] deliberately keep TRANSIENT: "no sender found for type" (sender artifact may be
  deployed later) — add code comment explaining this decision
- [x] write tests: send command with nonexistent template → exception chain contains
  `NotificationPermanentException`; broken FreeMarker template → same
- [x] run tests; must pass before task 5

➕ note: render wrapping done in `NotificationSenderServiceImpl.wrapTemplateDefects()`
(walks cause chain) — treats `freemarker.core.ParseException` as permanent too: it extends
`IOException` (so it surfaces via the "loading" catch in `FreemarkerTemplateEngineService`)
but broken markup is a template defect, not an IO failure. True IO/loading errors stay
transient. Tests in `PermanentFailureOriginTest` (missing-template, missing-model-attribute
render error, syntax error, plus negative test: SMTP outage is NOT permanent).

### Task 5: NotificationRetryPolicy — the single decision point
- [x] create `NotificationRetryPolicy` (Spring `@Component`, pure logic, no persistence):
  input = (current dto, failure kind, now); output = decision:
  - PERMANENT → state `FAILED`, no `nextRetryAt`
  - retries disabled (`retry.enabled=false`) → TRANSIENT goes to `EXPIRED` immediately
  - `tryingCount + 1 >= maxAttempts` OR `now - firstErrorAt > retryWindow` → `EXPIRED`
  - otherwise → state `ERROR`,
    `nextRetryAt = now + min(initialInterval * multiplier^(attempt-1), maxInterval) ± 20% jitter`
- [x] policy also fills `firstErrorAt` (only if null) and `failureKind`
- [x] terminal decision is made at computation time (no extra tick needed to expire a row,
  no tryingCount increment on the expiring transition)
- [x] write unit tests: backoff sequence grows 1m/3m/9m/27m/…/capped at 2h (jitter bounds ±20%);
  window exceeded → EXPIRED; attempts exhausted → EXPIRED; permanent → FAILED;
  disabled → EXPIRED (transient) / FAILED (permanent); firstErrorAt set once
- [x] run policy tests; must pass before task 6

➕ note: policy contract — `applyFailure` is only ever invoked after a REAL failed attempt, so
`tryingCount + 1` always counts that attempt (including on the EXPIRED transition); no phantom
increment can happen because expiring never runs as a separate tick. Decision table documented
in KDoc on the class. Tests: `NotificationRetryPolicyTest` (plain JUnit, 10 cases, jitter
bounds asserted over 200 samples).

### Task 6: Integrate policy into the synchronous error path
- [x] `NotificationCommandResultHolder.holdError`: classify the throwable via
  `NotificationFailureClassifier`, apply `NotificationRetryPolicy` — resulting dto gets
  proper `state` (ERROR/FAILED/EXPIRED), `nextRetryAt`, `firstErrorAt`, `failureKind`
- [x] `holdSuccess`: clear `nextRetryAt`/`failureKind` on success (row leaves the retry pipeline)
- [x] stack trace persistence: write `errorStackTrace` only when `errorMessage` changed
  (avoid rewriting identical multi-KB traces every attempt)
- [x] update integration test: failed send → row has state ERROR, `nextRetryAt` in the future,
  `firstErrorAt` set, `failureKind = TRANSIENT`
- [x] add integration test: permanent failure (bad template) → row is FAILED immediately,
  no `nextRetryAt`
- [x] run tests; must pass before task 7

➕ note: `holdError` builds the dto WITHOUT incrementing `tryingCount` — the policy's
`applyFailure` owns the increment (its contract counts the just-failed attempt), so the holder
must not pre-count it. New integration tests in `NotificationCommandResultHolderTest`
(transient → ERROR + schedule, permanent → FAILED, success clears retry fields, stack trace
rewritten only when `errorMessage` changes); `HandleErrorNotificationTest` extended with
retry-field assertions. Validation: `NotificationCommandResultHolderTest` +
`HandleErrorNotificationTest` + `PermanentFailureOriginTest` — 15 tests, all green.

### Task 7: Claim query — FOR UPDATE SKIP LOCKED with lease
- [x] add native modifying query to `NotificationRepository`:
  ```sql
  UPDATE notification SET next_retry_at = :leaseUntil
  WHERE id IN (
    SELECT id FROM notification
    WHERE state = 'ERROR' AND next_retry_at <= :now
    ORDER BY next_retry_at
    FOR UPDATE SKIP LOCKED
    LIMIT :batch
  )
  RETURNING id
  ```
  (claim = push `next_retry_at` forward by lease; crashed instance's rows become visible
  again when the lease expires; no SENDING state needed)
- [x] add `NotificationDao.claimErrorsForRetry(batch, lease): List<NotificationDto>` —
  runs the claim in its own short transaction (`REQUIRES_NEW`), then loads claimed rows;
  sending must happen OUTSIDE this transaction
- [x] add `NotificationDao.saveIfStateStillError(dto)`: conditional update that refuses to
  overwrite terminal states (`WHERE state = 'ERROR'`) — protects against cancel/re-drive races
- [x] write integration test: two sequential claims don't return the same rows (lease pushes
  them out of the window)
- [x] write integration test: `saveIfStateStillError` does not overwrite CANCELLED
- [x] run tests; must pass before task 8

➕ note: claim query is NOT `@Modifying` — `UPDATE ... RETURNING id` returns a result set, so
it runs as a select-style native query (`List<Long>`); `@Modifying`'s `executeUpdate()` would
fail on PG when rows come back. Returned claim order is unspecified (RETURNING/`findAllById`
don't preserve schedule order) — irrelevant since every claimed row is processed within the
tick. `saveIfStateStillError` returns Boolean and updates only retry-related fields
(state, trying_count, last_trying_date, next_retry_at, first_error_at, failure_kind,
error_message, error_stack_trace) via one atomic conditional UPDATE. Both DAO methods are
`@Secured(ADMIN, SYSTEM)` like `save()`. Tests: `NotificationRetryClaimTest` (5 cases, incl.
lease push-forward and future/non-ERROR rows not claimed); related suites
(`HandleErrorNotificationTest`, `NotificationCommandResultHolderTest`,
`PermanentFailureOriginTest`) still green.

### Task 8: Rewrite ErrorNotificationRepeater
- [x] rewrite `handleErrors()`: remove `while(true)`, `synchronized`, `PageRequest`;
  one tick = one `claimErrorsForRetry(retry.batchSize, retry.leaseTime)` = bounded work;
  leftover backlog waits for the next tick (recovery throttling)
- [x] schedule from new property: `fixedDelayString = "\${ecos-notifications.retry.poll-interval}"`
  (Spring parses Duration); with `retry.enabled=false` nothing is sent — see the scope change below
- [x] per row: re-execute command via `UnsafeSendNotificationCommandExecutor`;
  on success → SENT (clear retry fields); on failure → classify + policy (same code path
  as `holdError` — no duplicated rules); persist via `saveIfStateStillError`
- [x] remove the old minTryCount/ttl expiration logic entirely
- [x] rewrite `HandleErrorNotificationTest` for new semantics: transient error retried then SENT;
  permanent → FAILED on first retry; attempts budget exhausted → EXPIRED;
  row with `nextRetryAt` in the future is NOT picked up
- [x] add test: tick processes at most `batchSize` rows
- [x] run `./mvnw test -Dtest=HandleErrorNotificationTest`; must pass before task 9

➕ note (scope change vs the checkbox above): the tick is NOT skipped when `retry.enabled=false` —
that would also kill the manual re-drive of task 11, which is the only way forward on a stand with
retries off. Instead `retry.enabled` is enforced per row by the policy: `shouldAttempt()` refuses
any row that already spent an attempt (`tryingCount > 0`) while retries are off, and the repeater
expires it without sending, so the switch still stops a backlog scheduled before the flip. Re-driven
rows (`tryingCount = 0`) get exactly one attempt. `scheduledRetryTick()` is split from the work
method only so that tests and re-drive can call `handleErrors()` directly. `initialDelayString` also uses `poll-interval` (old fixed 10s initial delay dropped).
➕ note: `error-notification` block removed from `application-test.yml` (per Task 2 note) and
test `poll-interval` set to 30m — the background scheduled tick would race deterministic
direct `handleErrors()` calls across the shared Spring test contexts; tests drive ticks
themselves. `HandleFailureMinTryCountNotificationTest` deleted — it verified the removed
minTryCount/ttl semantics and relied on the background job. Also added tests:
retry window exceeded → EXPIRED, `retry.enabled=false` skips the scheduled tick.
Validation: HandleErrorNotificationTest (8) + RecipientsSendStrategyCommandTest,
BlockedNotificationsSendersTest, NotificationRetryClaimTest, NotificationCommandResultHolderTest,
HoldFailureNotificationTest, ResendNotificationTest, PermanentFailureOriginTest,
RetryProperties* (36 total) — all green.

### Task 9: Bulk mail — cancellation kills retries, synchronizer understands new states
- [x] `BulkMailDao.cancelDeferredNotifications`: replace read-copy-save with atomic
  `UPDATE ... SET state='CANCELLED' WHERE bulk_mail_ref=:ref AND state IN ('WAIT_FOR_DISPATCH','ERROR')`
  (repository modifying query); rows claimed mid-flight are covered by
  `saveIfStateStillError` from task 7
- [x] rewrite `BulkMailStatusSynchronizer.sync` when-chain into explicit priority:
  1. any ERROR → TRYING_TO_DISPATCH (retries in progress)
  2. any WAIT_FOR_DISPATCH → WAIT_FOR_DISPATCH
  3. no ERROR/WAIT, any EXPIRED or FAILED → BulkMailStatus.ERROR + INFO log with summary
     `sent/failed/expired/cancelled counts` (partial-success visibility)
  4. otherwise (SENT/RECIPIENTS_NOT_FOUND/BLOCKED/CANCELLED only) → SENT
- [x] key fix: single EXPIRED must NOT flip bulk mail to ERROR while other rows are still
  being sent (old chain did this)
- [x] document in `BulkMailBatchConfigDto.size` KDoc: batch size is also the duplicate
  blast-radius on retry; `personalizedMails=true` recommended for important mailings
- [x] write integration test: bulk mail removal cancels ERROR rows (no further retries pick them up)
- [x] write test for synchronizer: mix ERROR+SENT → TRYING_TO_DISPATCH; mix FAILED+SENT →
  ERROR status; only SENT+CANCELLED → SENT
- [x] run bulk mail tests (`./mvnw test -Dtest=BulkMail*`); must pass before task 10

➕ note: cancel UPDATE lives in `NotificationRepository.cancelDeferredForBulkMail` (also clears
`next_retry_at`), exposed via `NotificationDao.cancelDeferredForBulkMail` (`@Secured` like save).
Synchronizer keeps old "empty summary → don't touch status" behavior (`resolveStatus` returns
null); CANCELLED-only bulk mails now resolve to SENT (was: status stuck). New
`BulkMailStatusSynchronizerTest` (6 cases, incl. EXPIRED+ERROR and EXPIRED+WAIT priority);
cancellation test in `BulkMailStateTest` uses `personalizedMails=true` (default config creates
ONE notification row for all recipients — needed 2 rows to prove multi-row cancel).
`BulkMailStateTest` expired/recovery tests now drive `errorNotificationRepeater.handleErrors()`
inside their Awaitility loops (Task 8 removed the fast background repeater they relied on).
Validation: BulkMail* (37) + HandleErrorNotificationTest, NotificationRetryClaimTest,
NotificationCommandResultHolderTest (17) — all green.

### Task 10: Partial SMTP acceptance handling
- [x] `EmailNotificationSender` (or classifier integration point): on `SendFailedException`
  with non-empty `validSentAddresses` → treat as SENT, record partial-delivery note into
  `errorMessage` (e.g. `"Partially delivered, rejected: <addresses>"`); do NOT retry —
  re-sending to everyone is worse than missing part (documented trade-off)
- [x] write test with GreenMail/mocked transport: partial failure → state SENT + partial note,
  no retry scheduled
- [x] run tests; must pass before task 11

➕ note: detection lives in `PartialDeliveryDetector` (`domain/notification/service/`, pure logic,
documents the no-retry trade-off); `EmailNotificationSender` catches the send failure, returns
`SENT` with the note in `NotificationSenderResult.meta[PARTIAL_DELIVERY_NOTE]` and rethrows
anything that is not a partial acceptance — so the success event is still emitted normally.
Note plumbing to the row: `NotificationSenderService.sendNotification` now returns
`NotificationSenderResult` (was bare status) and `UnsafeSendNotificationCommandExecutor.execute`
returns the new `NotificationExecutionResult` (command result + `partialDeliveryNote`), because
the lib's `SendNotificationResult(status, result)` cannot carry extra data.
`holdSuccess(command, result, partialDeliveryNote = null)` and the repeater's success branch
persist the note as `errorMessage` while still clearing `nextRetryAt`/`failureKind`.
➕ note: `sendFailureExceptionChain()` extended to walk `MailSendException.messageExceptions` —
Spring collects per-message failures there and leaves the cause empty, so without it the SMTP
reply codes behind Spring's wrapper were invisible to `EmailSendFailureClassifier` (permanent
5xx failures were silently defaulting to TRANSIENT). Covered by a new classifier test case.
⚠️ pre-existing (NOT caused by this task, verified on stashed working tree):
`CommandNotificationSenderTest` fails when it runs after `EmailNotificationTest` in the same JVM
(the conditional command sender is not selected, the default email sender handles the
notification). Alone it passes. To be investigated in Task 13 (full-suite run).
✅ resolved in Task 13 — leftover sender in `EmailNotificationTest`; see the Task 13 notes.

### Task 11: Manual re-drive mutation
- [x] add mutation to `NotificationRecords` (admin/system only, follow `@Secured` pattern from
  `NotificationDao`): action `resend` on FAILED/EXPIRED/ERROR row → state ERROR,
  `tryingCount=0`, `firstErrorAt=null`, `nextRetryAt=now`
- [x] reject re-drive for states where it makes no sense (SENT, CANCELLED, WAIT_FOR_DISPATCH)
- [x] write test: FAILED row re-driven → picked up by repeater tick → attempted again
- [x] write test: re-drive of SENT row is rejected
- [x] run tests; must pass before task 12

➕ note: the action is named `RETRY`, not `resend` — `NotificationRecords.mutate` already has a
`RESEND` action with different semantics (executes a brand new `SendNotificationCommand`, leaving
the old row untouched; covered by `ResendNotificationTest` and used by the UI journal action).
Re-driving under the same name would silently change that behavior, so `RETRY` was added
alongside it. Re-drive is one atomic conditional UPDATE
(`NotificationRepository.redriveForRetry`, `where id = :id and state in ('ERROR','FAILED','EXPIRED')`)
exposed via `NotificationDao.redriveForRetry` (`@Secured(ADMIN, SYSTEM)`); the allowed-state set
lives in `NotificationDao.RETRYABLE_STATES` and is validated in the records DAO for a clear error
message. `failureKind` is cleared too (the verdict is reset), `errorMessage`/`errorStackTrace` are
kept as history until the next attempt overwrites them. Tests: `NotificationRedriveTest` (5 cases —
FAILED → redrive → repeater tick → SENT, EXPIRED redriven, future-scheduled ERROR pulled to
immediate attempt, SENT rejected, CANCELLED/WAIT_FOR_DISPATCH/BLOCKED/RECIPIENTS_NOT_FOUND
rejected). Validation: `NotificationRedriveTest` (5) + `ResendNotificationTest`,
`HandleErrorNotificationTest`, `NotificationRetryClaimTest`, `NotificationStoreTest` (19) — all green.

### Task 12: Metrics
- [x] add Micrometer metrics: counter `notifications.retry.attempts` (tag `outcome`: sent/error),
  counter `notifications.retry.terminal` (tag `state`: expired/failed),
  gauge `notifications.retry.backlog` (count of ERROR rows; cheap indexed count on the
  partial index, cached/registered as gauge function)
- [x] write test: attempt increments counter with correct tag (use `SimpleMeterRegistry`)
- [x] run tests; must pass before task 13

➕ note: metrics live in `NotificationRetryMetrics` (`domain/notification/service/`). Counters are
registered eagerly in the constructor so they export as zero before the first failure. Attempt
outcome is derived from the resulting state (`ERROR`/`FAILED`/`EXPIRED` → `error`, everything else
incl. `RECIPIENTS_NOT_FOUND`/`BLOCKED` → `sent`); terminal transitions are recorded both in
`ErrorNotificationRepeater` (after a successful `saveIfStateStillError`, so dropped results are not
counted) and in `NotificationCommandResultHolder.holdError` — a permanent first failure never
reaches the repeater, and each transition is still counted exactly once. Backlog gauge reads
`NotificationDao.getErrorBacklogCount()` → `NotificationRepository.countErrorBacklog()` (native
`count(*) where state = 'ERROR'`, answerable from the Task 1 partial index), cached for 15s because
gauges are polled per scrape; a failing count keeps the previous value instead of reporting 0.
Tests: `NotificationRetryMetricsTest` (10 cases, `SimpleMeterRegistry` + mocked dao) plus a wiring
delta-assertion test in `HandleErrorNotificationTest`. Validation: those two suites (19) +
NotificationCommandResultHolderTest, NotificationRetryClaimTest, NotificationRedriveTest,
PartialDeliveryNotificationTest, HoldFailureNotificationTest (17) — all green; `./mvnw validate`
(ktlint) clean.

### Task 13: Verify acceptance criteria
- [x] verify: permanent failure (invalid address / broken template) → exactly 1 attempt,
  state FAILED — the original spam scenario is dead
- [x] verify: transient failure schedule ≈ +1m/+4m/+13m/+40m then every 2h, EXPIRED at ~24h
- [x] verify: old config `min-try-count`/`ttl`/`delay` in yml → startup OK + WARN logged
- [x] verify: bulk mail delete stops all its retries
- [x] verify: `retry.enabled=false` → nothing is re-sent, first failure goes terminal
- [x] run full suite `./mvnw clean test` — all green
- [x] run linter/ktlint if configured in the build (`./mvnw validate` includes it via parent pom)

➕ note: every criterion is now pinned by an automated test rather than eyeballed —
- broken template → 1 attempt: `HandleErrorNotificationTest.permanentFailureIsAttemptedExactlyOnce`
  (three further ticks leave the FAILED row at `tryingCount = 1`);
  invalid address → 1 attempt: `PartialDeliveryNotificationTest.fully rejected notification fails
  permanently after one attempt` (mocked transport replies 550 for the only recipient, asserts
  `mailSender.send` called exactly once).
- schedule: `NotificationRetryPolicyTest.production defaults produce the documented retry schedule
  and expire near the window` walks the whole lifecycle with production defaults, asserting
  cumulative offsets +1m/+4m/+13m/+40m (±20% jitter), a 2h steady-state step, terminal EXPIRED
  strictly between 24h and 27h, and `tryingCount < max-attempts` (window is the limit that fires,
  not the attempts ceiling).
- deprecated config: `RetryPropertiesBindingTest` (startup OK) + two new logback `ListAppender`
  tests in `RetryPropertiesDeprecationWarnerTest` asserting the WARN text is actually emitted, and
  that nothing is logged when the deprecated block is untouched.
- bulk mail delete: `BulkMailStateTest.bulk mail removal should cancel error notifications and
  stop retries` (already added in Task 9).
- `retry.enabled=false`: `HandleErrorNotificationTest.retryDisabledSendsFirstTransientFailureStraight
  ToTerminalState` (first transient failure → EXPIRED, no `nextRetryAt`, tick leaves it alone),
  `rowScheduledBeforeTheSwitchIsExpiredWithoutSendingWhenRetryDisabled` (backlog scheduled before the
  flip is expired without a send) and `scheduledTickStillDrainsRedrivenRowsWhenRetryDisabled`
  (a re-driven row still gets its single attempt).
- full suite: 270 tests, 0 failures; `./mvnw validate` (ktlint) clean.

➕ note: resolved the ⚠️ recorded in Task 10 — `CommandNotificationSenderTest` failed when it ran
after `EmailNotificationTest` because `EmailNotificationTest` saves `default-email-sender-with-condition`
(senderType `default`, `order = 1.0`, same condition as the conditional command sender) and never
removed it; senders are context-wide, so with equal order the leftover default sender could win the
routing. `EmailNotificationTest` now has an `@AfterEach` that deletes the senders it creates and
re-saves `default-email-sender` if a test deleted it. `EmailNotificationTest` +
`CommandNotificationSenderTest` in one JVM are green.

⚠️ observed once, not reproducible: during one full-suite run `NotificationTemplateConverterTest`
failed with "Failed to load ApplicationContext" while an extra Spring context existed (the
invalid-address test had briefly been a separate `@MockitoBean` test class). It passed alone and in
three later full runs. The invalid-address test was folded into `PartialDeliveryNotificationTest`
(same mocked-`JavaMailSender` context, mock re-stubbed per test), so the suite adds no new Spring
context; the flake did not recur. Worth watching in CI if context count grows again.

### Task 14: [Final] Update documentation
- [x] update `CLAUDE.md`: notification states list (add FAILED), retry mechanism description,
  new config block reference
- [x] add KDoc on `NotificationRetryPolicy` documenting the full decision table
- [x] draft release-notes entry (see Post-Completion) into the PR/commit description:
  config migration table old → new, new defaults, FAILED state, behavior changes

➕ note: `CLAUDE.md` got a new "Retry Mechanism" section under Architecture Overview (states,
classification chain, the policy as single decision point, claim/lease job model, manual `RETRY`
re-drive vs the older `RESEND`, the `ecos-notifications.retry` block with the lease invariant,
deprecated `error-notification` handling, metrics). Also updated there: `FAILED` in the states
list, an "Important Notes" entry forbidding retry logic outside `NotificationRetryPolicy`, the
bulk-mail note (synchronizer priority chain + cancellation stopping retries), and the Liquibase
path — it pointed at the nonexistent `src/main/resources/db/changelog/` (real location is
`src/main/resources/config/liquibase/changelog/`, the same discrepancy recorded in Task 1).
`NotificationRetryPolicy` KDoc: decision table extended with a "meaning" column plus notes on
PERMANENT beating `retry.enabled`, attempts/window independence, fields always filled,
`nextRetryAt` cleared on terminal decisions, re-drive as the only way back, and the concrete
default schedule. Release notes drafted in the commit body. Validation: `./mvnw validate`
(ktlint) clean; no functional code touched (comments/markdown only).

## Technical Details

### New config (application.yml)

```yaml
ecos-notifications:
  retry:
    enabled: true
    poll-interval: 30s      # job frequency; cheap indexed query
    batch-size: 50          # rows claimed per tick (recovery-storm throttle)
    max-attempts: 20        # safety ceiling; retry-window is the primary limit
    initial-interval: 1m
    multiplier: 3.0
    max-interval: 2h
    retry-window: 24h       # from first_error_at; whichever of attempts/window hits first → EXPIRED
    lease-time: 15m         # claim lease; MUST exceed batch-size × worst-case send timeout
```

Old → new mapping (for release notes):

| Old (`error-notification`) | New (`retry`) | Note |
|---|---|---|
| `delay` (job AND retry interval) | `poll-interval` + per-row backoff | semantics split |
| `ttl` (from created_date, ms, -1=∞) | `retry-window` (from first_error_at, Duration) | infinite retries intentionally not supported |
| `min-try-count` (minimum overriding ttl) | `max-attempts` (hard maximum) | old semantics dropped |

### State machine

- `ERROR` — non-terminal: attempt failed, next attempt scheduled at `next_retry_at`
- `FAILED` — NEW, terminal: permanent verdict, retrying is pointless
- `EXPIRED` — terminal: transient failure, retry budget (attempts or window) exhausted
- terminal states are never overwritten by the repeater (`saveIfStateStillError`)

### Claim + lease invariant

Claim pushes `next_retry_at = now + lease` in a short standalone transaction; sending happens
outside any transaction. Crashed instance → lease expires → row visible again. Requirement:
`lease-time (15m) > batch-size (50) × SMTP timeout (10s ≈ 8.3m total)` — enforced by the
timeouts added in Task 2; document this invariant next to the properties.

### Classification default

Unknown error → TRANSIENT. False-permanent loses mail forever; false-transient costs a few
cheap attempts.

## Post-Completion

**Release notes** (user explicitly requested):
- `ecos-notifications.error-notification.*` deprecated and ignored (WARN at startup),
  replaced by `ecos-notifications.retry.*` — include the mapping table above
- new notification state `FAILED`; `EXPIRED` narrowed to "retry budget exhausted"
- infinite retries (`ttl: -1`) no longer supported
- new admin action: manual re-send of FAILED/EXPIRED notifications

**Stand configuration migration**:
- stands overriding `error-notification` values (e.g. `min-try-count: 1`, `ttl: 600000`)
  should move to `retry` block; defaults are now safe so most stands can just drop the override

**External consumers**:
- any dashboards/journals filtering notifications by state should add FAILED
- monitoring: new Micrometer metrics available (`notifications.retry.*`)

**Manual verification on a stand**:
- multi-replica deployment: verify no duplicate emails on retry (SKIP LOCKED + lease)
- SMTP outage drill: stop/start SMTP, verify bounded recovery drain (batch per tick) and backoff
