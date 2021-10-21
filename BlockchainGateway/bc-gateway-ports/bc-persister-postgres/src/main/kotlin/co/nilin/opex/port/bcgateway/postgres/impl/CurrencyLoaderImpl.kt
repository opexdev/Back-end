package co.nilin.opex.port.bcgateway.postgres.impl

import co.nilin.opex.bcgateway.core.model.*
import co.nilin.opex.bcgateway.core.spi.CurrencyLoader
import co.nilin.opex.port.bcgateway.postgres.dao.ChainRepository
import co.nilin.opex.port.bcgateway.postgres.dao.CurrencyImplementationRepository
import co.nilin.opex.port.bcgateway.postgres.dao.CurrencyRepository
import co.nilin.opex.port.bcgateway.postgres.model.CurrencyImplementationModel
import co.nilin.opex.port.bcgateway.postgres.model.CurrencyModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.awaitSingleOrNull
import org.springframework.stereotype.Component

@Component
class CurrencyLoaderImpl(
    private val chainRepository: ChainRepository,
    private val currencyRepository: CurrencyRepository,
    private val currencyImplementationRepository: CurrencyImplementationRepository
) : CurrencyLoader {
    override suspend fun fetchCurrencyInfo(symbol: String): CurrencyInfo {
        val symbol = symbol.toUpperCase()
        val currencyModel = currencyRepository.findBySymbol(symbol).awaitSingleOrNull()
        if (currencyModel === null) {
            return CurrencyInfo(Currency("", symbol), emptyList())
        }
        val currencyImplModel = currencyImplementationRepository.findBySymbol(symbol)
        val currency = Currency(currencyModel.symbol, currencyModel.name)
        val implementations = currencyImplModel.map { projectCurrencyImplementation(it, currencyModel) }
        return CurrencyInfo(currency, implementations.toList())
    }

    override suspend fun findSymbol(chain: String, address: String?): String? {
        return currencyImplementationRepository.findByChainAndTokenAddress(chain, address)
            .awaitFirstOrNull()?.symbol
    }

    override suspend fun findImplementationsWithTokenOnChain(chain: String): List<CurrencyImplementation> {
        return currencyImplementationRepository.findByChain(chain).map { projectCurrencyImplementation(it) }.toList()
    }

    private suspend fun projectCurrencyImplementation(
        currencyImplementationModel: CurrencyImplementationModel,
        currencyModel: CurrencyModel? = null
    ): CurrencyImplementation {
        val addressTypesModel = chainRepository.findAddressTypesByName(currencyImplementationModel.chain)
        val addressTypes = addressTypesModel.map { AddressType(it.id!!, it.type, it.addressRegex, it.memoRegex) }
        val endpointsModel = chainRepository.findEndpointsByName(currencyImplementationModel.chain)
        val endpoints = endpointsModel.map { Endpoint(it.url) }
        val currencyModelVal = currencyModel ?: currencyRepository.findBySymbol(currencyImplementationModel.symbol).awaitSingle()
        val currency = Currency(currencyModelVal.symbol, currencyModelVal.name)
        return CurrencyImplementation(
            currency,
            Chain(currencyImplementationModel.chain, addressTypes.toList(), endpoints.toList()),
            currencyImplementationModel.token,
            currencyImplementationModel.tokenAddress,
            currencyImplementationModel.tokenName,
            currencyImplementationModel.withdrawEnabled,
            currencyImplementationModel.withdrawFee,
            currencyImplementationModel.withdrawMin
        )
    }
}
