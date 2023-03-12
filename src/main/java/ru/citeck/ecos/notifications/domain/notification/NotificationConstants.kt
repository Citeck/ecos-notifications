package ru.citeck.ecos.notifications.domain.notification

const val NOTIFICATION_DATA = "_data"
const val NOTIFICATION_ATTACHMENTS = "_attachments"
const val NOTIFICATION_MODEL = "model"

const val NOTIFICATION_ATTACHMENT_META = "meta"
const val NOTIFICATION_ATTACHMENTS_PREVIEW_INFO = "previewInfo"
const val NOTIFICATION_ATTACHMENT_BYTES = "bytes"

const val NOTIFICATION_ATTACHMENT_ORIGINAL_NAME = "originalName"
const val NOTIFICATION_ATTACHMENT_NAME = "name"

const val NOTIFICATION_ATTACHMENT_ORIGINAL_EXT = "originalExt"
const val NOTIFICATION_ATTACHMENT_EXT = "ext"

const val NOTIFICATION_ATTACHMENT_MIMETYPE_LOWER = "mimetype"
const val NOTIFICATION_ATTACHMENT_MIMETYPE = "mimeType"

val NOTIFICATION_ATTACHMENT_MIMETYPE_ATTS = listOf(
    NOTIFICATION_ATTACHMENT_MIMETYPE,
    NOTIFICATION_ATTACHMENT_MIMETYPE_LOWER
)

val NOTIFICATION_ATTACHMENT_NAME_ATTS = listOf(
    NOTIFICATION_ATTACHMENT_NAME,
    NOTIFICATION_ATTACHMENT_ORIGINAL_NAME
)

val NOTIFICATION_ATTACHMENT_EXT_ATTS = listOf(
    NOTIFICATION_ATTACHMENT_EXT,
    NOTIFICATION_ATTACHMENT_ORIGINAL_EXT
)

