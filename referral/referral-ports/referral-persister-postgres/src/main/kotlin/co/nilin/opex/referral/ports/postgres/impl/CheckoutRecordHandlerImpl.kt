package co.nilin.opex.referral.ports.postgres.impl

import co.nilin.opex.referral.core.model.CheckoutRecord
import co.nilin.opex.referral.core.model.CheckoutState
import co.nilin.opex.referral.core.model.CommissionReward
import co.nilin.opex.referral.core.spi.CheckoutRecordHandler
import co.nilin.opex.referral.ports.postgres.repository.CheckoutRecordRepository
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

@Service
class CheckoutRecordHandlerImpl(private val checkoutRecordRepository: CheckoutRecordRepository) :
    CheckoutRecordHandler {
    override suspend fun findCommissionsByCheckoutState(checkoutState: CheckoutState): List<CheckoutRecord> {
        return checkoutRecordRepository.findByCheckoutStateProjected(checkoutState).map {
            CheckoutRecord(
                CommissionReward(
                    it.commissionRewardId,
                    it.rewardedUuid,
                    it.referentUuid,
                    it.referralCode,
                    Pair(it.richTradeId, null),
                    it.referentOrderDirection,
                    it.share,
                    it.paymentCurrency,
                    it.createDate
                ),
                it.checkoutState,
                it.transferRef,
                it.updateDate
            )
        }.collectList().awaitFirstOrNull() ?: emptyList()
    }

    override suspend fun findUserCommissionsWhereTotalGreaterAndEqualTo(
        uuid: String,
        value: BigDecimal
    ): List<CheckoutRecord> {
        return checkoutRecordRepository.findByUuidWhereTotalShareMoreThanProjected(uuid, value)
            .collectList().awaitSingle().map {
                CheckoutRecord(
                    CommissionReward(
                        it.commissionRewardId,
                        it.rewardedUuid,
                        it.referentUuid,
                        it.referralCode,
                        Pair(it.richTradeId, null),
                        it.referentOrderDirection,
                        it.share,
                        it.paymentCurrency,
                        it.createDate
                    ),
                    it.checkoutState,
                    it.transferRef,
                    it.updateDate
                )
            }
    }

    override suspend fun findAllCommissionsWhereTotalGreaterAndEqualTo(value: BigDecimal): List<CheckoutRecord> {
        return checkoutRecordRepository.findAllWhereTotalShareMoreThanProjected(value)
            .collectList().awaitSingle().map {
                CheckoutRecord(
                    CommissionReward(
                        it.commissionRewardId,
                        it.rewardedUuid,
                        it.referentUuid,
                        it.referralCode,
                        Pair(it.richTradeId, null),
                        it.referentOrderDirection,
                        it.share,
                        it.paymentCurrency,
                        it.createDate
                    ),
                    it.checkoutState,
                    it.transferRef,
                    it.updateDate
                )
            }
    }

    override suspend fun findCommissionsWherePendingDateLessOrEqualThan(date: Date): List<CheckoutRecord> {
        return checkoutRecordRepository.findByCheckoutStateWhereCreateDateLessThanProjected(
            CheckoutState.PENDING,
            date
        ).collectList().awaitSingle().map {
            CheckoutRecord(
                CommissionReward(
                    it.commissionRewardId,
                    it.rewardedUuid,
                    it.referentUuid,
                    it.referralCode,
                    Pair(it.richTradeId, null),
                    it.referentOrderDirection,
                    it.share,
                    it.paymentCurrency,
                    it.createDate
                ),
                it.checkoutState,
                it.transferRef,
                it.updateDate
            )
        }
    }

    override suspend fun updateCheckoutState(id: Long, value: CheckoutState) {
        checkoutRecordRepository.updateCheckoutStateById(id, value)
    }

    override suspend fun checkout(id: Long, transferRef: String) {
        checkoutRecordRepository.checkout(id, transferRef)
    }
}
