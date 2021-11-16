package co.nilin.opex.api.ports.kafka.listener.consumer


import co.nilin.opex.matching.engine.core.eventh.events.CoreEvent
import co.nilin.opex.api.ports.kafka.listener.spi.EventListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.MessageListener
import org.springframework.stereotype.Component

@Component
class EventKafkaListener : MessageListener<String, CoreEvent> {
    val eventListeners = arrayListOf<EventListener>()
    override fun onMessage(data: ConsumerRecord<String, CoreEvent>) {
        eventListeners.forEach { tl ->
            tl.onEvent(data.value(), data.partition(), data.offset(), data.timestamp())
        }
    }

    fun addEventListener(tl: EventListener) {
        eventListeners.add(tl)
    }

    fun removeEventListener(tl: EventListener) {
        eventListeners.removeIf { item ->
            item.id() == tl.id()
        }
    }
}