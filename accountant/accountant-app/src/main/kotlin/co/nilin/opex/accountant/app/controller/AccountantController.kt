package co.nilin.opex.accountant.app.controller

import co.nilin.opex.accountant.app.data.PairFeeResponse
import co.nilin.opex.accountant.core.model.PairConfig
import co.nilin.opex.accountant.core.model.PairFeeConfig
import co.nilin.opex.accountant.core.spi.FinancialActionLoader
import co.nilin.opex.accountant.core.spi.PairConfigLoader
import co.nilin.opex.accountant.core.spi.WalletProxy
import co.nilin.opex.accountant.ports.walletproxy.data.BooleanResponse
import co.nilin.opex.common.OpexError
import co.nilin.opex.matching.engine.core.eventh.events.SubmitOrderEvent
import co.nilin.opex.matching.engine.core.model.OrderDirection
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
class AccountantController(
    val walletProxy: WalletProxy,
    val financialActionLoader: FinancialActionLoader,
    val pairConfigLoader: PairConfigLoader
) {

    private val logger = LoggerFactory.getLogger(AccountantController::class.java)

    @GetMapping("{uuid}/create_order/{amount}_{currency}/allowed")
    suspend fun canCreateOrder(
        @PathVariable("uuid") uuid: String,
        @PathVariable("currency") currency: String,
        @PathVariable("amount") amount: BigDecimal
    ): BooleanResponse {
        val canFulfil = runCatching { walletProxy.canFulfil(currency, "main", uuid, amount) }
            .onFailure { logger.error(it.message) }
            .getOrElse { false }
        if (canFulfil) {
            val unprocessed =
                financialActionLoader.countUnprocessed(uuid, currency, SubmitOrderEvent::class.simpleName!!)
            return BooleanResponse(unprocessed <= 0)
        } else
            return BooleanResponse(false)
    }

    @GetMapping("/config/{pair}/fee/{direction}-{userLevel}")
    suspend fun fetchPairFeeConfig(
        @PathVariable("pair") pair: String,
        @PathVariable("direction") direction: OrderDirection,
        @PathVariable("userLevel") level: String
    ): PairFeeConfig {
        return pairConfigLoader.load(pair, direction, level)
    }

    @GetMapping("/config/{pair}/{direction}")
    suspend fun fetchPairConfig(
        @PathVariable("pair") pair: String,
        @PathVariable("direction") direction: OrderDirection
    ): PairConfig {
        return pairConfigLoader.load(pair, direction)
    }

    @GetMapping("/config/all")
    suspend fun fetchPairConfigs(): List<PairConfig> {
        return pairConfigLoader.loadPairConfigs()
    }

    @GetMapping("/config/fee")
    suspend fun getFeeConfigs(): List<PairFeeResponse> {
        return pairConfigLoader.loadPairFeeConfigs()
            .map { PairFeeResponse(it.pairConfig.pair, it.direction, it.userLevel, it.makerFee, it.takerFee) }
    }

    @GetMapping("/config/fee/{pair}")
    suspend fun getFeeConfig(
        @PathVariable pair: String,
        @RequestParam(required = false) direction: OrderDirection?,
        @RequestParam(required = false) userLevel: String?
    ): PairFeeResponse {
        val fee = pairConfigLoader.loadPairFeeConfigs(pair, direction ?: OrderDirection.BID, userLevel ?: "*")
            ?: throw OpexError.PairFeeNotFound.exception()
        return PairFeeResponse(fee.pairConfig.pair, fee.direction, fee.userLevel, fee.makerFee, fee.takerFee)
    }
}