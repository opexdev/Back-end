package co.nilin.opex.app.service

import co.nilin.opex.app.inout.CancelOrderRequest
import co.nilin.opex.app.inout.CreateOrderRequest
import co.nilin.opex.app.spi.AccountantApiProxy
import co.nilin.opex.app.spi.PairConfigLoader
import co.nilin.opex.matching.core.eventh.events.CancelOrderEvent
import co.nilin.opex.matching.core.model.OrderDirection
import co.nilin.opex.matching.core.model.Pair
import co.nilin.opex.port.order.kafka.inout.OrderSubmitRequest
import co.nilin.opex.port.order.kafka.inout.OrderSubmitResult
import co.nilin.opex.port.order.kafka.service.EventSubmitter
import co.nilin.opex.port.order.kafka.service.OrderSubmitter
import co.nilin.opex.utility.error.data.OpexError
import co.nilin.opex.utility.error.data.throwError
import org.springframework.stereotype.Service
import java.util.*

@Service
class OrderService(
    val accountantApiProxy: AccountantApiProxy,
    val orderSubmitter: OrderSubmitter,
    val eventSubmitter: EventSubmitter,
    val pairConfigLoader: PairConfigLoader
) {
    suspend fun submitNewOrder(createOrderRequest: CreateOrderRequest): OrderSubmitResult {
        val symbolSides = createOrderRequest.pair.split("_")
        val symbol = if (createOrderRequest.direction == OrderDirection.ASK)
            symbolSides[0]
        else
            symbolSides[1]
        if (!accountantApiProxy.canCreateOrder(
                createOrderRequest.uuid!!,
                symbol,
                if (createOrderRequest.direction == OrderDirection.ASK)
                    createOrderRequest.quantity
                else
                    createOrderRequest.quantity.multiply(createOrderRequest.price)
            )
        ) {
            throwError(OpexError.SubmitOrderForbiddenByAccountant)
        }
        val pairFeeConfig = pairConfigLoader.load(
            createOrderRequest.pair, createOrderRequest.direction, ""
        )
        val orderSubmitRequest = OrderSubmitRequest(
            UUID.randomUUID().toString(),
            createOrderRequest.uuid!! //get from auth2
            ,
            null,
            Pair(symbolSides[0], symbolSides[1]),
            createOrderRequest.price.divide(
                pairFeeConfig.pairConfig.rightSideFraction
                    .toBigDecimal()
            ).longValueExact(),
            createOrderRequest.quantity.divide(
                pairFeeConfig.pairConfig.leftSideFraction
                    .toBigDecimal()
            )
                .longValueExact(),
            createOrderRequest.direction,
            createOrderRequest.matchConstraint,
            createOrderRequest.orderType
        )
        return orderSubmitter.submit(orderSubmitRequest)
    }

    suspend fun cancelOrder(request: CancelOrderRequest): OrderSubmitResult {
        val symbols = request.symbol.split("_")
        val event = CancelOrderEvent().apply {
            ouid = request.ouid
            uuid = request.uuid
            orderId = request.orderId
            pair = Pair(symbols[0], symbols[1])
        }
        return eventSubmitter.submit(event)
    }
}