package ru.citeck.ecos.notifications.domain.template

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.notifications.domain.sender.NotificationSenderService
import ru.citeck.ecos.notifications.domain.template.api.records.NotificationTemplateRecords
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateWithMeta
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateAttsCalculator
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService
import ru.citeck.ecos.notifications.lib.api.GetTemplateDataExactTemplate
import ru.citeck.ecos.notifications.lib.api.GetTemplateDataTemplateVariants
import ru.citeck.ecos.notifications.loadAllTemplates
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.test.commons.EcosWebAppApiMock
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant
import java.util.*

class NotificationTemplateAttsCalculatorTest {

    companion object {
        private const val SENDER_ATT = "sender-att"
    }

    private lateinit var calculator: NotificationTemplateAttsCalculator
    private lateinit var templateService: NotificationTemplateService
    private lateinit var recordsService: RecordsService

    @Test
    fun testAllRequiredAtts() {

        fun checkTemplate(template: String, expectedAtts: Set<String>) {

            val calcAtts = calculator.getAllRequiredAtts(templateService.findById(template).orElse(null))
            assertThat(calcAtts).containsExactlyInAnyOrderElementsOf(expectedAtts)

            val recAtts = recordsService.getAtt(template.asTemplateRef(), "multiModelAttributes[]").asStrList()
            assertThat(recAtts).containsExactlyInAnyOrderElementsOf(expectedAtts)
        }

        checkTemplate(
            "sub-template-1",
            setOf(
                "text|presuf('','-sub-template-1')",
                SENDER_ATT
            )
        )
        checkTemplate(
            "multi-template-0",
            setOf(
                "text", // from condition
                "text|presuf('','-multi-att')",
                "text|presuf('','-sub-template-0')",
                "text|presuf('','-sub-template-1')",
                "text|presuf('','-sub-cond-template-0')",
                SENDER_ATT
            )
        )
    }

    private fun String.asTemplateRef(): EntityRef {
        return EntityRef.create(AppName.NOTIFICATIONS, "template", this)
    }

    @Test
    fun testGetTemplateDataForSimpleTemplate() {

        val atts = ObjectData.create()
            .set("_type?id", "emodel/type@type-0")

        val simpleData = calculator.getTemplateData(
            "sub-template-1".asTemplateRef(),
            atts,
            DataValue.createObj()
        )
        assertThat(simpleData).isInstanceOf(GetTemplateDataExactTemplate::class.java)
        simpleData as GetTemplateDataExactTemplate

        assertThat(simpleData.templateRef).isEqualTo("sub-template-1".asTemplateRef())
        assertThat(simpleData.requiredAtts).containsExactlyInAnyOrder(
            "text|presuf('','-sub-template-1')",
            SENDER_ATT
        )
    }

    @Test
    fun testGetTemplateDataForMultiTemplateWithSimpleMatch() {
        val atts = ObjectData.create()
            .set("_type?id", "emodel/type@type-0")

        val multiData0 = calculator.getTemplateData(
            "multi-template-0".asTemplateRef(),
            atts,
            DataValue.createObj()
        )
        assertThat(multiData0).isInstanceOf(GetTemplateDataExactTemplate::class.java)
        multiData0 as GetTemplateDataExactTemplate

        assertThat(multiData0.templateRef).isEqualTo("sub-template-0".asTemplateRef())
        assertThat(multiData0.requiredAtts).containsExactlyInAnyOrder(
            "text|presuf('','-multi-att')",
            "text|presuf('','-sub-template-0')",
            SENDER_ATT
        )
    }

    @Test
    fun testGetTemplateDataForMultiTemplateWithComplexMatch() {

        val atts = ObjectData.create()
            .set("_type?id", "emodel/type@type-1")

        val multiData0 = calculator.getTemplateData(
            "multi-template-0".asTemplateRef(),
            atts,
            DataValue.createObj()
        )
        assertThat(multiData0).isInstanceOf(GetTemplateDataTemplateVariants::class.java)
        multiData0 as GetTemplateDataTemplateVariants

        assertThat(multiData0.requiredAtts).containsExactly(
            "text|presuf('','-multi-att')"
        )
        assertThat(multiData0.variants).containsExactly(
            GetTemplateDataTemplateVariants.Variant(
                "sub-template-conditional-0".asTemplateRef(),
                Predicates.eq("text", "conditional-0")
            ),
            GetTemplateDataTemplateVariants.Variant(
                "sub-template-1".asTemplateRef(),
                Predicates.alwaysTrue()
            ),
            GetTemplateDataTemplateVariants.Variant(
                "multi-template-0".asTemplateRef(),
                Predicates.alwaysTrue()
            )
        )

        val multiData1 = calculator.getTemplateData(
            "sub-template-conditional-0".asTemplateRef(),
            atts,
            DataValue.createObj()
        )
        assertThat(multiData1).isInstanceOf(GetTemplateDataExactTemplate::class.java)
        multiData1 as GetTemplateDataExactTemplate

        assertThat(multiData1.templateRef).isEqualTo("sub-template-conditional-0".asTemplateRef())
        assertThat(multiData1.requiredAtts).containsExactlyInAnyOrder(
            "text|presuf('','-sub-cond-template-0')",
            SENDER_ATT
        )
    }

    @BeforeEach
    fun beforeEach() {

        val templates = loadAllTemplates("template/attscalc").associateBy { it.id }

        templateService = Mockito.mock(NotificationTemplateService::class.java)
        Mockito.`when`(templateService.findById(Mockito.anyString())).then { invocation ->
            Optional.ofNullable(
                templates[invocation.getArgument(0)]?.let { dto ->
                    NotificationTemplateWithMeta(
                        dto.id,
                        dto.name,
                        dto.notificationTitle,
                        dto.tags,
                        dto.model,
                        dto.multiTemplateConfig,
                        emptyMap(),
                        "",
                        Instant.EPOCH,
                        "",
                        Instant.EPOCH
                    )
                }
            )
        }
        val notificationSendersService = Mockito.mock(NotificationSenderService::class.java)
        Mockito.`when`(notificationSendersService.getModel()).then {
            setOf(SENDER_ATT)
        }

        val webAppApi = EcosWebAppApiMock(AppName.NOTIFICATIONS)
        val recordsServices = object : RecordsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi {
                return webAppApi
            }
        }
        recordsService = recordsServices.recordsService

        calculator = NotificationTemplateAttsCalculator(
            templateService,
            recordsService,
            notificationSendersService
        )

        recordsServices.recordsService.register(NotificationTemplateRecords(templateService, calculator))
    }
}
