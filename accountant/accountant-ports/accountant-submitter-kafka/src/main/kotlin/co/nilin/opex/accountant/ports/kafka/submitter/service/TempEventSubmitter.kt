package co.nilin.opex.accountant.ports.kafka.submitter.service

import co.nilin.opex.accountant.core.spi.TempEventRepublisher
import co.nilin.opex.matching.engine.core.eventh.events.CoreEvent
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Component
class TempEventSubmitter(@Qualifier("accountantEventKafkaTemplate") val kafkaTemplate: KafkaTemplate<String, CoreEvent>) :
    TempEventRepublisher {
    override suspend fun republish(events: List<CoreEvent>): Unit = suspendCoroutine { cont ->
        println("accountantEventSubmit!")
        events.forEach { event ->
            val sendFuture = kafkaTemplate.send("tempevents", event)
            sendFuture.addCallback({ sendResult ->
                cont.resume(Unit)
            }, { exception ->
                cont.resumeWithException(exception)
            })
        }
    }
}