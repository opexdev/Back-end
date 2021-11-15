package co.nilin.opex.port.accountant.kafka.spi

import co.nilin.opex.matching.engine.core.eventh.events.CoreEvent

interface TempEventListener {
    fun id(): String
    fun onEvent(coreEvent: CoreEvent, partition: Int, offset: Long, timestamp: Long)
}