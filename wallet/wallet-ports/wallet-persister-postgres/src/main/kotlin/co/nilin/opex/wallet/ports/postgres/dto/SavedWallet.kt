package co.nilin.opex.wallet.ports.postgres.dto

import co.nilin.opex.wallet.core.model.Amount
import co.nilin.opex.wallet.core.model.Currency
import co.nilin.opex.wallet.core.model.Wallet
import co.nilin.opex.wallet.core.model.WalletOwner

class SavedWallet(
    val id_: Long, val owner_: WalletOwner, val balance_: Amount, val currency_: Currency, val type_: String
) : Wallet {
    override fun id(): Long {
        return id_
    }

    override fun owner(): WalletOwner {
        return owner_
    }

    override fun balance(): Amount {
        return balance_
    }

    override fun currency(): Currency {
        return currency_
    }

    override fun type(): String {
        return type_
    }
}