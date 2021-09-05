package co.nilin.opex.port.api.postgres.dao

import co.nilin.opex.port.api.postgres.model.TradeModel
import co.nilin.opex.port.api.postgres.model.TradeTickerData
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.*

@Repository
interface TradeRepository : ReactiveCrudRepository<TradeModel, Long> {

    @Query("select * from trades where :ouid in (taker_ouid, maker_ouid) ")
    fun findByOuid(@Param("ouid") ouid: String): Flow<TradeModel>

    @Query(
        "select * from trades where :uuid in (taker_uuid, maker_uuid) " +
                "and (:fromTrade is null or id > :fromTrade) " +
                "and (:symbol is null or symbol = :symbol) " +
                "and (:startTime is null or trade_date >= :startTime) " +
                "and (:endTime is null or trade_date < :endTime)"
    )
    fun findByUuidAndSymbolAndTimeBetweenAndTradeIdGreaterThan(
        @Param("uuid")
        uuid: String,
        @Param("symbol")
        symbol: String?,
        @Param("fromTrade")
        fromTrade: Long?,
        @Param("startTime")
        startTime: Date?,
        @Param("endTime")
        endTime: Date?
    ): Flow<TradeModel>

    @Query("select * from trades where symbol = :symbol order by create_date desc limit :limit")
    fun findBySymbolSortDescendingByCreateDate(
        @Param("symbol")
        symbol: String,
        @Param("limit")
        limit: Int
    ): Flow<TradeModel>

    @Query(
        """
        select symbol, 
        (select taker_price from trades where create_date > :date and symbol=t.symbol order by create_date desc limit 1) - (select taker_price from trades where create_date > :date and symbol=t.symbol order by create_date asc limit 1) as price_change,
        ((((select taker_price from trades where create_date > :date and symbol=t.symbol order by create_date desc limit 1) - (select taker_price from trades where create_date > :date and symbol=t.symbol order by create_date asc limit 1))/(select taker_price from trades where create_date > :date and symbol=t.symbol order by create_date asc limit 1))*100) as price_change_percent, 
        (sum(matched_quantity)/sum(taker_price)) as weighted_avg_price,
        (select taker_price from trades where create_date > :date and symbol=t.symbol order by create_date asc limit 1) as last_price, 
        (select matched_quantity from trades where create_date > :date and symbol=t.symbol order by create_date asc limit 1) as last_qty, 
        (select price from orders where create_date > :date and symbol=t.symbol and (status=1 or status=4) and side='BID' order by create_date desc limit 1) as bid_price,
        (select price from orders where create_date > :date and symbol=t.symbol and (status=1 or status=4) and side='ASK' order by create_date asc limit 1) as ask_price, 
        (select price from orders where create_date > :date and symbol=t.symbol and (status=1 or status=4) order by create_date desc limit 1) as open_price, 
        max(taker_price) as high_price, 
        min(taker_price) as low_price, 
        sum(matched_quantity) as volume, 
        (select id from trades where create_date > :date and symbol=t.symbol order by create_date asc limit 1) as first_id, 
        (select id from trades where create_date > :date and symbol=t.symbol order by create_date desc limit 1) as last_id, 
        count(id) as count
        from trades as t 
        where create_date > :date
        group by symbol
        """
    )
    fun tradeTicker(@Param("date") createDate: LocalDateTime): Flux<TradeTickerData>

    @Query(
        """
        select symbol, 
        (select taker_price from trades where create_date > :date and symbol=:symbol order by create_date desc limit 1) - (select taker_price from trades where create_date > :date and symbol=:symbol order by create_date asc limit 1) as price_change,
        ((((select taker_price from trades where create_date > :date and symbol=:symbol order by create_date desc limit 1) - (select taker_price from trades where create_date > :date and symbol=:symbol order by create_date asc limit 1))/(select taker_price from trades where create_date > :date and symbol=:symbol order by create_date asc limit 1))*100) as price_change_percent, 
        (sum(matched_quantity)/sum(taker_price)) as weighted_avg_price,
        (select taker_price from trades where create_date > :date and symbol=:symbol order by create_date asc limit 1) as last_price, 
        (select matched_quantity from trades where create_date > :date and symbol=:symbol order by create_date asc limit 1) as last_qty, 
        (select price from orders where create_date > :date and symbol=t.symbol and (status=1 or status=4) and side='BID' order by create_date desc limit 1) as bid_price,
        (select price from orders where create_date > :date and symbol=t.symbol and (status=1 or status=4) and side='ASK' order by create_date asc limit 1) as ask_price, 
        (select price from orders where create_date > :date and symbol=t.symbol and (status=1 or status=4) order by create_date desc limit 1) as open_price, 
        max(taker_price) as high_price, 
        min(taker_price) as low_price, 
        sum(matched_quantity) as volume, 
        (select id from trades where create_date > :date and symbol=:symbol order by create_date asc limit 1) as first_id, 
        (select id from trades where create_date > :date and symbol=:symbol order by create_date desc limit 1) as last_id, 
        count(id) as count
        from trades as t 
        where create_date > :date and symbol = :symbol
        group by symbol
        """
    )
    fun tradeTickerBySymbol(
        @Param("symbol")
        symbol: String,
        @Param("date")
        createDate: LocalDateTime,
    ): Mono<TradeTickerData>
}