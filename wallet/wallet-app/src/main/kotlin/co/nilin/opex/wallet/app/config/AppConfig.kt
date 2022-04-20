package co.nilin.opex.wallet.app.config

import co.nilin.opex.wallet.ports.kafka.listener.consumer.AdminEventKafkaListener
import co.nilin.opex.wallet.ports.kafka.listener.consumer.UserCreatedKafkaListener
import co.nilin.opex.wallet.ports.kafka.listener.spi.AdminEventListener
import co.nilin.opex.wallet.ports.kafka.listener.spi.UserCreatedEventListener
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class AppConfig {

    @Autowired
    fun configureEventListeners(
        useCreatedKafkaListener: UserCreatedKafkaListener,
        userCreatedEventListener: UserCreatedEventListener,
        adminKafkaEventListener: AdminEventKafkaListener,
        adminEventListener: AdminEventListener,
    ) {
        useCreatedKafkaListener.addEventListener(userCreatedEventListener)
        adminKafkaEventListener.addEventListener(adminEventListener)
    }

    @Bean
    @Primary
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper {
        return builder.build<ObjectMapper>().apply {
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            registerKotlinModule()
        }
    }

}