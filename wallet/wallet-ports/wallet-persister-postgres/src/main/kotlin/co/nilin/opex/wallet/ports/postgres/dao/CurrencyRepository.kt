package co.nilin.opex.wallet.ports.postgres.dao

import co.nilin.opex.wallet.ports.postgres.model.CurrencyModel
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.security.cert.TrustAnchor
import java.time.LocalDateTime

@Repository
interface CurrencyRepository : ReactiveCrudRepository<CurrencyModel, String> {

    @Query("select * from currency where symbol = :symbol")
    fun findBySymbol(symbol: String): Mono<CurrencyModel>?

    @Query("insert into currency values (:symbol, :name, :precision) on conflict do nothing")
    fun insert(name: String, symbol: String, precision: BigDecimal): Mono<CurrencyModel>

    @Query("delete from currency where name = :name")
    fun deleteByName(name: String): Mono<Void>


    fun deleteBySymbol(symbol: String): Mono<Void>

    @Query("insert into currency values (:symbol, :name, :precision, :title, :alias, :maxDeposit, :minDeposit, :minWithdraw, :maxWithdraw, :icon, :lastUpdateDate, :createDate, :isTransitive, :isActive, :sign, :description, :shortDescription) on conflict do nothing")
    fun insert(name: String, symbol: String, precision: BigDecimal,
               title: String? = null,
               alias: String? = null,
               maxDeposit: BigDecimal? = BigDecimal.TEN,
               minDeposit: BigDecimal? = BigDecimal.ZERO,
               minWithdraw: BigDecimal? = BigDecimal.TEN,
               maxWithdraw: BigDecimal? = BigDecimal.ZERO,
               icon: String? = null,
               createDate: LocalDateTime? = null,
               lastUpdateDate: LocalDateTime? = null,
               isTransitive: Boolean? = false,
               isActive: Boolean? = true,
               sign: String? = null,
               description: String? = null,
               shortDescription: String? = null): Mono<CurrencyModel?>?

    fun findByIsTransitive(isTransitive: Boolean): Flux<CurrencyModel>?

}
