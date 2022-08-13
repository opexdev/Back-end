package co.nilin.opex.market.ports.postgres.impl

import co.nilin.opex.market.core.inout.*
import co.nilin.opex.market.core.spi.MarketQueryHandler
import co.nilin.opex.market.ports.postgres.dao.OrderRepository
import co.nilin.opex.market.ports.postgres.dao.OrderStatusRepository
import co.nilin.opex.market.ports.postgres.dao.TradeRepository
import co.nilin.opex.market.ports.postgres.model.TradeTickerData
import co.nilin.opex.market.ports.postgres.util.asOrderDTO
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrElse
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

@Component
class MarketQueryHandlerImpl(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository,
    private val orderStatusRepository: OrderStatusRepository
) : MarketQueryHandler {

    //TODO merge order and status fetching in query

    override suspend fun getTradeTickerData(startFrom: LocalDateTime): List<PriceChange> {
        return tradeRepository.tradeTicker(startFrom)
            .collectList()
            .awaitFirstOrElse { emptyList() }
            .map { it.asPriceChangeResponse(Date().time, startFrom.toInstant(ZoneOffset.UTC).toEpochMilli()) }

    }

    override suspend fun getTradeTickerDateBySymbol(symbol: String, startFrom: LocalDateTime): PriceChange? {
        return tradeRepository.tradeTickerBySymbol(symbol, startFrom)
            .awaitFirstOrNull()
            ?.asPriceChangeResponse(Date().time, startFrom.toInstant(ZoneOffset.UTC).toEpochMilli())
    }

    override suspend fun openBidOrders(symbol: String, limit: Int): List<OrderBook> {
        return orderRepository.findBySymbolAndDirectionAndStatusSortDescendingByPrice(
            symbol,
            OrderDirection.BID,
            limit,
            listOf(OrderStatus.NEW.code, OrderStatus.PARTIALLY_FILLED.code)
        ).collectList()
            .awaitFirstOrElse { emptyList() }
            .map { OrderBook(it.price, it.quantity) }
    }

    override suspend fun openAskOrders(symbol: String, limit: Int): List<OrderBook> {
        return orderRepository.findBySymbolAndDirectionAndStatusSortAscendingByPrice(
            symbol,
            OrderDirection.ASK,
            limit,
            listOf(OrderStatus.NEW.code, OrderStatus.PARTIALLY_FILLED.code)
        ).collectList()
            .awaitFirstOrElse { emptyList() }
            .map { OrderBook(it.price, it.quantity) }
    }

    override suspend fun lastOrder(symbol: String): Order? {
        val order = orderRepository.findLastOrderBySymbol(symbol).awaitFirstOrNull() ?: return null
        val status = orderStatusRepository.findMostRecentByOUID(order.ouid).awaitFirstOrNull()
        return order.asOrderDTO(status)
    }

    override suspend fun recentTrades(symbol: String, limit: Int): List<MarketTrade> {
        return tradeRepository.findBySymbolSortDescendingByCreateDate(symbol, limit)
            .map {
                val takerOrder = orderRepository.findByOuid(it.takerOuid).awaitFirst()
                val makerOrder = orderRepository.findByOuid(it.makerOuid).awaitFirst()
                val isMakerBuyer = makerOrder.direction == OrderDirection.BID
                MarketTrade(
                    it.symbol,
                    it.baseAsset,
                    it.quoteAsset,
                    it.tradeId,
                    it.matchedPrice,
                    it.matchedQuantity,
                    if (isMakerBuyer)
                        makerOrder.quoteQuantity!!
                    else
                        takerOrder.quoteQuantity!!,
                    Date.from(it.createDate.atZone(ZoneId.systemDefault()).toInstant()),
                    true,
                    isMakerBuyer
                )
            }.toList()
    }

    override suspend fun lastPrice(symbol: String?): List<PriceTicker> {
        val list = if (symbol.isNullOrEmpty())
            tradeRepository.findAllGroupBySymbol()
        else
            tradeRepository.findBySymbolGroupBySymbol(symbol)
        return list.collectList()
            .awaitFirstOrElse { emptyList() }
            .map { PriceTicker(it.symbol, it.matchedPrice.toString()) }
    }

    override suspend fun getBestPriceForSymbols(symbols: List<String>): List<BestPrice> {
        return tradeRepository.bestAskAndBidPrice(symbols)
            .collectList()
            .awaitFirstOrElse { emptyList() }
    }

    override suspend fun getCandleInfo(
        symbol: String,
        interval: String,
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<CandleData> {
        val st = if (startTime == null)
            tradeRepository.findFirstByCreateDate().awaitFirstOrNull()?.createDate
                ?: LocalDateTime.now()
        else
            with(Instant.ofEpochMilli(startTime)) {
                LocalDateTime.ofInstant(this, ZoneId.systemDefault())
            }

        val et = if (endTime == null)
            tradeRepository.findLastByCreateDate().awaitFirstOrNull()?.createDate
                ?: LocalDateTime.now()
        else
            with(Instant.ofEpochMilli(endTime)) {
                LocalDateTime.ofInstant(this, ZoneId.systemDefault())
            }

        return tradeRepository.candleData(symbol, interval, st, et, limit)
            .collectList()
            .awaitFirstOrElse { emptyList() }
            .map {
                CandleData(
                    it.openTime,
                    it.closeTime,
                    it.open ?: BigDecimal.ZERO,
                    it.close ?: BigDecimal.ZERO,
                    it.high ?: BigDecimal.ZERO,
                    it.low ?: BigDecimal.ZERO,
                    it.volume ?: BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    it.trades,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
                )
            }
    }

    override suspend fun numberOfActiveUsers(interval: LocalDateTime): Long {
        return orderRepository.countUsersWhoMadeOrder(interval).singleOrNull() ?: 0L
    }

    override suspend fun numberOfTrades(interval: LocalDateTime, pair: String?): Long {
        return if (pair != null)
            tradeRepository.countBySymbolNewerThan(interval, pair).singleOrNull() ?: 0
        else
            tradeRepository.countNewerThan(interval).singleOrNull() ?: 0
    }

    override suspend fun numberOfOrders(interval: LocalDateTime, pair: String?): Long {
        return if (pair != null)
            orderRepository.countBySymbolNewerThan(interval, pair).singleOrNull() ?: 0
        else
            orderRepository.countNewerThan(interval).singleOrNull() ?: 0
    }

    override suspend fun mostIncreasePrice(interval: LocalDateTime, limit: Int): List<PriceStat> {
        return tradeRepository.findByMostIncreasedPrice(interval, limit)
            .collectList()
            .awaitFirstOrElse { emptyList() }
    }

    override suspend fun mostDecreasePrice(interval: LocalDateTime, limit: Int): List<PriceStat> {
        return tradeRepository.findByMostDecreasedPrice(interval, limit)
            .collectList()
            .awaitFirstOrElse { emptyList() }
    }

    override suspend fun mostVolume(interval: LocalDateTime): TradeVolumeStat? {
        return tradeRepository.findByMostVolume(interval).awaitSingleOrNull()
    }

    override suspend fun mostTrades(interval: LocalDateTime): TradeVolumeStat? {
        return tradeRepository.findByMostTrades(interval).awaitSingleOrNull()
    }

    private fun TradeTickerData.asPriceChangeResponse(openTime: Long, closeTime: Long) = PriceChange(
        symbol,
        priceChange ?: BigDecimal.ZERO,
        priceChangePercent ?: BigDecimal.ZERO,
        weightedAvgPrice ?: BigDecimal.ZERO,
        lastPrice ?: BigDecimal.ZERO,
        lastQty ?: BigDecimal.ZERO,
        bidPrice ?: BigDecimal.ZERO,
        askPrice ?: BigDecimal.ZERO,
        openPrice ?: BigDecimal.ZERO,
        highPrice ?: BigDecimal.ZERO,
        lowPrice ?: BigDecimal.ZERO,
        volume ?: BigDecimal.ZERO,
        openTime,
        closeTime,
        firstId ?: -1,
        lastId ?: -1,
        count ?: 0
    )
}
