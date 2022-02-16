package co.nilin.opex.accountant.core.service

import co.nilin.opex.accountant.core.api.OrderManager
import co.nilin.opex.accountant.core.inout.OrderStatus
import co.nilin.opex.accountant.core.inout.RichOrder
import co.nilin.opex.accountant.core.model.FinancialAction
import co.nilin.opex.accountant.core.model.Order
import co.nilin.opex.accountant.core.spi.*
import co.nilin.opex.matching.engine.core.eventh.events.*
import co.nilin.opex.matching.engine.core.model.OrderDirection
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

open class OrderManagerImpl(
    private val pairConfigLoader: PairConfigLoader,
    private val financialActionPersister: FinancialActionPersister,
    private val financeActionLoader: FinancialActionLoader,
    private val orderPersister: OrderPersister,
    private val tempEventPersister: TempEventPersister,
    private val tempEventRepublisher: TempEventRepublisher,
    private val richOrderPublisher: RichOrderPublisher
) : OrderManager {

    @Transactional
    override suspend fun handleRequestOrder(submitOrderEvent: SubmitOrderEvent): List<FinancialAction> {
        //pair + dir -> symbol
        //user level?
        //pair config.makerFee and takerFee
        val symbol = if (submitOrderEvent.direction == OrderDirection.ASK) {
            submitOrderEvent.pair.leftSideName
        } else {
            submitOrderEvent.pair.rightSideName
        }
        val pairFeeConfig = pairConfigLoader.load(submitOrderEvent.pair.toString(), submitOrderEvent.direction, "")
        val makerFee = pairFeeConfig.makerFee * 1 //user level formula
        val takerFee = pairFeeConfig.takerFee * 1 //user level formula

        //create fa for transfer uuid symbol main wallet to uuid symbol exchange wallet
        /*
        amount for sell (ask): quantity
        amount for buy (bid): quantity * price
         */
        val financialAction =
            FinancialAction(
                null,
                SubmitOrderEvent::class.simpleName!!,
                submitOrderEvent.ouid,
                symbol,
                if (submitOrderEvent.direction == OrderDirection.ASK) {
                    BigDecimal(submitOrderEvent.quantity).multiply(pairFeeConfig.pairConfig.leftSideFraction.toBigDecimal())
                } else {
                    BigDecimal(submitOrderEvent.quantity).multiply(pairFeeConfig.pairConfig.leftSideFraction.toBigDecimal())
                        .multiply(submitOrderEvent.price.toBigDecimal())
                        .multiply(pairFeeConfig.pairConfig.rightSideFraction.toBigDecimal())
                },
                submitOrderEvent.uuid,
                "main",
                submitOrderEvent.uuid,
                "exchange",
                LocalDateTime.now()
            )
        //store order (ouid, uuid, fees, userlevel, pair, direction, price, quantity, filledQ, status, transfered)
        orderPersister.save(
            Order(
                submitOrderEvent.pair.toString(),
                submitOrderEvent.ouid,
                null,
                makerFee,
                takerFee,
                pairFeeConfig.pairConfig.leftSideFraction,
                pairFeeConfig.pairConfig.rightSideFraction,
                submitOrderEvent.uuid,
                "",
                submitOrderEvent.direction,
                submitOrderEvent.matchConstraint,
                submitOrderEvent.orderType,
                submitOrderEvent.price,
                submitOrderEvent.quantity,
                submitOrderEvent.quantity - submitOrderEvent.remainedQuantity,
                submitOrderEvent.price.toBigDecimal()
                    .multiply(pairFeeConfig.pairConfig.rightSideFraction.toBigDecimal()),
                submitOrderEvent.quantity.toBigDecimal()
                    .multiply(pairFeeConfig.pairConfig.leftSideFraction.toBigDecimal()),
                BigDecimal(submitOrderEvent.quantity - submitOrderEvent.remainedQuantity).multiply(pairFeeConfig.pairConfig.leftSideFraction.toBigDecimal()),
                financialAction.amount,
                financialAction.amount,
                OrderStatus.REQUESTED.code
            )
        )
        return financialActionPersister.persist(listOf(financialAction))
    }

    @Transactional
    override suspend fun handleNewOrder(createOrderEvent: CreateOrderEvent): List<FinancialAction> {
        //update order add id to other fields
        val order = orderPersister.load(createOrderEvent.ouid)
        if (order != null) {
            order.matchingEngineId = createOrderEvent.orderId
            orderPersister.save(order)
            //new order accepted by engine
            publishRichOrder(order, createOrderEvent.remainedQuantity.toBigDecimal())
        } else {
            tempEventPersister.saveTempEvent(createOrderEvent.ouid, createOrderEvent)
        }
        return emptyList()
    }

    private suspend fun publishRichOrder(order: Order, remainedQuantity: BigDecimal, status: OrderStatus? = null) {
        richOrderPublisher.publish(
            RichOrder(
                order.id,
                order.pair,
                order.ouid,
                order.uuid,
                order.userLevel,
                order.makerFee.toBigDecimal(),
                order.takerFee.toBigDecimal(),
                order.leftSideFraction.toBigDecimal(),
                order.rightSideFraction.toBigDecimal(),
                order.direction,
                order.matchConstraint,
                order.orderType,
                order.origPrice,
                order.origQuantity,
                order.origPrice.multiply(order.origQuantity),
                order.quantity.toBigDecimal().subtract(remainedQuantity)
                    .multiply(order.leftSideFraction.toBigDecimal()),
                order.origPrice.multiply(
                    order.quantity.toBigDecimal().subtract(remainedQuantity)
                ),
                status?.code ?: if (remainedQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    OrderStatus.FILLED.code
                } else if (remainedQuantity.compareTo(order.quantity.toBigDecimal()) == 0) {
                    OrderStatus.NEW.code
                } else {
                    OrderStatus.PARTIALLY_FILLED.code
                }
            )
        )
    }

    override suspend fun handleUpdateOrder(updatedOrderEvent: UpdatedOrderEvent): List<FinancialAction> {
        TODO("Not yet implemented")
    }

    @Transactional
    override suspend fun handleRejectOrder(rejectOrderEvent: RejectOrderEvent): List<FinancialAction> {
        //order by ouid
        val order = orderPersister.load(rejectOrderEvent.ouid)
        if (order == null) {
            tempEventPersister.saveTempEvent(rejectOrderEvent.ouid, rejectOrderEvent)
            return emptyList()
        }
        val symbol = if (rejectOrderEvent.direction == OrderDirection.ASK) {
            rejectOrderEvent.pair.leftSideName
        } else {
            rejectOrderEvent.pair.rightSideName
        }
        //check uuid
        //lookup for parent fa
        val parentFinancialAction = financeActionLoader.findLast(rejectOrderEvent.uuid, rejectOrderEvent.ouid)
        //create fa for transfer remaining transfered uuid symbol exchange wallet to uuid main exchange wallet
        val financialAction = FinancialAction(
            parentFinancialAction,
            RejectOrderEvent::class.simpleName!!,
            rejectOrderEvent.ouid,
            symbol,
            order.remainedTransferAmount,
            rejectOrderEvent.uuid,
            "exchange",
            rejectOrderEvent.uuid,
            "main",
            LocalDateTime.now()
        )
        //update order status
        order.status = OrderStatus.REJECTED.code
        orderPersister.save(order)
        publishRichOrder(order, order.quantity.toBigDecimal(), OrderStatus.REJECTED)
        return financialActionPersister.persist(listOf(financialAction))
    }

    @Transactional
    override suspend fun handleCancelOrder(cancelOrderEvent: CancelOrderEvent): List<FinancialAction> {
        //order by ouid
        val order = orderPersister.load(cancelOrderEvent.ouid)
        if (order == null) {
            tempEventPersister.saveTempEvent(cancelOrderEvent.ouid, cancelOrderEvent)
            return emptyList()
        }
        val symbol = if (cancelOrderEvent.direction == OrderDirection.ASK) {
            cancelOrderEvent.pair.leftSideName
        } else {
            cancelOrderEvent.pair.rightSideName
        }
        //check uuid
        //lookup for parent fa
        val parentFinancialAction = financeActionLoader.findLast(cancelOrderEvent.uuid, cancelOrderEvent.ouid)
        //create fa for transfer remaining transfered uuid symbol exchange wallet to uuid main exchange wallet
        val financialAction = FinancialAction(
            parentFinancialAction,
            RejectOrderEvent::class.simpleName!!,
            cancelOrderEvent.ouid,
            symbol,
            order.remainedTransferAmount,
            cancelOrderEvent.uuid,
            "exchange",
            cancelOrderEvent.uuid,
            "main",
            LocalDateTime.now()
        )
        //update order status
        order.status = OrderStatus.CANCELED.code
        orderPersister.save(order)
        publishRichOrder(order, cancelOrderEvent.quantity.toBigDecimal(), OrderStatus.CANCELED)
        return financialActionPersister.persist(listOf(financialAction))
    }
}