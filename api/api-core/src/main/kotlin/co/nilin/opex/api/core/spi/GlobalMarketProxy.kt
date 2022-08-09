package co.nilin.opex.api.core.spi

import co.nilin.opex.api.core.inout.GlobalPrice

interface GlobalMarketProxy {

    suspend fun getPrices(symbols: List<String>): List<GlobalPrice>

}