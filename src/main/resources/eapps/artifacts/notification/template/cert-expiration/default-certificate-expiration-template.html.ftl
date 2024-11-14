<#setting time_zone="GMT+3">
<#setting locale="ru_RU"/>

<#assign expDate = certValidityTo?datetime.iso>

Приближается окончание срока действия сертификата <a href="${link.getRecordLink(doc_ref)}" target="_blank">${certName}</a>, действительного до ${expDate?string.short}.

