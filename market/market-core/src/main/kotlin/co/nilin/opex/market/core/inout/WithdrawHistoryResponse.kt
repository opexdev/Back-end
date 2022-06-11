package co.nilin.opex.market.core.inout

import java.math.BigDecimal

data class WithdrawHistoryResponse(
    val withdrawId: Long?,
    val uuid: String,
    val amount: BigDecimal,
    val acceptedFee: BigDecimal,
    val appliedFee: BigDecimal?,
    val destAmount: BigDecimal?,
    val destCurrency: String?,
    val destAddress: String?,
    val destNetwork: String?,
    var destNote: String?,
    var destTransactionRef: String?,
    val statusReason: String?,
    val status: String,
    val createDate: Long,
    val acceptDate: Long?
)
