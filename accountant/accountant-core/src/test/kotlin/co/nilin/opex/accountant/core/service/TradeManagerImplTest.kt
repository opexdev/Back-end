package co.nilin.opex.accountant.core.service

import co.nilin.opex.accountant.core.model.*
import co.nilin.opex.accountant.core.spi.*
import co.nilin.opex.matching.engine.core.eventh.events.SubmitOrderEvent
import co.nilin.opex.matching.engine.core.eventh.events.TradeEvent
import co.nilin.opex.matching.engine.core.model.MatchConstraint
import co.nilin.opex.matching.engine.core.model.OrderDirection
import co.nilin.opex.matching.engine.core.model.OrderType
import co.nilin.opex.matching.engine.core.model.Pair
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class TradeManagerImplTest {

    private val financialActionPersister = mockk<FinancialActionPersister>()
    private val financeActionLoader = mockk<FinancialActionLoader>()
    private val orderPersister = mockk<OrderPersister>()
    private val pairConfigLoader = mockk<PairConfigLoader>()
    private val tempEventPersister = mockk<TempEventPersister>()
    private val richOrderPublisher = mockk<RichOrderPublisher>()
    private val richTradePublisher = mockk<RichTradePublisher>()
    private val userLevelLoader = mockk<UserLevelLoader>()
    private val financialActionPublisher = mockk<FinancialActionPublisher>()
    private val jsonMapper = JsonMapperTestImpl()

    private val orderManager = OrderManagerImpl(
        pairConfigLoader,
        userLevelLoader,
        financialActionPersister,
        financeActionLoader,
        orderPersister,
        tempEventPersister,
        richOrderPublisher,
        financialActionPublisher,
        jsonMapper
    )

    private val tradeManager = TradeManagerImpl(
        financialActionPersister,
        financeActionLoader,
        orderPersister,
        tempEventPersister,
        richTradePublisher,
        richOrderPublisher,
        FeeCalculatorImpl("0x0", jsonMapper),
        financialActionPublisher,
        jsonMapper
    )

    init {
        coEvery { tempEventPersister.loadTempEvents(any()) } returns emptyList()
        coEvery { orderPersister.save(any()) } returnsArgument (0)
        coEvery { financeActionLoader.findLast(any(), any()) } returns null
        coEvery { richOrderPublisher.publish(any()) } returns Unit
        coEvery { richTradePublisher.publish(any()) } returns Unit
        coEvery { userLevelLoader.load(any()) } returns "*"
        coEvery { financialActionPublisher.publish(any()) } returns Unit
        coEvery { financialActionPersister.updateStatus(any<FinancialAction>(), any()) } returns Unit
        coEvery { financialActionPersister.updateStatus(any<String>(), any()) } returns Unit
    }

    @Test
    fun givenSellOrder_WhenMatchBuyOrderCome_thenFAMatched(): Unit = runBlocking {
        //given
        val pair = Pair("eth", "btc")
        val pairConfig = PairConfig(
            pair.toString(),
            pair.leftSideName,
            pair.rightSideName,
            BigDecimal.valueOf(1.0),
            BigDecimal.valueOf(0.01)
        )
        val makerSubmitOrderEvent = SubmitOrderEvent(
            "mouid",
            "muuid",
            null,
            pair,
            60000,
            1,
            0,
            OrderDirection.ASK,
            MatchConstraint.GTC,
            OrderType.LIMIT_ORDER
        )
        prepareOrder(pair, pairConfig, makerSubmitOrderEvent, BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.12))

        val takerSubmitOrderEvent = SubmitOrderEvent(
            "touid",
            "tuuid",
            null,
            pair,
            70000,
            1,
            0,
            OrderDirection.BID,
            MatchConstraint.GTC,
            OrderType.LIMIT_ORDER
        )

        prepareOrder(pair, pairConfig, takerSubmitOrderEvent, BigDecimal.valueOf(0.08), BigDecimal.valueOf(0.1))

        val tradeEvent = makeTradeEvent(pair, takerSubmitOrderEvent, makerSubmitOrderEvent)
        //when
        val tradeFinancialActions = tradeManager.handleTrade(tradeEvent)

        assertThat(tradeFinancialActions.size).isEqualTo(4)
        assertThat(tradeFinancialActions[0].category).isEqualTo(FinancialActionCategory.TRADE)
        assertThat(tradeFinancialActions[0].detail).containsKeys("userLevel", "direction", "matchConstraint", "orderType", "eventDate", "tradeId", "makerOrderId", "takerOrderId")
        assertThat(tradeFinancialActions[1].category).isEqualTo(FinancialActionCategory.TRADE)
        assertThat(tradeFinancialActions[2].category).isEqualTo(FinancialActionCategory.FEE)
        assertThat(tradeFinancialActions[3].category).isEqualTo(FinancialActionCategory.FEE)

        assertThat((makerSubmitOrderEvent.price.toBigDecimal() * pairConfig.rightSideFraction).stripTrailingZeros())
            .isEqualTo(tradeFinancialActions[0].amount.stripTrailingZeros())
    }

    @Test
    fun givenBuyOrder_WhenMatchSellOrderCome_thenFAMatched(): Unit = runBlocking {
        //given
        val pair = Pair("eth", "btc")
        val pairConfig = PairConfig(
            pair.toString(),
            pair.leftSideName,
            pair.rightSideName,
            BigDecimal.valueOf(1.0),
            BigDecimal.valueOf(0.001)
        )
        val makerSubmitOrderEvent = SubmitOrderEvent(
            "mouid",
            "muuid",
            null,
            pair,
            70000,
            1,
            0,
            OrderDirection.BID,
            MatchConstraint.GTC,
            OrderType.LIMIT_ORDER
        )
        prepareOrder(pair, pairConfig, makerSubmitOrderEvent, BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.12))

        val takerSubmitOrderEvent = SubmitOrderEvent(
            "touid",
            "tuuid",
            null,
            pair,
            60000,
            1,
            0,
            OrderDirection.ASK,
            MatchConstraint.GTC,
            OrderType.LIMIT_ORDER
        )

        prepareOrder(pair, pairConfig, takerSubmitOrderEvent, BigDecimal.valueOf(0.08), BigDecimal.valueOf(0.1))

        val tradeEvent = makeTradeEvent(pair, takerSubmitOrderEvent, makerSubmitOrderEvent)
        //when
        val tradeFinancialActions = tradeManager.handleTrade(tradeEvent)

        assertThat(tradeFinancialActions.size).isEqualTo(4)
        assertThat((makerSubmitOrderEvent.price.toBigDecimal() * pairConfig.rightSideFraction).stripTrailingZeros())
            .isEqualTo(tradeFinancialActions[1].amount.stripTrailingZeros())
    }

    private fun makeTradeEvent(
        pair: Pair,
        takerSubmitOrderEvent: SubmitOrderEvent,
        makerSubmitOrderEvent: SubmitOrderEvent
    ): TradeEvent {
        return TradeEvent(
            0,
            pair,
            takerSubmitOrderEvent.ouid,
            takerSubmitOrderEvent.uuid,
            takerSubmitOrderEvent.orderId ?: -1,
            takerSubmitOrderEvent.direction,
            takerSubmitOrderEvent.price,
            0,
            makerSubmitOrderEvent.ouid,
            makerSubmitOrderEvent.uuid,
            makerSubmitOrderEvent.orderId ?: 1,
            makerSubmitOrderEvent.direction,
            makerSubmitOrderEvent.price,
            makerSubmitOrderEvent.quantity - takerSubmitOrderEvent.quantity,
            takerSubmitOrderEvent.quantity
        )
    }

    private suspend fun prepareOrder(
        pair: Pair,
        pairConfig: PairConfig,
        submitOrderEvent: SubmitOrderEvent,
        makerFee: BigDecimal,
        takerFee: BigDecimal
    ) {
        coEvery {
            pairConfigLoader.load(pair.toString(), submitOrderEvent.direction, any())
        } returns PairFeeConfig(
            pairConfig,
            submitOrderEvent.direction.toString(),
            "",
            makerFee,
            takerFee
        )
        coEvery { financialActionPersister.persist(any()) } returnsArgument (0)

        val financialActions = orderManager.handleRequestOrder(submitOrderEvent)

        val orderPairFeeConfig =
            pairConfigLoader.load(submitOrderEvent.pair.toString(), submitOrderEvent.direction, "")
        val orderMakerFee = orderPairFeeConfig.makerFee * BigDecimal.ONE //user level formula
        val orderTakerFee = orderPairFeeConfig.takerFee * BigDecimal.ONE //user level formula

        coEvery { orderPersister.load(submitOrderEvent.ouid) } returns Order(
            submitOrderEvent.pair.toString(),
            submitOrderEvent.ouid,
            null,
            orderMakerFee,
            orderTakerFee,
            orderPairFeeConfig.pairConfig.leftSideFraction,
            orderPairFeeConfig.pairConfig.rightSideFraction,
            submitOrderEvent.uuid,
            submitOrderEvent.userLevel,
            submitOrderEvent.direction,
            submitOrderEvent.matchConstraint,
            submitOrderEvent.orderType,
            submitOrderEvent.price,
            submitOrderEvent.quantity,
            submitOrderEvent.quantity - submitOrderEvent.remainedQuantity,
            submitOrderEvent.price.toBigDecimal(),
            submitOrderEvent.quantity.toBigDecimal(),
            (submitOrderEvent.quantity - submitOrderEvent.remainedQuantity).toBigDecimal(),
            financialActions[0].amount,
            financialActions[0].amount,
            0
        )
    }
}