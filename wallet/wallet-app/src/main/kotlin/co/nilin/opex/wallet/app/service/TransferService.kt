package co.nilin.opex.wallet.app.service

import co.nilin.opex.utility.error.data.OpexError
import co.nilin.opex.utility.error.data.OpexException
import co.nilin.opex.utility.preferences.Preferences
import co.nilin.opex.wallet.app.dto.AdvanceReservedTransferData
import co.nilin.opex.wallet.app.dto.ManualTransferRequest
import co.nilin.opex.wallet.app.dto.TransferRequest
import co.nilin.opex.wallet.core.exc.NotEnoughBalanceException
import co.nilin.opex.wallet.core.inout.TransferCommand
import co.nilin.opex.wallet.core.inout.TransferResult
import co.nilin.opex.wallet.core.model.Amount
import co.nilin.opex.wallet.core.model.otc.Rate
import co.nilin.opex.wallet.core.spi.CurrencyService
import co.nilin.opex.wallet.core.spi.TransferManager
import co.nilin.opex.wallet.core.spi.WalletManager
import co.nilin.opex.wallet.core.spi.WalletOwnerManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*


@Service
class TransferService(
    private val transferManager: TransferManager,
    private val currencyService: CurrencyService,
    private val walletManager: WalletManager,
    private val walletOwnerManager: WalletOwnerManager,
    private val currencyGraph: co.nilin.opex.wallet.app.service.otc.GraphService
) {
    @Autowired
    private lateinit var preferences: Preferences

    private val logger = LoggerFactory.getLogger(TransferService::class.java)

    val reserved: MutableMap<String, AdvanceReservedTransferData> = mutableMapOf()

    @Transactional
    suspend fun transfer(
            symbol: String,
            senderWalletType: String,
            senderUuid: String,
            receiverWalletType: String,
            receiverUuid: String,
            amount: BigDecimal,
            description: String?,
            transferRef: String?,
            transferCategory: String? = "NO_CATEGORY",
            additionalData: Map<String, Any>? = emptyMap()
    ): TransferResult {
        return _transfer(
            symbol,
            senderWalletType,
            senderUuid,
            receiverWalletType,
            receiverUuid,
            amount,
            description,
            transferRef,
            transferCategory,
            additionalData
        )
    }

    @Transactional
    suspend fun batchTransfer(request: List<TransferRequest>) {
        request.filter { it.receiverWalletType != "cashout" && it.senderWalletType != "cashout" }
            .forEach {
                _transfer(
                    it.symbol,
                    it.senderWalletType,
                    it.senderUuid,
                    it.receiverWalletType,
                    it.receiverUuid,
                    it.amount,
                    it.description,
                    it.transferRef,
                    it.transferCategory,
                    it.additionalData,
                    it.symbol,
                    it.amount
                )
            }
    }

    @Transactional
    suspend fun deposit(
            symbol: String,
            receiverUuid: String,
            receiverWalletType: String,
            amount: BigDecimal,
            description: String?,
            transferRef: String?
    ): TransferResult {
        return _transfer(
            symbol,
            "main",
            walletOwnerManager.systemUuid,
            receiverWalletType,
            receiverUuid,
            amount,
            description,
            transferRef,
            "DEPOSIT",
            null,
            symbol,
            amount
        )
    }

    suspend fun calculateDestinationAmount(
            symbol: String,
            amount: BigDecimal,
            destSymbol: String,
    ): BigDecimal {
        val rate = currencyGraph.buildRoutes(symbol, destSymbol)
            .map { route -> Rate(route.getSourceSymbol(), route.getDestSymbol(), route.getRate()) }
            .firstOrNull() ?: throw OpexException(OpexError.NOT_EXCHANGEABLE_CURRENCIES)
        return amount.multiply(rate.rate)
    }

    suspend fun reserveTransfer(
            sourceAmount: BigDecimal,
            sourceSymbol: String,
            destSymbol: String,
            senderUuid: String,
            senderWalletType: String,
            receiverUuid: String,
            receiverWalletType: String
    ): Pair<String, BigDecimal> {
        val rate = currencyGraph.buildRoutes(sourceSymbol, destSymbol)
            .map { route -> Rate(route.getSourceSymbol(), route.getDestSymbol(), route.getRate()) }
            .firstOrNull() ?: throw OpexException(OpexError.NOT_EXCHANGEABLE_CURRENCIES)
        val finalAmount = sourceAmount.multiply(rate.rate)
        checkIfSystemHasEnoughBalance(destSymbol, receiverWalletType, finalAmount)
        val reserveNumber = UUID.randomUUID().toString()
        reserved.put(
            reserveNumber, AdvanceReservedTransferData(
                sourceSymbol,
                destSymbol,
                senderWalletType,
                senderUuid,
                receiverWalletType,
                receiverUuid,
                sourceAmount,
                finalAmount,
                Date()
            )
        )
        return Pair(reserveNumber, finalAmount)
    }

    @Transactional
    suspend fun advanceTransfer(
        reserveNumber: String,
        description: String?,
        transferRef: String?,
        //todo need to review
        transferCategory: String? = "PURCHASE_FINALIZED",
        additionalData: Map<String, Any>? = emptyMap()
    ): TransferResult {
        val reservations = reserved.get(reserveNumber) ?: throw Exception()
        val calendar = Calendar.getInstance()
        calendar.time = reservations.reserveTime
        calendar.add(Calendar.MINUTE, -5)
        if (reservations.reserveTime.before(calendar.time))
            throw Exception("Expired Reservation")

        val senderTransfer = _transfer(
            reservations.sourceSymbol,
            reservations.senderWalletType,
            reservations.senderUuid,
            "main",
            walletOwnerManager.systemUuid,
            reservations.sourceAmount,
            description,
            "$transferRef-withdraw",
            transferCategory, additionalData,
            reservations.sourceSymbol,
            reservations.sourceAmount
        )

        val receiverTransfer = _transfer(
            reservations.destSymbol,
            "main",
            walletOwnerManager.systemUuid,
            reservations.receiverWalletType,
            reservations.receiverUuid,
            reservations.reservedDestAmount,
            description,
            "$transferRef-deposit",
            transferCategory, additionalData,
            reservations.destSymbol,
            reservations.reservedDestAmount
        )

        return TransferResult(
            senderTransfer.date, senderTransfer.sourceUuid, senderTransfer.sourceWalletType, senderTransfer.sourceBalanceBeforeAction, senderTransfer.sourceBalanceAfterAction,
            senderTransfer.amount, receiverTransfer.destUuid, receiverTransfer.destWalletType, receiverTransfer.receivedAmount
        )
    }

    @Transactional
    suspend fun depositManually(
            symbol: String,
            receiverUuid: String,
            senderUuid:String,
            amount: BigDecimal,
            request: ManualTransferRequest
    ): TransferResult {
        logger.info("deposit manually: $senderUuid to $receiverUuid on $symbol at ${LocalDateTime.now()}")
        val systemUuid = "1"
        //todo customize error message
        val senderLevel=walletOwnerManager.findWalletOwner(senderUuid)?.let {it.level }?:throw OpexException(OpexError.WalletOwnerNotFound)
        val receiverLevel=walletOwnerManager.findWalletOwner(receiverUuid)?.let {it.level }?:throw OpexException(OpexError.WalletOwnerNotFound)

        if ( senderLevel !in arrayListOf<String>(preferences.admin.walletLevel,preferences.system.walletLevel))
            throw OpexException(OpexError.Forbidden)

        if(senderLevel == preferences.system.walletLevel && receiverLevel !=preferences.admin.walletLevel)
            throw OpexException(OpexError.Forbidden)

//        if (walletOwnerManager.findWalletOwner(receiverUuid)?.level !in arrayListOf<String>(preferences.admin.walletLevel,preferences.system.walletLevel))
//            throw OpexException(OpexError.Error)
        return _transfer(
                symbol,
                "main",
                senderUuid,
                "main",
                receiverUuid,
                amount,
                request.description,
                request.ref,
                "DEPOSIT_MANUALLY",
                null,
                symbol,
                amount

        )
    }


    suspend fun _transfer(
            symbol: String,
            senderWalletType: String,
            senderUuid: String,
            receiverWalletType: String,
            receiverUuid: String,
            amount: BigDecimal,
            description: String?,
            transferRef: String?,
            transferCategory: String? = "NO_CATEGORY",
            additionalData: Map<String, Any>? = emptyMap(),
            destSymbol: String = symbol,
            destAmount: BigDecimal = amount

    ): TransferResult {
        if (senderWalletType == "cashout" || receiverWalletType == "cashout")
            throw OpexException(OpexError.InvalidCashOutUsage)
        val sourceCurrency = currencyService.getCurrency(symbol) ?: throw OpexException(OpexError.CurrencyNotFound)
        val sourceOwner = walletOwnerManager.findWalletOwner(senderUuid)
            ?: throw OpexException(OpexError.WalletOwnerNotFound)
        val sourceWallet =
            walletManager.findWalletByOwnerAndCurrencyAndType(sourceOwner, senderWalletType, sourceCurrency)
                ?: throw OpexException(OpexError.WalletNotFound)

        val receiverOwner = walletOwnerManager.findWalletOwner(receiverUuid) ?: walletOwnerManager.createWalletOwner(
                receiverUuid,
                "not set",
                ""
        )
        val receiverCurrency = currencyService.getCurrency(destSymbol)
            ?: throw OpexException(OpexError.CurrencyNotFound)
        val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(
            receiverOwner, receiverWalletType, receiverCurrency
        ) ?: walletManager.createWallet(
            receiverOwner,
            Amount(receiverCurrency, BigDecimal.ZERO),
            receiverCurrency,
            receiverWalletType
        )

        logger.info(
            "Transferring funds: $amount ${sourceWallet.owner.id}-${sourceWallet.currency.symbol}-$senderWalletType " +
                    "==> ${receiverWallet.owner.id}-${receiverWallet.currency.symbol}-$receiverWalletType "
        )

        return transferManager.transfer(
            TransferCommand(
                sourceWallet,
                receiverWallet,
                Amount(sourceWallet.currency, amount),
                description, transferRef, transferCategory!!, additionalData,
                Amount(receiverWallet.currency, destAmount)
            )
        ).transferResult
    }

    private suspend fun checkIfSystemHasEnoughBalance(destSymbol: String, receiverWalletType: String, finalAmount: BigDecimal?) {
        val destCurrency = currencyService.getCurrency(destSymbol)!!
        val system = walletOwnerManager.findWalletOwner(walletOwnerManager.systemUuid) ?: throw OpexException(OpexError.WalletOwnerNotFound)
        val systemWallet = walletManager.findWalletByOwnerAndCurrencyAndType(system, receiverWalletType, destCurrency) ?: throw OpexException(OpexError.WalletNotFound)
        if (systemWallet.balance.amount < finalAmount) {
            throw NotEnoughBalanceException()
        }
    }

}