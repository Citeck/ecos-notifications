package ru.citeck.ecos.notifications.domain.reminder.dto

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.webapp.api.entity.EntityRef

@IncludeNonDefault
@JsonDeserialize(builder = Reminder.Builder::class)
data class Reminder(
    val id: String,
    val name: MLText,
    val enabled: Boolean,
    val reminderType: ReminderType,
    val certificates: List<EntityRef>,
    val notificationTemplate: EntityRef,
    val recipients: List<EntityRef>,
    val reminderThresholdDurations: List<String>
) {

    companion object {
        @JvmField
        val EMPTY = create {}

        fun create(): Builder {
            return Builder()
        }

        fun create(builder: Builder.() -> Unit): Reminder {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    @Suppress("DuplicatedCode")
    fun validate() {
        if (reminderType == ReminderType.CERTIFICATE_EXPIRATION) {
            require(certificates.isNotEmpty()) { "certificates must not be empty" }
            require(notificationTemplate.isNotEmpty()) { "notificationTemplate must not be empty" }
            require(recipients.isNotEmpty()) { "recipients must not be empty" }
            require(reminderThresholdDurations.isNotEmpty()) { "reminderThresholdDurations must not be empty" }
        }
    }

    class Builder() {

        var id: String = ""
        var name: MLText = MLText.EMPTY
        var enabled: Boolean = false
        var reminderType: ReminderType = ReminderType.UNKNOWN
        var certificates: List<EntityRef> = emptyList()
        var notificationTemplate: EntityRef = EntityRef.EMPTY
        var recipients: List<EntityRef> = emptyList()
        var reminderThresholdDurations: List<String> = emptyList()

        constructor(base: Reminder) : this() {
            this.id = base.id
            this.name = base.name
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withEnabled(enabled: Boolean?): Builder {
            this.enabled = enabled ?: false
            return this
        }

        fun withReminderType(reminderType: ReminderType?): Builder {
            this.reminderType = reminderType ?: ReminderType.UNKNOWN
            return this
        }

        fun withcertificates(certificates: List<EntityRef>?): Builder {
            this.certificates = certificates ?: emptyList()
            return this
        }

        fun withNotificationTemplate(notificationTemplate: EntityRef?): Builder {
            this.notificationTemplate = notificationTemplate ?: EntityRef.EMPTY
            return this
        }

        fun withRecipients(recipients: List<EntityRef>?): Builder {
            this.recipients = recipients ?: emptyList()
            return this
        }

        fun withReminderThresholdDurations(reminderThresholdDurations: List<String>?): Builder {
            this.reminderThresholdDurations = reminderThresholdDurations ?: emptyList()
            return this
        }

        @Suppress("DuplicatedCode")
        fun build(): Reminder {

            if (reminderType == ReminderType.CERTIFICATE_EXPIRATION) {
                require(certificates.isNotEmpty()) { "certificates must not be empty" }
                require(notificationTemplate.isNotEmpty()) { "notificationTemplate must not be empty" }
                require(recipients.isNotEmpty()) { "recipients must not be empty" }
                require(reminderThresholdDurations.isNotEmpty()) { "reminderThresholdDurations must not be empty" }
            }

            return Reminder(
                id,
                name,
                enabled,
                reminderType,
                certificates,
                notificationTemplate,
                recipients,
                reminderThresholdDurations
            )
        }
    }
}

enum class ReminderType {
    CERTIFICATE_EXPIRATION,
    CERTIFICATE_REVOCATION,

    @JsonEnumDefaultValue
    UNKNOWN
}
