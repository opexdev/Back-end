package co.nilin.opex.wallet.app.controller

import co.nilin.opex.utility.error.data.OpexError
import co.nilin.opex.utility.error.data.OpexException
import co.nilin.opex.wallet.core.inout.TransferCommand
import co.nilin.opex.wallet.core.inout.TransferResult
import co.nilin.opex.wallet.core.model.Amount
import co.nilin.opex.wallet.core.service.TransferService
import co.nilin.opex.wallet.core.spi.CurrencyService
import co.nilin.opex.wallet.core.spi.WalletManager
import co.nilin.opex.wallet.core.spi.WalletOwnerManager
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.Example
import io.swagger.annotations.ExampleProperty
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
class TransferController(
    val transferService: TransferService,
    val currencyService: CurrencyService,
    val walletManager: WalletManager,
    val walletOwnerManager: WalletOwnerManager
) {
    @PostMapping("/transfer/{amount}_{symbol}/from/{senderUuid}_{senderWalletType}/to/{receiverUuid}_{receiverWalletType}")
    @ApiResponse(
        message = "OK",
        code = 200,
        examples = Example(
            ExampleProperty(
                value = "{ }",
                mediaType = "application/json"
            )
        )
    )
    suspend fun transfer(
        @PathVariable("symbol") symbol: String,
        @PathVariable("senderWalletType") senderWalletType: String,
        @PathVariable("senderUuid") senderUuid: String,
        @PathVariable("receiverWalletType") receiverWalletType: String,
        @PathVariable("receiverUuid") receiverUuid: String,
        @PathVariable("amount") amount: BigDecimal,
        @PathVariable("description") description: String?,
        @PathVariable("transferRef") transferRef: String?
    ): TransferResult {
        if (senderWalletType == "cashout" || receiverWalletType == "cashout")
            throw OpexException(OpexError.InvalidCashOutUsage)
        val currency = currencyService.getCurrency(symbol) ?: throw OpexException(OpexError.CurrencyNotFound)
        val sourceOwner = walletOwnerManager.findWalletOwner(senderUuid)
            ?: throw OpexException(OpexError.WalletOwnerNotFound)
        val sourceWallet = walletManager.findWalletByOwnerAndCurrencyAndType(sourceOwner, senderWalletType, currency)
            ?: throw OpexException(OpexError.WalletNotFound)

        val receiverOwner = walletOwnerManager.findWalletOwner(receiverUuid) ?: walletOwnerManager.createWalletOwner(
            senderUuid,
            "not set",
            ""
        )
        val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(
            receiverOwner, receiverWalletType, currency
        ) ?: walletManager.createWallet(
            receiverOwner,
            Amount(currency, BigDecimal.ZERO),
            currency,
            receiverWalletType
        )
        return transferService.transfer(
            TransferCommand(
                sourceWallet,
                receiverWallet,
                Amount(sourceWallet.currency(), amount),
                description, transferRef, emptyMap()
            )
        ).transferResult
    }

    @PostMapping("/deposit/{amount}_{symbol}/{receiverUuid}_{receiverWalletType}")
    @ApiResponse(
        message = "OK",
        code = 200,
        examples = Example(
            ExampleProperty(
                value = "{ }",
                mediaType = "application/json"
            )
        )
    )
    suspend fun deposit(
        @PathVariable("symbol") symbol: String,
        @PathVariable("receiverUuid") receiverUuid: String,
        @PathVariable("receiverWalletType") receiverWalletType: String,
        @PathVariable("amount") amount: BigDecimal,
        @RequestParam("description") description: String?,
        @RequestParam("transferRef") transferRef: String?
    ): TransferResult {
        if (receiverWalletType == "cashout") throw OpexException(OpexError.InvalidCashOutUsage)
        val systemUuid = "1"
        val currency = currencyService.getCurrency(symbol) ?: throw OpexException(OpexError.CurrencyNotFound)
        val sourceOwner = walletOwnerManager.findWalletOwner(systemUuid)
            ?: throw OpexException(OpexError.WalletOwnerNotFound)
        val sourceWallet = walletManager.findWalletByOwnerAndCurrencyAndType(sourceOwner, "main", currency)
            ?: throw OpexException(OpexError.WalletNotFound)

        val receiverOwner = walletOwnerManager.findWalletOwner(receiverUuid) ?: walletOwnerManager.createWalletOwner(
            systemUuid,
            "not set",
            ""
        )
        val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(
            receiverOwner, receiverWalletType, currency
        ) ?: walletManager.createWallet(
            receiverOwner,
            Amount(currency, BigDecimal.ZERO),
            currency,
            receiverWalletType
        )
        return transferService.transfer(
            TransferCommand(
                sourceWallet,
                receiverWallet,
                Amount(sourceWallet.currency(), amount),
                description, transferRef, emptyMap()
            )
        ).transferResult
    }
}
