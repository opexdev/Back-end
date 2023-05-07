package co.nilin.opex.wallet.app.service

import co.nilin.opex.wallet.core.exc.ConcurrentBalanceChangException
import co.nilin.opex.wallet.core.inout.TransferCommand
import co.nilin.opex.wallet.core.model.Amount
import co.nilin.opex.wallet.core.spi.CurrencyService
import co.nilin.opex.wallet.core.spi.TransferManager
import co.nilin.opex.wallet.core.spi.WalletManager
import co.nilin.opex.wallet.core.spi.WalletOwnerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.*

@SpringBootTest
@DirtiesContext
@ActiveProfiles("test")
@Import(TestChannelBinderConfiguration::class)
class TransferManagerImplIT {
    @Autowired
    lateinit var transferManager: TransferManager

    @Autowired
    lateinit var currencyService: CurrencyService

    @Autowired
    lateinit var walletManager: WalletManager

    @Autowired
    lateinit var walletOwnerManager: WalletOwnerManager

    val senderWalletType = "main"
    val receiverWalletType = "exchange"
    val cc = "CC"
    val amount = BigDecimal.valueOf(10)
    var sourceUuid: String? = null

    @BeforeEach
    fun setup() {
        sourceUuid = UUID.randomUUID().toString()
        setupWallets(sourceUuid!!)
    }

    @Test
    fun givenSameSenderWallet_whenConcurrentTransfers_thenSecondTransferFail() {

        val block: () -> Unit = {
            runBlocking {
                val currency = currencyService.getCurrency(cc)!!
                val owner = walletOwnerManager.findWalletOwner(sourceUuid!!)
                val sourceWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner!!, senderWalletType, currency)
                val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner, receiverWalletType, currency)

                launch {
                    transferManager.transfer(
                        TransferCommand(
                            sourceWallet!!,
                            receiverWallet!!,
                            Amount(sourceWallet.currency, amount),
                            "Amount1 ${System.currentTimeMillis()}", "Ref1 ${System.currentTimeMillis()}", emptyMap()
                        )
                    )
                }
                launch {
                    transferManager.transfer(
                        TransferCommand(
                            sourceWallet!!,
                            receiverWallet!!,
                            Amount(sourceWallet.currency, amount),
                            "Amount2 ${System.currentTimeMillis()}", "Ref2 ${System.currentTimeMillis()}", emptyMap()
                        )
                    )
                }
            }
        }
        try {
            block.invoke()
        } catch (_: ConcurrentBalanceChangException) {

        }
        runBlocking {
            val currency = currencyService.getCurrency(cc)!!
            val owner = walletOwnerManager.findWalletOwner(sourceUuid!!)
            val sourceWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner!!, senderWalletType, currency)
            val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner, receiverWalletType, currency)

            assertEquals(amount, sourceWallet!!.balance.amount)
            assertEquals(amount, receiverWallet!!.balance.amount)
        }
    }

    @Test
    fun givenSameReceiverWallet_whenConcurrentTransfers_thenTransfersSuccess() {
        runBlocking {
            val currency = currencyService.getCurrency(cc)!!
            val owner = walletOwnerManager.findWalletOwner(sourceUuid!!)
            val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner!!, receiverWalletType, currency)

            val source2Uuid = UUID.randomUUID().toString()
            setupWallets(source2Uuid)
            val sourceOwner2 = walletOwnerManager.findWalletOwner(source2Uuid)

            val t1 = async {
                val sourceWallet1 = walletManager.findWalletByOwnerAndCurrencyAndType(owner, senderWalletType, currency)
                transferManager.transfer(
                    TransferCommand(
                        sourceWallet1!!,
                        receiverWallet!!,
                        Amount(sourceWallet1.currency, amount),
                        "Amount1 ${System.currentTimeMillis()}", "Ref1 ${System.currentTimeMillis()}", emptyMap()
                    )
                )
            }
            val t2 = async {
                val sourceWallet2 = walletManager.findWalletByOwnerAndCurrencyAndType(sourceOwner2!!, senderWalletType, currency)
                transferManager.transfer(
                    TransferCommand(
                        sourceWallet2!!,
                        receiverWallet!!,
                        Amount(sourceWallet2.currency, amount),
                        "Amount2 ${System.currentTimeMillis()}", "Ref2 ${System.currentTimeMillis()}", emptyMap()
                    )
                )
            }
            t1.await()
            t2.await()

            val sourceWallet1Refresh = walletManager.findWalletByOwnerAndCurrencyAndType(owner, senderWalletType, currency)
            val sourceWallet2Refresh = walletManager.findWalletByOwnerAndCurrencyAndType(sourceOwner2!!, senderWalletType, currency)
            val receiverWalletRefresh = walletManager.findWalletByOwnerAndCurrencyAndType(owner, receiverWalletType, currency)

            assertEquals(amount, sourceWallet1Refresh!!.balance.amount)
            assertEquals(amount, sourceWallet2Refresh!!.balance.amount)
            assertEquals(amount.plus(amount), receiverWalletRefresh!!.balance.amount)
        }


    }

    @Test
    fun givenSameSenderWallet_whenSequentialTransfers_thenTransfersSuccess() {
        runBlocking {
            val currency = currencyService.getCurrency(cc)!!
            val owner = walletOwnerManager.findWalletOwner(sourceUuid!!)

            async {
                val sourceWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner!!, senderWalletType, currency)
                val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner, receiverWalletType, currency)

                transferManager.transfer(
                    TransferCommand(
                        sourceWallet!!,
                        receiverWallet!!,
                        Amount(sourceWallet.currency, amount),
                        "Amount1 ${System.currentTimeMillis()}", "Ref1 ${System.currentTimeMillis()}", emptyMap()
                    )
                )
            }.await()
            async {
                val sourceWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner!!, senderWalletType, currency)
                val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner, receiverWalletType, currency)

                transferManager.transfer(
                    TransferCommand(
                        sourceWallet!!,
                        receiverWallet!!,
                        Amount(sourceWallet!!.currency, amount),
                        "Amount2 ${System.currentTimeMillis()}", "Ref2 ${System.currentTimeMillis()}", emptyMap()
                    )
                )
            }.await()
            val sourceWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner!!, senderWalletType, currency)
            val receiverWallet = walletManager.findWalletByOwnerAndCurrencyAndType(owner, receiverWalletType, currency)

            assertEquals(BigDecimal.ZERO, sourceWallet!!.balance.amount)
            assertEquals(amount.plus(amount), receiverWallet!!.balance.amount)
        }
    }

    fun setupWallets(sourceUuid: String) {
        runBlocking {
            var currency = currencyService.getCurrency(cc)
            if (currency == null) {
                currencyService.deleteCurrency(cc)
                currencyService.addCurrency(cc, cc, BigDecimal.ONE)
                currency = currencyService.getCurrency(cc)
            }
            val sourceOwner = walletOwnerManager.createWalletOwner(sourceUuid, "not set", "")
            walletManager.createWallet(sourceOwner, Amount(currency!!, amount.multiply(BigDecimal.valueOf(2))), currency, senderWalletType)
            walletManager.createWallet(
                sourceOwner,
                Amount(currency, BigDecimal.ZERO),
                currency,
                receiverWalletType
            )
        }
    }
}
