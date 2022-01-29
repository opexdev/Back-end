package co.nilin.opex.accountant.core.inout

import co.nilin.opex.matching.engine.core.model.MatchConstraint
import co.nilin.opex.matching.engine.core.model.OrderDirection
import co.nilin.opex.matching.engine.core.model.OrderType
import java.math.BigDecimal

data class RichOrder(
    val orderId: Long? = 0,
    val pair: String,
    val ouid: String,
    val uuid: String,
    val userLevel: String,
    val makerFee: BigDecimal,
    val takerFee: BigDecimal,
    val leftSideFraction: BigDecimal,
    val rightSideFraction: BigDecimal,
    val direction: OrderDirection,
    val constraint: MatchConstraint,
    val type: OrderType,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val quoteQuantity: BigDecimal,
    val executedQuantity: BigDecimal,
    val accumulativeQuoteQty: BigDecimal,
    val status: Int = 0
) : RichOrderEvent
