package co.nilin.opex.wallet.app.dto

data class OwnerLimitsResponse(
    val canTrade: Boolean,
    val canWithdraw: Boolean,
    val canDeposit: Boolean
)
