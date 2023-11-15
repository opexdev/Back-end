package co.nilin.opex.kyc.ports.kafka.eventlistener.consumer


import co.nilin.opex.kyc.core.data.event.UserCreatedEvent
import co.nilin.opex.kyc.core.spi.UserCreatedEventListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.MessageListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserCreatedKafkaListener : MessageListener<String, UserCreatedEvent> {
    val eventListeners = arrayListOf<UserCreatedEventListener>()
    private val logger = LoggerFactory.getLogger(UserCreatedKafkaListener::class.java)
    override fun onMessage(data: ConsumerRecord<String, UserCreatedEvent>) {
        logger.info("kyc on message listener ................")
        logger.info(eventListeners.size.toString())
        eventListeners.forEach { tl ->
            logger.info("incoming new event " + tl.id())
            tl.onEvent(data.value(), data.partition(), data.offset(), data.timestamp(), UUID.randomUUID().toString())
        }
    }

    fun addEventListener(tl: UserCreatedEventListener) {
        eventListeners.add(tl)
    }

    fun removeEventListener(tl: UserCreatedEventListener) {
        eventListeners.removeIf { item ->
            item.id() == tl.id()
        }
    }
}