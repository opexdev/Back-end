package co.nilin.opex.wallet.app

import co.nilin.opex.wallet.core.model.Amount
import co.nilin.opex.wallet.core.model.Currency
import co.nilin.opex.wallet.core.model.Wallet
import co.nilin.opex.wallet.core.model.WalletOwner
import co.nilin.opex.wallet.ports.postgres.dao.*
import co.nilin.opex.wallet.ports.postgres.impl.WalletManagerImpl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import java.math.BigDecimal
import org.assertj.core.api.Assertions.*

private class WalletManagerTest {
    @Mock
    private var walletLimitsRepository: WalletLimitsRepository

    @Mock
    private var transactionRepository: TransactionRepository

    @Mock
    private lateinit var walletRepository: WalletRepository

    @Mock
    private lateinit var walletOwnerRepository: WalletOwnerRepository

    @Mock
    private lateinit var currencyRepository: CurrencyRepository

    private var walletManagerImpl: WalletManagerImpl

    private val walletOwner = object : WalletOwner {
        override fun id() = 2L
        override fun uuid() = "fdf453d7-0633-4ec7-852d-a18148c99a82"
        override fun title() = "wallet"
        override fun level() = "1"
        override fun isTradeAllowed() = true
        override fun isWithdrawAllowed() = true
        override fun isDepositAllowed() = true
    }

    private val currency = object : Currency {
        override fun getSymbol() = "ETH"
        override fun getName() = "Ethereum"
        override fun getPrecision() = 0.0001
    }

    init {
        MockitoAnnotations.openMocks(this)
        walletLimitsRepository = mock {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(anyLong(), anyString(), anyLong(), anyString())
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(anyLong(), anyString(), anyString(), anyString())
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType(anyString(), anyString(), anyString(), anyString())
            } doReturn Mono.empty()
        }
        transactionRepository = mock {
            on { calculateWithdrawStatistics(anyLong(), anyLong(), any(), any()) } doReturn Mono.empty()
        }
        walletManagerImpl = WalletManagerImpl(
            walletLimitsRepository, transactionRepository, walletRepository, walletOwnerRepository, currencyRepository
        )
    }

    @Test
    fun givenFullWalletWithNoLimit_whenIsWithdrawAllowed_thenReturnTrue(): Unit = runBlocking {
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0.5))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isWithdrawAllowed(wallet, BigDecimal.valueOf(0.5))

        assertThat(isAllowed).isTrue()
    }

    @Test
    fun givenEmptyWalletWithNoLimit_whenIsWithdrawAllowed_thenReturnFalse(): Unit = runBlocking {
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isWithdrawAllowed(wallet, BigDecimal.valueOf(0.5))

        assertThat(isAllowed).isFalse()
    }
}