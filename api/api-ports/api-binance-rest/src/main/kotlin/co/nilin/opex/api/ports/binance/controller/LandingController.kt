package co.nilin.opex.api.ports.binance.controller

import co.nilin.opex.api.core.spi.MarketDataProxy
import co.nilin.opex.api.core.spi.MarketStatProxy
import co.nilin.opex.api.core.spi.SymbolMapper
import co.nilin.opex.api.core.utils.Interval
import co.nilin.opex.api.ports.binance.data.MarketInfoResponse
import co.nilin.opex.api.ports.binance.data.MarketStatResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController // Custom service
@RequestMapping("/v1/landing")
class LandingController(
    private val marketStatProxy: MarketStatProxy,
    private val marketDataProxy: MarketDataProxy,
    private val symbolMapper: SymbolMapper
) {

    @GetMapping("/prices")
    suspend fun getCurrencyPrices(
        @RequestParam interval: String,
        @RequestParam basedOn: String
    ): List<String> {
        val result = arrayListOf<String>()
        val since = (Interval.findByLabel(interval) ?: Interval.Day).getDate().time

        return result
    }

    @GetMapping("/marketStats")
    suspend fun getMarketStats(
        @RequestParam interval: String,
        @RequestParam(required = false) limit: Int?
    ): MarketStatResponse {
        val since = (Interval.findByLabel(interval) ?: Interval.Day).getDate().time
        val validLimit = getValidLimit(limit)

        return MarketStatResponse(
            marketStatProxy.getMostIncreasedInPricePairs(since, validLimit),
            marketStatProxy.getMostDecreasedInPricePairs(since, validLimit),
            marketStatProxy.getHighestVolumePair(since),
            marketStatProxy.getTradeCountPair(since)
        )
    }

    @GetMapping("/exchangeInfo")
    suspend fun marketInfo(@RequestParam interval: String): MarketInfoResponse {
        val since = (Interval.findByLabel(interval) ?: Interval.ThreeMonth).getDate().time
        return MarketInfoResponse(
            marketDataProxy.countActiveUsers(since),
            marketDataProxy.countTotalOrders(since),
            marketDataProxy.countTotalTrades(since),
        )
    }

    private fun getValidLimit(limit: Int?): Int = when {
        limit == null -> 100
        limit > 1000 -> 1000
        limit < 1 -> 1
        else -> limit
    }

}