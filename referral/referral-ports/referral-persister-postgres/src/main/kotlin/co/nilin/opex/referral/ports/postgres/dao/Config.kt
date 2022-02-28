package co.nilin.opex.referral.ports.postgres.dao

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal

@Table("configs")
data class Config(
    @Id var name: String,
    var referralCommissionReward: BigDecimal,
    var paymentCurrency: String,
    var minPaymentAmount: BigDecimal,
    var paymentWindowSeconds: Int,
    var maxReferralCodePerUser: Int
)
