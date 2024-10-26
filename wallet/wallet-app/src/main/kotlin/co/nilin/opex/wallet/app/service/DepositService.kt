package co.nilin.opex.wallet.app.service

import co.nilin.opex.common.OpexError
import co.nilin.opex.utility.error.data.OpexException
import co.nilin.opex.wallet.app.dto.ManualTransferRequest
import co.nilin.opex.wallet.core.inout.*
import co.nilin.opex.wallet.core.model.DepositStatus
import co.nilin.opex.wallet.core.model.DepositType
import co.nilin.opex.wallet.core.model.TransferCategory
import co.nilin.opex.wallet.core.model.WalletType
import co.nilin.opex.wallet.core.spi.*
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class DepositService(
    private val walletOwnerManager: WalletOwnerManager,
    private val currencyService: CurrencyServiceV2,
    private val depositPersister: DepositPersister,
    private val transferService: TransferService
) {
    private val logger = LoggerFactory.getLogger(DepositService::class.java)

    @Transactional
    suspend fun depositManually(
        symbol: String,
        receiverUuid: String,
        senderUuid: String,
        amount: BigDecimal,
        request: ManualTransferRequest
    ): TransferResult? {
        logger.info("deposit manually: $senderUuid to $receiverUuid on $symbol at ${LocalDateTime.now()}")
        walletOwnerManager.findWalletOwner(senderUuid)?.let { it.level }
            ?: throw OpexException(OpexError.WalletOwnerNotFound)
        walletOwnerManager.findWalletOwner(receiverUuid)?.let { it.level }
            ?: walletOwnerManager.createWalletOwner(
                receiverUuid,
                "not set",
                "1"
            ).level
        return deposit(
            symbol,
            receiverUuid,
            WalletType.MAIN,
            amount,
            request.description,
            request.ref,
            null,
            request.attachment,
            DepositType.MANUALLY, null
        )
    }


    @Transactional
    suspend fun deposit(
        symbol: String,
        receiverUuid: String,
        receiverWalletType: WalletType,
        amount: BigDecimal,
        description: String?,
        transferRef: String?,
        chain: String?,
        attachment: String? = null,
        depositType: DepositType,
        gatewayUuid: String?
    ): TransferResult? {
        logger.info("A ${depositType.name} deposit tx on $symbol-$chain was received for $receiverUuid .......")
        var status = DepositStatus.DONE
        val gatewayData = _fetchDepositData(gatewayUuid, symbol, depositType)
        if (!(gatewayData.isEnabled && amount < gatewayData.minimum && amount > gatewayData.maximum)) {
            logger.info("An invalid deposit command :$symbol-$chain-$receiverUuid-$amount")
            status = DepositStatus.INVALID

        }

        depositPersister.persist(
            Deposit(
                receiverUuid,
                UUID.randomUUID().toString(),
                symbol,
                amount,
                note = description,
                transactionRef = transferRef,
                status = status,
                depositType = depositType,
                network = chain,
                attachment = attachment
            )
        )
        if (status == DepositStatus.DONE) {
            logger.info("Going to charge wallet on a ${depositType.name} deposit event :$symbol-$chain-$receiverUuid-$amount")
            return transferService.transfer(
                symbol,
                WalletType.MAIN,
                walletOwnerManager.systemUuid,
                receiverWalletType,
                receiverUuid,
                amount,
                description,
                transferRef,
                TransferCategory.DEPOSIT,
            )
        }
        return null
    }


    suspend fun _fetchDepositData(
        gatewayUuid: String?,
        symbol: String,
        depositType: DepositType,
    ): GatewayData {

        when (depositType) {
            DepositType.ON_CHAIN -> {
                currencyService.fetchCurrencyGateway(gatewayUuid!!, symbol)?.let {
                    return GatewayData(
                        it.isActive ?: true && it.depositAllowed ?: true,
                        BigDecimal.ZERO,
                        it.depositMin ?: BigDecimal.ZERO,
                        it.depositMax
                    )
                } ?: throw OpexError.GatewayNotFount.exception()
            }
            else -> return GatewayData(true, BigDecimal.ZERO, BigDecimal.ZERO, null)

        }

    }


    suspend fun findDepositHistory(
        uuid: String,
        symbol: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        limit: Int?,
        size: Int?,
        ascendingByTime: Boolean?
    ): List<DepositResponse> {
        return depositPersister.findDepositHistory(uuid, symbol, startTime, endTime, limit, size, ascendingByTime)
            .map {
                DepositResponse(
                    it.id!!,
                    it.ownerUuid,
                    it.currency,
                    it.amount,
                    it.network,
                    it.note,
                    it.transactionRef,
                    it.sourceAddress,
                    it.status,
                    it.depositType,
                    it.attachment,
                    it.createDate
                )
            }
    }


    suspend fun searchDeposit(
        ownerUuid: String?,
        symbol: String?,
        sourceAddress: String?,
        transactionRef: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        offset: Int?,
        size: Int?,
        ascendingByTime: Boolean?
    ): List<DepositResponse> {

        return depositPersister.findByCriteria(
            ownerUuid,
            symbol,
            sourceAddress,
            transactionRef,
            startTime,
            endTime,
            offset,
            size,
            ascendingByTime
        ).map {
            DepositResponse(
                it.id!!,
                it.ownerUuid,
                it.currency,
                it.amount,
                it.network,
                it.note,
                it.transactionRef,
                it.sourceAddress,
                it.status,
                it.depositType,
                it.attachment,
                it.createDate
            )
        }
    }


}