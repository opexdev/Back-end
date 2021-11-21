package co.nilin.opex.accountant.ports.postgres.impl

import co.nilin.opex.accountant.core.model.Order
import co.nilin.opex.accountant.core.spi.OrderPersister
import co.nilin.opex.accountant.ports.postgres.dao.OrderRepository
import co.nilin.opex.accountant.ports.postgres.model.OrderModel
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderPersisterImpl(val orderRepository: OrderRepository) : OrderPersister {

    override suspend fun load(ouid: String): Order? {
        val model = orderRepository.findByOuid(ouid).awaitFirstOrNull() ?: return null
        return Order(
            model.pair,
            model.ouid,
            model.matchingEngineId,
            model.makerFee,
            model.takerFee,
            model.leftSideFraction,
            model.rightSideFraction,
            model.uuid,
            model.userLevel,
            model.direction,
            model.matchConstraint,
            model.orderType,
            model.price,
            model.quantity,
            model.filledQuantity,
            model.origPrice,
            model.origQuantity,
            model.filledOrigQuantity,
            model.firstTransferAmount,
            model.remainedTransferAmount,
            model.status,
            model.id
        )
    }

    override suspend fun save(order: Order): Order {
        orderRepository.save(
            OrderModel(
                order.id,
                order.ouid,
                order.uuid,
                order.pair,
                order.matchingEngineId,
                order.makerFee,
                order.takerFee,
                order.leftSideFraction,
                order.rightSideFraction,
                order.userLevel,
                order.direction,
                order.matchConstraint,
                order.orderType,
                order.price,
                order.quantity,
                order.filledQuantity,
                order.origPrice,
                order.origQuantity,
                order.filledOrigQuantity,
                order.firstTransferAmount,
                order.remainedTransferAmount,
                order.status,
                "",
                "",
                LocalDateTime.now()
            )
        ).awaitFirstOrNull()
        return order
    }
}