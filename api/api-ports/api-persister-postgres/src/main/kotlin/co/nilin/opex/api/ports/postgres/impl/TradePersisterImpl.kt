package co.nilin.opex.api.ports.postgres.impl

import co.nilin.opex.accountant.core.inout.OrderStatus
import co.nilin.opex.accountant.core.inout.RichTrade
import co.nilin.opex.accountant.core.inout.comesBefore
import co.nilin.opex.api.core.spi.TradePersister
import co.nilin.opex.api.ports.postgres.dao.OrderRepository
import co.nilin.opex.api.ports.postgres.dao.TradeRepository
import co.nilin.opex.api.ports.postgres.model.OrderModel
import co.nilin.opex.api.ports.postgres.model.TradeModel
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class TradePersisterImpl(val tradeRepository: TradeRepository, val orderRepository: OrderRepository) : TradePersister {

    @Transactional
    override suspend fun save(trade: RichTrade) {
        println("RichTrade save")
        tradeRepository.save(
            TradeModel(
                null,
                trade.id,
                trade.pair,
                trade.matchedQuantity.toDouble(),
                trade.takerPrice.toDouble(),
                trade.makerPrice.toDouble(),
                trade.takerCommision.toDouble(),
                trade.makerCommision.toDouble(),
                trade.takerCommisionAsset,
                trade.makerCommisionAsset,
                trade.tradeDateTime,
                trade.makerOuid,
                trade.takerOuid,
                trade.makerUuid,
                trade.takerUuid,
                LocalDateTime.now()
            )
        ).awaitFirstOrNull()
        println("RichTrade save/update maker order")
        saveMakerOrder(trade)
        println("RichTrade save/update taker order")
        saveTakerOrder(trade)

    }

    private suspend fun saveTakerOrder(trade: RichTrade) {
        val existingOrder = orderRepository
            .findByOuid(trade.takerOuid)
            .awaitFirstOrNull()

        val executedQuantity = (trade.takerQuantity.minus(trade.takerRemainedQuantity)).toDouble()
        val status = if (trade.takerRemainedQuantity.compareTo(BigDecimal.ZERO) == 0) {
            OrderStatus.FILLED.code
        } else {
            OrderStatus.PARTIALLY_FILLED.code
        }

        if (existingOrder == null || existingOrder.status.comesBefore(status) || (existingOrder.executedQuantity
                ?: 0.0) < executedQuantity
        )
            orderRepository.save(
                OrderModel(
                    existingOrder?.id,
                    trade.takerOuid,
                    trade.takerUuid,
                    null,
                    trade.pair,
                    trade.takerOrderId,
                    existingOrder?.makerFee,
                    existingOrder?.takerFee,
                    existingOrder?.leftSideFraction,
                    existingOrder?.rightSideFraction,
                    existingOrder?.userLevel,
                    trade.takerDirection,
                    existingOrder?.constraint,
                    existingOrder?.type,
                    trade.takerPrice.toDouble(),
                    trade.takerQuantity.toDouble(),
                    trade.takerQuoteQuantity.toDouble(),
                    (trade.takerQuantity.minus(trade.takerRemainedQuantity)).toDouble(),
                    trade.takerPrice.multiply(
                        (trade.takerQuantity.minus(trade.takerRemainedQuantity))
                    ).toDouble(),
                    if (trade.takerRemainedQuantity.compareTo(BigDecimal.ZERO) == 0) {
                        OrderStatus.FILLED.code
                    } else {
                        OrderStatus.PARTIALLY_FILLED.code
                    },
                    existingOrder?.createDate,
                    LocalDateTime.now(),
                    existingOrder?.version
                )
            ).awaitFirstOrNull()
    }

    private suspend fun saveMakerOrder(trade: RichTrade) {
        val existingOrder = orderRepository
            .findByOuid(trade.makerOuid)
            .awaitFirstOrNull()

        val executedQuantity = (trade.makerQuantity.minus(trade.makerRemainedQuantity)).toDouble()
        val status = if (trade.makerRemainedQuantity.compareTo(BigDecimal.ZERO) == 0) {
            OrderStatus.FILLED.code
        } else {
            OrderStatus.PARTIALLY_FILLED.code
        }

        if (existingOrder == null || existingOrder.status.comesBefore(status) || (existingOrder.executedQuantity
                ?: 0.0) < executedQuantity
        )
            orderRepository.save(
                OrderModel(
                    existingOrder?.id,
                    trade.makerOuid,
                    trade.makerUuid,
                    null,
                    trade.pair,
                    trade.makerOrderId,
                    existingOrder?.makerFee,
                    existingOrder?.takerFee,
                    existingOrder?.leftSideFraction,
                    existingOrder?.rightSideFraction,
                    existingOrder?.userLevel,
                    trade.makerDirection,
                    existingOrder?.constraint,
                    existingOrder?.type,
                    trade.makerPrice.toDouble(),
                    trade.makerQuantity.toDouble(),
                    trade.makerQuoteQuantity.toDouble(),
                    (trade.makerQuantity.minus(trade.makerRemainedQuantity)).toDouble(),
                    trade.makerPrice.multiply(
                        (trade.makerQuantity.minus(trade.makerRemainedQuantity))
                    ).toDouble(),
                    if (trade.makerRemainedQuantity.compareTo(BigDecimal.ZERO) == 0) {
                        OrderStatus.FILLED.code
                    } else {
                        OrderStatus.PARTIALLY_FILLED.code
                    },
                    existingOrder?.createDate ?: LocalDateTime.now(),
                    LocalDateTime.now(),
                    existingOrder?.version
                )
            ).awaitFirstOrNull()
    }
}