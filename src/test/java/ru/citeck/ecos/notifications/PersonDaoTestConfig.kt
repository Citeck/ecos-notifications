package ru.citeck.ecos.notifications

import com.github.javafaker.Faker
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.records2.source.dao.local.InMemRecordsDao
import ru.citeck.ecos.webapp.api.constants.AppName
import java.util.*

@Configuration
class PersonDaoTestConfig {

    companion object {
        private val faker = Faker.instance()
    }

    @Bean
    fun createPersonDao(): InMemRecordsDao<PersonDto> {
        return InMemRecordsDao("${AppName.EMODEL}/person")
    }

    fun addPerson(dto: PersonDto) {
        createPersonDao().setRecord(dto.id, dto)
    }

    class PersonDto(
        val id: String,
        val firstName: String = faker.name().firstName(),
        val lastName: String = faker.name().lastName(),
        val email: String = "${firstName.lowercase()}.${lastName.lowercase()}@gmail.com"
    )
}
