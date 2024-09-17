package co.nilin.opex.wallet.ports.postgres.dao

import co.nilin.opex.wallet.ports.postgres.model.ReservedTransferModel
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface ReservedTransferRepository : ReactiveCrudRepository<ReservedTransferModel, Long> {
    fun findByReserveNumber(reservedNumber:String): Mono<ReservedTransferModel>?
}