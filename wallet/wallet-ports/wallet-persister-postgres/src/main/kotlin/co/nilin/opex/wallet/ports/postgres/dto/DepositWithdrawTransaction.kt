package co.nilin.opex.wallet.ports.postgres.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class DepositWithdrawTransaction(
    val id: Long,
    val currency: String,
    val amount: BigDecimal,
    val description: String?,
    val ref: String?,
    val date: LocalDateTime,
    val category: String,
    val detail: String?,
    val sender: Long?,
    val receiver: Long?,
    val owner: Long?
)