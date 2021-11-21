package co.nilin.opex.accountant.core.spi

import co.nilin.opex.accountant.core.model.TempEvent
import co.nilin.opex.matching.engine.core.eventh.events.CoreEvent

interface TempEventPersister {
    suspend fun saveTempEvent(ouid: String, event: CoreEvent)
    suspend fun loadTempEvents(ouid: String): List<CoreEvent>
    suspend fun removeTempEvents(ouid: String)
    suspend fun removeTempEvents(tempEvents: List<TempEvent>)
    suspend fun fetchTempEvents(offset: Long, size: Long): List<TempEvent>
}