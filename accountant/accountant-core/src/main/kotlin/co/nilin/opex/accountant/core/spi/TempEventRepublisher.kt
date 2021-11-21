package co.nilin.opex.accountant.core.spi

import co.nilin.opex.matching.engine.core.eventh.events.CoreEvent

interface TempEventRepublisher {
    suspend fun republish(events: List<CoreEvent>)
}