package co.nilin.opex.accountant.core.model

class PairFeeConfig(
    val pairConfig: PairConfig,
    val direction: String,
    val userLevel: String,
    val makerFee: Double,
    val takerFee: Double
)