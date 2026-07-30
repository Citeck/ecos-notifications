# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ecos-notifications is a Spring Boot microservice for the Citeck ECOS platform that handles multi-channel notification delivery (email, Firebase push notifications, commands to other services). It's part of a larger microservices ecosystem with RabbitMQ-based event integration and Records API for inter-service communication.

**Tech Stack**: Spring Boot, Kotlin/Java, JPA/Hibernate, PostgreSQL, FreeMarker, Firebase Admin SDK, Maven

## Build and Development Commands

### Running Locally

For development with local SMTP server (e.g., MailHog on port 1025):
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,dev_local
```

On macOS, add the `dev_local_macos` profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,dev_local,dev_local_macos
```

Alternatively, run `ru.citeck.ecos.notifications.NotificationsApp` main class directly from your IDE.

### Testing

Run all tests:
```bash
./mvnw clean test
```

Run a single test class:
```bash
./mvnw test -Dtest=EmailNotificationTest
```

Run a specific test method:
```bash
./mvnw test -Dtest=EmailNotificationTest#shouldSendEmailNotification
```

**Test Infrastructure**: Tests extending `BaseMailTest` use GreenMail (in-memory SMTP server) and automatically deploy local artifacts before each test.

Two conventions worth knowing before writing tests:

- The scheduled retry tick is effectively disabled in tests (`retry.poll-interval: 30m` in
  `application-test.yml`); drive retries deterministically by calling
  `errorNotificationRepeater.handleErrors()` / `scheduledRetryTick()`. Test retry budgets are
  intentionally tiny (`max-attempts: 3`, `retry-window: 30s`, `initial-interval: 1ms`).
- Notification senders are context-wide. A test that saves a sender must delete it in `@AfterEach` —
  a leftover sender with `order = 1.0` hijacks routing in other test classes.

### Building for Production

Build Docker image with Jib:
```bash
./mvnw -Pprod clean package jib:dockerBuild -Djib.docker.image.tag=custom
```

### Code Quality

Start local Sonar server:
```bash
docker compose -f docker/sonar.yml up -d
```

Run Sonar analysis:
```bash
./mvnw -Pprod clean test sonar:sonar
```

## Architecture Overview

### Domain Structure

The codebase follows domain-driven design with these core domains (under `src/main/java/ru/citeck/ecos/notifications/domain/`):

- **notification**: Core notification lifecycle management
  - States: ERROR, SENT, EXPIRED, FAILED, WAIT_FOR_DISPATCH, CANCELLED, BLOCKED, RECIPIENTS_NOT_FOUND
  - `NotificationDao` - JPA persistence with workspace filtering
  - `AwaitingNotificationDispatcher` - Scheduled job that dispatches queued notifications
  - `ErrorNotificationRepeater` - Scheduled job that retries failed notifications (see [Retry Mechanism](#retry-mechanism))

- **template**: FreeMarker template management
  - Multi-locale support (separate templates per language)
  - Multi-template configuration (dynamic template selection based on record type/predicates)
  - `NotificationTemplateArtifactHandler` - Deploys templates from artifact zip archives
  - `EcosTemplateLoader` - Custom FreeMarker loader that reads from database

- **sender**: Pluggable notification sender configuration
  - Sender types: EMAIL, FIREBASE, COMMAND
  - Each sender has: enabled flag, order, notificationType, senderType, senderConfig JSON, condition predicate, templates filter
  - `NotificationSenderService` routes notifications to appropriate senders based on configuration

- **bulkmail**: Mass notification dispatch system
  - States: NEW, WAIT_FOR_DISPATCH, TRYING_TO_DISPATCH, SENT, ERROR
  - `BulkMailOperator` - Orchestrates bulk sending with batching
  - `RecipientsFinder` - Resolves recipients from queries
  - `BulkMailStatusSynchronizer` - Aggregates notification status

- **subscribe**: Legacy event-based subscription system for task events

- **file**: File attachment management

- **firebase**: Firebase Cloud Messaging integration

- **reminder**: Scheduled reminder functionality (artifact-configurable)

- **event**: Event emission system (emits notification success/failure/blocked events via RabbitMQ)

### Notification Flow

1. **Command Reception**: `SendNotificationCommand` received via Commands API
2. **Command Splitting**: `SendNotificationCommandExecutor` splits by user portal URLs for personalized web links
3. **Template Resolution**:
   - Resolves base template by ID/name
   - Applies multi-template logic (selects specific template based on record type/predicates)
   - Maps model attributes if needed
4. **Recipient Validation**: Checks for valid recipients
5. **Raw Notification**: Creates `RawNotification` (template + model data)
6. **Sender Routing**: `NotificationSenderService` finds matching `NotificationsSenderEntity` by type, condition predicate, and template filter
7. **Template Rendering**: `FreemarkerTemplateEngineService` renders title and body with injected beans
8. **Fit Notification**: Converts to `FitNotification` (rendered content + attachments)
9. **Sending**: Concrete sender implementation (`EmailNotificationSender`, `FirebaseNotificationSender`, `CommandNotificationSender`) delivers notification
10. **Event Emission**: Success/failure/blocked events emitted via `NotificationEventService`
11. **Persistence**: `NotificationDto` saved with final state

### Retry Mechanism

Failed notifications are retried per-row with exponential backoff (plus ±20% jitter, so observed
intervals are never exactly the configured ones), not on a flat global interval.

**States relevant to retry**:
- `ERROR` — non-terminal: the last attempt failed, the next one is scheduled at `next_retry_at`
- `FAILED` — terminal: permanent verdict (invalid recipient address, broken template, unparsable
  sender config) — retrying is pointless, so exactly one attempt is made
- `EXPIRED` — terminal: transient failure whose retry budget (`max-attempts` or `retry-window`) ran out

**Failure classification** (`domain/notification/service/`):
- `NotificationFailureClassifier` — composite entry point: `NotificationPermanentException` anywhere
  in the exception chain wins, otherwise per-type `SendFailureClassifier` beans are consulted
- `EmailSendFailureClassifier` — walks the JavaMail chain (cause / `MessagingException.nextException` /
  `MailSendException.messageExceptions`): invalid-address (both a rejected recipient list and an
  `AddressException` from parsing a malformed address before the SMTP dialogue) and 5xx replies →
  `PERMANENT`, except `552` and 5xx replies mentioning *quota* (a full mailbox may be cleaned up) →
  `TRANSIENT`; 4xx / connect / timeout → `TRANSIENT`. An SMTP reply code that could not be parsed
  decides nothing on its own — the invalid-address rule still applies to the same exception.
  A failure that still lists valid-unsent recipients is `TRANSIENT` regardless of its reply code —
  those addresses were not rejected outright and a retry can still reach them. For the same reason
  the whole chain is inspected and `TRANSIENT` beats `PERMANENT`: a message to a mix of permanently
  and temporarily rejected recipients chains one exception per address, and letting the 5xx one win
  would drop the temporarily rejected recipients' mail forever
- Unknown errors default to `TRANSIENT`. A false-permanent verdict loses mail forever, a
  false-transient one costs a few cheap attempts — always keep this asymmetry when adding rules.
  This is also why `NotificationSenderServiceImpl.wrapTemplateDefects` takes its verdict from the
  ROOT of the cause chain: FreeMarker wraps failures of the injected beans (config service, DB) into
  a `TemplateException` too, and those are outages, not broken templates. The permanent set also
  covers `TemplateNotFoundException`/`MalformedTemplateNameException` — despite being IOExceptions
  they mean a template name (typically a `<#include>`/`<#import>` target) resolves to nothing, which
  no retry fixes; a DB failure inside `EcosTemplateLoader` propagates as a SQL error instead and
  stays transient.
- `PartialDeliveryDetector` — an SMTP failure that still had accepted recipients counts as SENT with a
  partial-delivery note in `errorMessage`; it is deliberately not retried (re-sending to everyone is worse).
  Requires `spring.mail.properties.mail.smtp.sendpartial: true` — without it JavaMail aborts the whole
  message and never reports `validSentAddresses`, so the detector can never fire.
- Adding a channel: implement `SendFailureClassifier` as a `@Component` (return `null` for anything
  you do not recognize — the composite then falls back to `TRANSIENT`). Anything a custom
  `NotificationSender` throws is `TRANSIENT` unless it raises `NotificationPermanentException`
  (matched anywhere in the cause chain). A sender may report a partial delivery through
  `meta[NotificationSenderResult.PARTIAL_DELIVERY_NOTE]`.

**Decision point**: `NotificationRetryPolicy` is the only place retry rules live. Both the synchronous
path (`NotificationCommandResultHolder.holdError`) and the retry job go through it, so adding a rule
there covers both. Its KDoc holds the full decision table.

**Retry job**: `ErrorNotificationRepeater.scheduledRetryTick()` runs every `retry.poll-interval`.
One tick claims at most `retry.batch-size` due rows through `NotificationDao.claimErrorsForRetry`
(`FOR UPDATE SKIP LOCKED`, claim = push `next_retry_at` forward by `retry.lease-time`), so multiple
replicas never send the same row and a crashed instance's rows come back when its lease expires.
The tick also stops early once its own lease deadline passes, leaving the rest of the batch for a
later tick — a slow SMTP server can never stretch a batch beyond its claim. Sending happens outside
the transaction; results are written with `NotificationDao.saveIfStateStillError`, which applies them
only while the row still carries the claim token (`next_retry_at` = the lease deadline it was claimed
with) and is still in ERROR — so a cancellation, a manual re-drive or a re-claim by another replica is
never undone by an in-flight attempt. Backlog left over after a tick waits for the next one — that
bound is what throttles the drain after an SMTP outage.

An ERROR row whose `next_retry_at` is NULL counts as due and is claimed first. The policy always
fills the schedule, so such a row can only come from outside it — an old replica writing during a
rolling upgrade, a restore from a pre-migration backup. Since SQL NULL never satisfies
`next_retry_at <= now`, leaving it out of the claim predicate would strand the row forever: never
retried, never terminal, its bulk mail pinned in `TRYING_TO_DISPATCH` and the backlog gauge
permanently inflated. Any new query over the retry pipeline must keep the same NULL tolerance.

The tick runs regardless of `retry.enabled`: with retries off the policy never schedules a row, so the
only rows it still sends are manually re-driven ones, and each of them gets exactly one attempt before
going terminal. That is what keeps re-drive working on stands with retries disabled. Rows scheduled
before the switch was flipped are claimed too, but `NotificationRetryPolicy.shouldAttempt` (the
pre-send gate, `tryingCount > 0` while retries are off) expires them without sending anything — so
flipping the switch during a mail storm really stops the backlog instead of letting it out once more.

**Manual re-drive**: the `RETRY` mutation action on `NotificationRecords` (admin/system) resets an
ERROR/FAILED/EXPIRED row to ERROR with `tryingCount=0`, `firstErrorAt=null`, `failureKind=null`,
`nextRetryAt=now`. This is distinct from the older `RESEND` action, which executes a brand new command
and leaves the old row alone. UI entry point: `eapps/artifacts/ui/action/retry-notification-action.yml`
("Повторить отправку сейчас"); the two actions are mutually exclusive in the UI — `RESEND`'s evaluator
now hides it in ERROR/FAILED/EXPIRED, the exact states where `RETRY` shows up, so a failed row offers
re-drive only and the near-identical Russian names can no longer be confused.

`nextRetryAt`, `firstErrorAt` and `failureKind` are exposed as read-only attributes on
`NotificationRecords` (and `nextRetryAt` as a journal column).

**Configuration** (`ecos-notifications.retry` in `config/application.yml`, bound to
`ApplicationProperties.Retry`, defaults in `NotificationsDefault`):

```yaml
ecos-notifications:
  retry:
    enabled: true         # false => first failure goes straight to a terminal state; re-drive still works
    poll-interval: 30s    # job frequency
    batch-size: 50        # rows claimed per tick (recovery-storm throttle)
    max-attempts: 20      # safety ceiling; retry-window is the primary limit
    initial-interval: 1m
    multiplier: 3.0
    max-interval: 2h
    retry-window: 24h     # measured from first_error_at
    lease-time: 15m       # also the wall-clock bound of a single tick
```

`lease-time` should exceed `batch-size × worst-case send time` (defaults: 15m vs 50 × 10s ≈ 8.3m), so
a full batch normally fits into one tick; when it does not, the tick stops at the lease deadline and
the rest is picked up later. `spring.mail.properties.mail.smtp.*timeout` values must stay finite —
an untimed SMTP socket would park a claimed row for as long as the server keeps it.

`RetryPropertiesValidator` checks the block at startup: non-positive durations/counts or
`multiplier < 1` fail the context (`batch-size: 0` would silently stall every retry, a non-positive
`lease-time` would let another replica re-send rows still in flight), while a violated lease
invariant or missing SMTP timeouts are only WARNed about — the worst case rarely materializes and
refusing to start over it would be worse than a re-sent message. The test config intentionally
trips that WARN (1m lease vs 50 × 10s) because GreenMail answers instantly.

The old `ecos-notifications.error-notification.*` block (`ttl`/`delay`/`min-try-count`) is deprecated
and ignored; it is still bound so that `ignoreUnknownFields = false` does not crash existing stands,
and `RetryPropertiesDeprecationWarner` logs a WARN when a stand still overrides it.

**Metrics**: `NotificationRetryMetrics` exports `notifications.retry.attempts` (tag `outcome`),
`notifications.retry.terminal` (tag `state`) and the `notifications.retry.backlog` gauge.

### Key Architectural Patterns

**Strategy Pattern**: `NotificationSender<T>` interface with implementations auto-registered via Spring. Routing based on sender configuration with predicate matching.

**Template Method Pattern**: `AbstractRecordsDao` provides common query/mutation patterns for all Records DAOs.

**Command Pattern**: `SendNotificationCommand` + `CommandExecutor` with `CommandsService` as command bus.

**Artifact Deployment**: `EcosArtifactHandler<T>` enables hot deployment of templates, senders, and reminders from zip archives without service restart.

**Workspace Isolation**: Multi-tenancy with workspace-aware queries and ID prefixing (`workspace$id`).

### FreeMarker Template Processing

Templates are stored in database (`notification_template` and `template_data` tables) and loaded via `EcosTemplateLoader`.

**Injected Beans** (available in all templates):
- `ImageAccessor` - Image handling utilities
- `MetaAccessor` - Metadata access from records
- `LinkAccessor` - URL generation helpers
- `EcosConfigAccessor` - Configuration access

To add custom beans, implement `InjectedFreemarkerBean` interface and register as Spring `@Component`.

**Multi-Template Resolution**:
- Base template can define `multiTemplateConfig` with conditions
- System resolves to specific template based on `_type?id` or `_etype?id` in model
- Supports recursive resolution (templates referencing other templates)

### Integration Points

**RabbitMQ/Events**: Receives task events (legacy subscribe system) and emits notification status events.

**Records API**: All domain entities exposed via Records DAOs (e.g., `NotificationRecords`, `BulkMailRecords`). Supports queries with predicates, mutations, and attribute resolution.

**Commands API**: Receives `SendNotificationCommand` from external services.

**Workspace Service**: Multi-tenancy support with `WorkspaceService.buildAvailableWorkspacesPredicate()` for filtering entities by workspace.

**Spring Mail/SMTP**: Email sending via `spring-boot-starter-mail` with `org.apache.commons.email2-jakarta` wrapper for advanced features (signing, attachments).

**Firebase Admin SDK**: Push notifications with device type handling (iOS/Android).

## Permission Model

**System Artifacts**: `NotificationsSystemArtifactPerms` controls write access to templates and senders (system-level artifacts).

**Method Security**: `@Secured(AuthRole.ADMIN, AuthRole.SYSTEM)` on sensitive DAO operations.

**Records-Level**: `RecordPerms` calculated via `EcosPermissionsService` and exposed through Records API.

**Authentication Context**: Use `AuthContext.runAs()` or `AuthContext.runAsSystem()` for privileged operations.

## Common Extension Points

### Adding a Custom Notification Sender

1. Implement `NotificationSender<YourConfigClass>`:
```kotlin
@Component
class CustomSender : NotificationSender<CustomConfig> {
    override fun getSenderType(): String = "custom"
    override fun getNotificationType(): NotificationType = NotificationType.EMAIL_NOTIFICATION

    override fun sendNotification(
        notification: FitNotification,
        config: CustomConfig
    ): NotificationSenderResult {
        // Your sending logic
    }

    override fun getConfigClass(): Class<CustomConfig> = CustomConfig::class.java
}
```

2. Configuration is stored as JSON in `notifications_sender.sender_config` column
3. Auto-registered via Spring component scanning
4. Create sender configuration via Records API or UI

### Adding FreeMarker Template Utilities

Implement `InjectedFreemarkerBean`:
```kotlin
@Component
class CustomAccessor : InjectedFreemarkerBean, TemplateMethodModelEx {
    override fun getId(): String = "customUtil"

    override fun exec(arguments: List<*>): Any {
        // Your template utility logic
    }
}
```

Access in templates: `${customUtil('arg1', 'arg2')}`

### Creating Custom Artifact Handlers

Implement `EcosArtifactHandler<T>` for deploying custom artifact types.

## Key Files and Locations

- **Main Application**: `src/main/java/ru/citeck/ecos/notifications/NotificationsApp.java`
- **Domain Logic**: `src/main/java/ru/citeck/ecos/notifications/domain/*/service/`
- **Records DAOs**: `src/main/java/ru/citeck/ecos/notifications/domain/*/api/records/`
- **JPA Repositories**: `src/main/java/ru/citeck/ecos/notifications/domain/*/repo/`
- **Senders**: `src/main/java/ru/citeck/ecos/notifications/lib/*/sender/`
- **Configuration**: `src/main/java/ru/citeck/ecos/notifications/config/`
- **Liquibase Migrations**: `src/main/resources/config/liquibase/changelog/` (one file per change,
  named `<yyyyMMddHHmmss>_<description>.xml`, included from `config/liquibase/master.xml`)
- **Test Resources**: `src/test/resources/`

## Important Notes

- **Workspace Context**: Always consider workspace isolation when querying entities. Use `WorkspaceService` helpers.
- **Authentication**: Sensitive operations require proper `AuthContext` setup or `@Secured` annotations.
- **Delayed Notifications**: Notifications with `delayedSend` timestamp use `WAIT_FOR_DISPATCH` state and scheduled dispatcher.
- **Retry Rules**: Never add retry/expiration logic outside `NotificationRetryPolicy` — both the synchronous and the job path depend on it being the single source of truth.
- **Bulk Mail**: Uses two-phase approach (calculate recipients → dispatch). Status synchronized asynchronously. `BulkMailStatusSynchronizer` aggregates row states by an explicit priority chain (any ERROR → TRYING_TO_DISPATCH, any WAIT_FOR_DISPATCH → WAIT_FOR_DISPATCH, any EXPIRED/FAILED → ERROR, otherwise SENT) — it must be updated whenever a notification state is added. Deleting a bulk mail cancels its pending AND ERROR rows atomically, which stops their retries: an
in-flight attempt can no longer revive them either — the repeater's claim token drops its result, and
`NotificationCommandResultHolder.holdError` writes its outcome through
`NotificationDao.saveFailureIfNotCancelled` (a conditional `update ... where state <> 'CANCELLED'`),
so a row cancelled between reading it and writing the failure is never resurrected — the read-based
check in `holdError` is only a shortcut, the atomic one is the guard.
- **Template Caching**: Templates loaded via `EcosTemplateLoader` are cached by FreeMarker configuration.
- **Event Emission**: All notification state changes should emit events via `NotificationEventService`.

## Dependencies

**Required Services** (from Citeck deployment):
- Zookeeper - Service discovery
- RabbitMQ - Event messaging
- ecos-model - Record types and workspace service
- ecos-registry - Service registry

**Database**: PostgreSQL with Liquibase migrations

## References

- [Citeck Documentation](https://citeck-ecos.readthedocs.io/ru/latest/index.html)
- Parent POM: `ecos-webapp-spring-hibernate-parent:3.24.13`
- Key Libraries: ecos-events (1.2.0), FreeMarker (2.3.34), Firebase Admin SDK (9.7.0)
