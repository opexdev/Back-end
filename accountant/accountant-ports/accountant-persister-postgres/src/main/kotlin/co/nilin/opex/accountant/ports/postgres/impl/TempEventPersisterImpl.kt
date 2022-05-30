package co.nilin.opex.accountant.ports.postgres.impl

import co.nilin.opex.accountant.core.model.TempEvent
import co.nilin.opex.accountant.core.spi.TempEventPersister
import co.nilin.opex.accountant.ports.postgres.dao.TempEventRepository
import co.nilin.opex.accountant.ports.postgres.model.TempEventModel
import co.nilin.opex.matching.engine.core.eventh.events.CoreEvent
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class TempEventPersisterImpl(private val tempEventRepository: TempEventRepository) : TempEventPersister {

    override suspend fun saveTempEvent(ouid: String, event: CoreEvent) {
        tempEventRepository.save(
            TempEventModel(
                null,
                ouid,
                event.javaClass.name,
                Gson().toJson(event),
                LocalDateTime.now()
            )
        ).awaitSingleOrNull()
    }

    override suspend fun loadTempEvents(ouid: String): List<CoreEvent> {
        return tempEventRepository
            .findByOuid(ouid)
            .map { Gson().fromJson(it.eventBody, Class.forName(it.eventType)) as CoreEvent }
            .toList()
    }

    override suspend fun removeTempEvents(ouid: String) {
        tempEventRepository.deleteByOuid(ouid).awaitFirstOrNull()
    }

    override suspend fun removeTempEvents(tempEvents: List<TempEvent>) {
        tempEventRepository.deleteAll(tempEvents.map {
            TempEventModel(
                it.id,
                it.ouid,
                it.eventBody.javaClass.name,
                "",
                it.eventDate
            )
        }).awaitFirstOrNull()
    }

    override suspend fun fetchTempEvents(offset: Long, size: Long): List<TempEvent> {
        return tempEventRepository
            .findAll(PageRequest.of(offset.toInt(), size.toInt(), Sort.by(Sort.Direction.ASC, "eventDate")))
            .map {
                TempEvent(
                    it.id!!,
                    it.ouid,
                    Gson().fromJson(it.eventBody, Class.forName(it.eventType)) as CoreEvent,
                    it.eventDate
                )
            }
            .toList()
    }
}