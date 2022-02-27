package co.nilin.opex.wallet.ports.kafka.listener.config

import co.nilin.opex.wallet.ports.kafka.listener.model.AdminEvent
import co.nilin.opex.wallet.ports.kafka.listener.model.UserCreatedEvent
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaProducerConfig {

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Bean
    fun producerConfigs(): Map<String, Any?> {
        return mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all"
        )
    }

    @Bean("walletProducerFactory")
    fun producerFactory(producerConfigs: Map<String, Any?>): ProducerFactory<String?, UserCreatedEvent> {
        return DefaultKafkaProducerFactory(producerConfigs)
    }

    @Bean("walletKafkaTemplate")
    fun kafkaTemplate(factory: ProducerFactory<String?, UserCreatedEvent>): KafkaTemplate<String?, UserCreatedEvent> {
        return KafkaTemplate(factory)
    }

    @Bean
    fun adminProducerFactory(config: Map<String, Any>): ProducerFactory<String?, AdminEvent> {
        return DefaultKafkaProducerFactory(config)
    }

    @Bean
    fun adminKafkaTemplate(factory: ProducerFactory<String?, AdminEvent>): KafkaTemplate<String?, AdminEvent> {
        return KafkaTemplate(factory)
    }
}