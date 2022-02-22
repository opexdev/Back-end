package co.nilin.opex.bcgateway.ports.postgres.dao

import co.nilin.opex.bcgateway.ports.postgres.model.CurrencyImplementationModel
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface CurrencyImplementationRepository : ReactiveCrudRepository<CurrencyImplementationModel, Long> {

    fun findBySymbol(symbol: String): Flow<CurrencyImplementationModel>

    fun findByChain(chain: String): Flow<CurrencyImplementationModel>

    fun findBySymbolAndChain(symbol: String, chain: String): Mono<CurrencyImplementationModel>

    fun findByChainAndTokenAddress(chain: String, tokenAddress: String?): Mono<CurrencyImplementationModel>
}
