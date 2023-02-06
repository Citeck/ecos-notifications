package ru.citeck.ecos.notifications.domain

import ru.citeck.ecos.records2.RecordConstants

interface SearchAtEntity {
    fun replaceAttributeNameByValid(attributeName: String): String
    fun isAttributeNameNotValid(attributeName: String): Boolean = RecordConstants.ATT_TYPE.equals(attributeName)
}
