package co.nilin.opex.wallet.core.spi

import co.nilin.opex.wallet.core.model.CurrencyImp
import co.nilin.opex.wallet.core.model.PropagateCurrencyChanges
import co.nilin.opex.wallet.core.model.otc.*

interface AuthProxy {
   suspend fun getToken(loginRequest: LoginRequest):LoginResponse
}