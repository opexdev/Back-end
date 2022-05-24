package co.nilin.opex.wallet.ports.postgres.impl

import co.nilin.opex.wallet.core.model.Amount
import co.nilin.opex.wallet.core.model.Currency
import co.nilin.opex.wallet.core.model.Wallet
import co.nilin.opex.wallet.core.model.WalletOwner
import co.nilin.opex.wallet.ports.postgres.dao.*
import co.nilin.opex.wallet.ports.postgres.model.CurrencyModel
import co.nilin.opex.wallet.ports.postgres.model.WalletLimitsModel
import co.nilin.opex.wallet.ports.postgres.model.WalletModel
import co.nilin.opex.wallet.ports.postgres.model.WalletOwnerModel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import java.math.BigDecimal

private class WalletManagerTest {
    private val walletLimitsRepository: WalletLimitsRepository = mock()
    private val walletRepository: WalletRepository = mock()
    private val walletOwnerRepository: WalletOwnerRepository = mock()
    private val currencyRepository: CurrencyRepository = mock { }

    private var transactionRepository: TransactionRepository = mock {
        on { calculateWithdrawStatistics(eq(2), eq(20), any(), any()) } doReturn Mono.empty()
    }

    private val walletManagerImpl: WalletManagerImpl = WalletManagerImpl(
        walletLimitsRepository, transactionRepository, walletRepository, walletOwnerRepository, currencyRepository
    )

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

    @Test
    fun givenWalletWithNoLimit_whenIsWithdrawAllowed_thenReturnTrue(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(walletOwner.id(), "ETH", 20, "withdraw")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(walletOwner.id(), "ETH", "withdraw", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.empty()
        }
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
    fun givenNoWallet_whenIsWithdrawAllowed_thenThrow(): Unit = runBlocking {
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(anyLong(), "ETH", anyLong(), "withdraw")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(anyLong(), "ETH", "withdraw", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.empty()
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.isWithdrawAllowed(
                    wallet,
                    BigDecimal.valueOf(0.5)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun giNoCurrency_whenIsWithdrawAllowed_thenThrow(): Unit = runBlocking {
        stubbing(currencyRepository) {
            on { findBySymbol(currency.getSymbol()) } doReturn Mono.empty()
            on { findById(currency.getSymbol()) } doReturn Mono.empty()
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.isWithdrawAllowed(
                    wallet,
                    BigDecimal.valueOf(0.5)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenEmptyWallet_whenIsWithdrawAllowed_thenReturnFalse(): Unit = runBlocking {
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

    @Test
    fun givenWrongAmount_whenIsWithdrawAllowed_thenThrow(): Unit = runBlocking {
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.isWithdrawAllowed(
                    wallet,
                    BigDecimal.valueOf(-1)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenOwnerAndWalletTypeLimit_whenIsWithdrawAllowed_thenReturnTrue(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(2, "ETH", 30, "withdraw")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(2, "ETH", "withdraw", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.empty()
        }
        val wallet = object : Wallet {
            override fun id() = 30L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(5))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isWithdrawAllowed(wallet, BigDecimal.valueOf(1))

        assertThat(isAllowed).isTrue()
    }

    @Test
    fun givenOwnerAndWalletLimit_whenIsWithdrawAllowed_thenReturnTrue(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(2, "ETH", 30, "withdraw")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(2, "ETH", "withdraw", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.empty()
        }
        val wallet = object : Wallet {
            override fun id() = 30L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(5))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isWithdrawAllowed(wallet, BigDecimal.valueOf(1))

        assertThat(isAllowed).isTrue()
    }

    @Test
    fun givenLevelAndWalletTypeLimit_whenIsWithdrawAllowed_thenReturnTrue(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(2, "ETH", 30, "withdraw")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(2, "ETH", "withdraw", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    "1",
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
        }
        val wallet = object : Wallet {
            override fun id() = 30L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(5))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isWithdrawAllowed(wallet, BigDecimal.valueOf(1))

        assertThat(isAllowed).isTrue()
    }

    @Test
    fun givenAllLimits_whenIsWithdrawAllowedWithValidAmount_thenReturnTrue(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(2, "ETH", 30, "withdraw")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(2, "ETH", "withdraw", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    "1",
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
        }
        val wallet = object : Wallet {
            override fun id() = 30L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(5))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isWithdrawAllowed(wallet, BigDecimal.valueOf(1))

        assertThat(isAllowed).isTrue()
    }

    @Test
    fun givenAllLimits_whenIsWithdrawAllowedWithInvalidAmount_thenReturnFalse(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(2, "ETH", 30, "withdraw")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(2, "ETH", "withdraw", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    "1",
                    2,
                    "withdraw",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
        }
        val wallet = object : Wallet {
            override fun id() = 30L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(500))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isWithdrawAllowed(wallet, BigDecimal.valueOf(30))

        assertThat(isAllowed).isFalse()
    }

    @Test
    fun givenEmptyWalletWithNoLimit_whenIsWithdrawAllowed_thenReturnFalse(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(walletOwner.id(), "ETH", 20, "withdraw")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(walletOwner.id(), "ETH", "withdraw", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.empty()
        }
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

    @Test
    fun givenWalletWithNoLimit_whenIsDepositAllowed_thenReturnTrue(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(walletOwner.id(), "ETH", 20, "deposit")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(walletOwner.id(), "ETH", "deposit", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "deposit", "main")
            } doReturn Mono.empty()
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0.5))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isDepositAllowed(wallet, BigDecimal.valueOf(0.5))

        assertThat(isAllowed).isTrue()
    }

    @Test
    fun givenNotExistWallet_whenIsDepositAllowed_thenThrow(): Unit = runBlocking {
        val wallet = object : Wallet {
            override fun id() = 40L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.isDepositAllowed(
                    wallet,
                    BigDecimal.valueOf(0.5)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenNoCurrency_whenIsDepositAllowed_thenThrow(): Unit = runBlocking {
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }
        stubbing(currencyRepository) {
            on { findBySymbol(anyString()) } doReturn Mono.empty()
            on { findById(anyString()) } doReturn Mono.empty()
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.isDepositAllowed(
                    wallet,
                    BigDecimal.valueOf(0.5)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenEmptyWallet_whenIsDepositAllowed_thenFalse(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(walletOwner.id(), "ETH", 20, "withdraw")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(walletOwner.id(), "ETH", "withdraw", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "withdraw", "main")
            } doReturn Mono.empty()
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = runBlocking { walletManagerImpl.isDepositAllowed(wallet, BigDecimal.valueOf(0.5)) }

        verify(walletLimitsRepository, never()).findByOwnerAndCurrencyAndWalletAndAction(
            walletOwner.id(),
            "ETH",
            20,
            "withdraw"
        )
        verify(walletLimitsRepository, never()).findByOwnerAndCurrencyAndActionAndWalletType(
            walletOwner.id(),
            "ETH",
            "withdraw",
            "main"
        )
        verify(walletLimitsRepository, never()).findByLevelAndCurrencyAndActionAndWalletType(
            "1",
            "ETH",
            "withdraw",
            "main"
        )
        assertThat(isAllowed).isFalse()
    }

    @Test
    fun givenWrongAmount_whenIsDepositAllowed_thenThrow(): Unit = runBlocking {
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy { runBlocking { walletManagerImpl.isDepositAllowed(wallet, BigDecimal.valueOf(-1)) } }
    }

    @Test
    fun givenAllLimits_whenIsDepositAllowedWithValidAmount_thenReturnTrue(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(2, "ETH", 30, "deposit")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "deposit",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(2, "ETH", "deposit", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "deposit",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "deposit", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    "1",
                    2,
                    "deposit",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
        }
        val wallet = object : Wallet {
            override fun id() = 30L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(5))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isDepositAllowed(wallet, BigDecimal.valueOf(1))

        assertThat(isAllowed).isTrue()
    }

    @Test
    fun givenWalletWithWalletLimit_whenIsDepositAllowed_thenReturnFalse(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(2, "ETH", 30, "deposit")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "deposit",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(2, "ETH", "deposit", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    null,
                    2,
                    "deposit",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "deposit", "main")
            } doReturn Mono.just(
                WalletLimitsModel(
                    1,
                    "1",
                    2,
                    "deposit",
                    "ETH",
                    "main",
                    30,
                    BigDecimal.valueOf(100),
                    10,
                    BigDecimal.valueOf(3000),
                    300
                )
            )
        }
        val wallet = object : Wallet {
            override fun id() = 30L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(500))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isDepositAllowed(wallet, BigDecimal.valueOf(30))

        assertThat(isAllowed).isFalse()
    }

    @Test
    fun givenEmptyWalletWithNoLimit_whenIsDepositAllowed_thenReturnFalse(): Unit = runBlocking {
        stubbing(walletLimitsRepository) {
            on {
                findByOwnerAndCurrencyAndWalletAndAction(walletOwner.id(), "ETH", 20, "deposit")
            } doReturn Mono.empty()
            on {
                findByOwnerAndCurrencyAndActionAndWalletType(walletOwner.id(), "ETH", "deposit", "main")
            } doReturn Mono.empty()
            on {
                findByLevelAndCurrencyAndActionAndWalletType("1", "ETH", "deposit", "main")
            } doReturn Mono.empty()
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(0))
            override fun currency() = currency
            override fun type() = "main"
        }

        val isAllowed = walletManagerImpl.isDepositAllowed(wallet, BigDecimal.valueOf(0.5))

        assertThat(isAllowed).isFalse()
    }

    @Test
    fun givenWallet_whenFindWalletByOwnerAndCurrencyAndType_thenReturnWallet(): Unit = runBlocking {
        stubbing(walletOwnerRepository) {
            on { findById(walletOwner.id()) } doReturn Mono.just(
                WalletOwnerModel(
                    walletOwner.id(),
                    walletOwner.uuid(),
                    walletOwner.title(),
                    walletOwner.level(),
                    walletOwner.isTradeAllowed(),
                    walletOwner.isWithdrawAllowed(),
                    walletOwner.isDepositAllowed()
                )
            )
        }
        stubbing(walletRepository) {
            on {
                findByOwnerAndTypeAndCurrency(walletOwner.id(), "main", currency.getSymbol())
            } doReturn Mono.just(
                WalletModel(
                    20L,
                    walletOwner.id(),
                    "main",
                    currency.getSymbol(),
                    BigDecimal.valueOf(1.2)
                )
            )
        }
        stubbing(currencyRepository) {
            on {
                findBySymbol(currency.getSymbol())
            } doReturn Mono.just(
                CurrencyModel(
                    currency.getSymbol(),
                    currency.getName(),
                    currency.getPrecision()
                )
            )
        }

        val wallet = walletManagerImpl.findWalletByOwnerAndCurrencyAndType(walletOwner, "main", currency)

        assertThat(wallet).isNotNull
        assertThat(wallet!!.owner().id()).isEqualTo(walletOwner.id())
        assertThat(wallet.currency().getSymbol()).isEqualTo(currency.getSymbol())
        assertThat(wallet.type()).isEqualTo("main")
    }

    @Test
    fun givenEmptyWalletWithNoLimit_whenCreateWallet_thenReturnWallet(): Unit = runBlocking {
        stubbing(walletRepository) {
            on {
                save(WalletModel(null, walletOwner.id(), "main", currency.getSymbol(), BigDecimal.valueOf(1)))
            } doReturn Mono.just(
                WalletModel(
                    20L,
                    walletOwner.id(),
                    "main",
                    currency.getSymbol(),
                    BigDecimal.valueOf(1)
                )
            )
        }

        val wallet = walletManagerImpl.createWallet(
            walletOwner,
            Amount(currency, BigDecimal.valueOf(1)),
            currency,
            "main"
        )

        assertThat(wallet).isNotNull
        assertThat(wallet.owner().id()).isEqualTo(walletOwner.id())
        assertThat(wallet.currency().getSymbol()).isEqualTo(currency.getSymbol())
        assertThat(wallet.type()).isEqualTo("main")
    }

    @Test
    fun givenWallet_whenIncreaseBalance_thenSuccess(): Unit = runBlocking {
        stubbing(walletRepository) {
            on {
                updateBalance(eq(20), any())
            } doReturn Mono.just(1)
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(2))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.increaseBalance(
                    wallet,
                    BigDecimal.valueOf(1)
                )
            }
        }.doesNotThrowAnyException()
    }

    @Test
    fun givenNoWallet_whenIncreaseBalance_thenThrow(): Unit = runBlocking {
        stubbing(walletRepository) {
            on {
                updateBalance(any(), eq(BigDecimal.valueOf(1)))
            } doReturn Mono.just(0)
        }
        val wallet = object : Wallet {
            override fun id() = 40L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(2))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.increaseBalance(
                    wallet,
                    BigDecimal.valueOf(1)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenWrongAmount_whenIncreaseBalance_thenThrow(): Unit = runBlocking {
        stubbing(walletRepository) {
            on {
                updateBalance(eq(20), any())
            } doReturn Mono.just(0)
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(2))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.increaseBalance(
                    wallet,
                    BigDecimal.valueOf(-1)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenWallet_whenDecreaseBalance_thenSuccess(): Unit = runBlocking {
        stubbing(walletRepository) {
            on { updateBalance(eq(20), eq(BigDecimal.valueOf(-1))) } doReturn Mono.just(1)
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(2))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.decreaseBalance(
                    wallet,
                    BigDecimal.valueOf(1)
                )
            }
        }.doesNotThrowAnyException()
    }

    @Test
    fun givenNoWallet_whenDecreaseBalance_thenThrow(): Unit = runBlocking {
        stubbing(walletRepository) {
            on {
                updateBalance(any(), eq(BigDecimal.valueOf(-1)))
            } doReturn Mono.just(0)
        }
        val wallet = object : Wallet {
            override fun id() = 40L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(2))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.decreaseBalance(
                    wallet,
                    BigDecimal.valueOf(1)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenWrongAmount_whenDecreaseBalance_thenThrow(): Unit = runBlocking {
        stubbing(walletRepository) {
            on {
                updateBalance(eq(20), eq(BigDecimal.valueOf(-1)))
            } doReturn Mono.just(0)
        }
        val wallet = object : Wallet {
            override fun id() = 20L
            override fun owner() = walletOwner
            override fun balance() = Amount(currency, BigDecimal.valueOf(2))
            override fun currency() = currency
            override fun type() = "main"
        }

        assertThatThrownBy {
            runBlocking {
                walletManagerImpl.decreaseBalance(
                    wallet,
                    BigDecimal.valueOf(-1)
                )
            }
        }.isNotInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun givenWallet_whenFindWalletById_thenReturnWallet(): Unit = runBlocking {
        stubbing(walletRepository) {
            on { findById(20) } doReturn Mono.just(
                WalletModel(
                    20L,
                    walletOwner.id(),
                    "main",
                    currency.getSymbol(),
                    BigDecimal.valueOf(0.5)
                )
            )
        }
        stubbing(walletOwnerRepository) {
            on {
                findById(walletOwner.id())
            } doReturn Mono.just(
                WalletOwnerModel(
                    walletOwner.id(),
                    walletOwner.uuid(),
                    walletOwner.title(),
                    walletOwner.level(),
                    walletOwner.isTradeAllowed(),
                    walletOwner.isWithdrawAllowed(),
                    walletOwner.isDepositAllowed()
                )
            )
        }
        stubbing(currencyRepository) {
            on {
                findById(currency.getSymbol())
            } doReturn Mono.just(
                CurrencyModel(
                    currency.getSymbol(),
                    currency.getName(),
                    currency.getPrecision()
                )
            )
        }
        val wallet = walletManagerImpl.findWalletById(20)

        assertThat(wallet).isNotNull
        assertThat(wallet!!.id()).isEqualTo(20)
        assertThat(wallet.balance()).isEqualTo(Amount(currency, BigDecimal.valueOf(0.5)))
        assertThat(wallet.currency().getSymbol()).isEqualTo("ETH")
    }
}
