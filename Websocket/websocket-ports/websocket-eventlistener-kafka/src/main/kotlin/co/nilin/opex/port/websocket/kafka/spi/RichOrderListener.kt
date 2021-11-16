package co.nilin.opex.port.websocket.kafka.spi

import co.nilin.opex.accountant.core.inout.RichOrder

interface RichOrderListener {
    fun id(): String
    fun onOrder(order: RichOrder, partition: Int, offset: Long, timestamp: Long)
}