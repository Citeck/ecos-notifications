<#setting time_zone="GMT+3">
<#setting locale="ru_RU"/>

<#if certValidityTo??>
    <#assign expDate = certValidityTo?datetime.iso>

    Приближается окончание срока действия сертификата <a href="${link.getRecordLink(doc_ref)}" target="_blank">${certName!"имя неизвестно"}</a>, действительного до ${expDate?string.short}.

<#else>

    Не удалось определить срок действия сертификата для настоящего напоминания.
    Сертификат <a href="${link.getRecordLink(doc_ref)}" target="_blank">${certName!"имя неизвестно"}</a> не содержит информации о сроке действия или был удален.

</#if>

