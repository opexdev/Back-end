package co.nilin.opex.bcgateway.app.controller

import co.nilin.opex.bcgateway.app.dto.*
import co.nilin.opex.bcgateway.app.service.AdminService
import co.nilin.opex.bcgateway.core.model.AddressType
import co.nilin.opex.bcgateway.core.spi.AddressTypeHandler
import co.nilin.opex.bcgateway.core.spi.ChainEndpointHandler
import co.nilin.opex.bcgateway.core.spi.ChainLoader
import co.nilin.opex.bcgateway.core.spi.CurrencyHandler
import co.nilin.opex.utility.error.data.OpexError
import co.nilin.opex.utility.error.data.OpexException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin")
class AdminController(
    private val service: AdminService,
    private val chainLoader: ChainLoader,
    private val currencyHandler: CurrencyHandler,
    private val addressTypeHandler: AddressTypeHandler,
    private val chainEndpointHandler: ChainEndpointHandler
) {

    @GetMapping("/chain")
    suspend fun getChains(): List<ChainResponse> {
        return chainLoader.fetchAllChains()
            .map { c -> ChainResponse(c.name, c.addressTypes.map { it.type }, c.endpoints.map { it.url }) }
    }

    @PostMapping("/chain")
    suspend fun addChain(@RequestBody body: AddChainRequest) {
        if (!body.isValid())
            throw OpexException(OpexError.InvalidRequestBody)
        service.addChain(body)
    }

    @PostMapping("/chain/{chain}/endpoint")
    suspend fun addChainEndpoint(@PathVariable chain: String, @RequestBody body: ChainEndpointRequest) {
        chainEndpointHandler.addEndpoint(chain, body.url, body.username, body.password)
    }

    @DeleteMapping("/chain/{chain}/endpoint")
    suspend fun deleteChainEndpoint(@PathVariable chain: String, @RequestParam url: String) {
        chainEndpointHandler.deleteEndpoint(chain, url)
    }

    @GetMapping("/address/type")
    suspend fun getAddressTypes(): List<AddressType> {
        return addressTypeHandler.fetchAll()
    }

    @PostMapping("/address/type")
    suspend fun addAddressType(@RequestBody body: AddressTypeRequest) {
        if (body.name.isNullOrEmpty() || body.addressRegex.isNullOrEmpty())
            throw OpexException(OpexError.InvalidRequestBody)
        service.addAddressType(body.name, body.addressRegex, body.memoRegex)
    }

    @GetMapping("/token")
    suspend fun getCurrencyImplementation(): List<TokenResponse> {
        return currencyHandler.fetchAllImplementations()
            .map {
                TokenResponse(
                    it.currency.symbol,
                    it.chain.name,
                    it.token,
                    it.tokenAddress,
                    it.tokenName,
                    it.withdrawEnabled,
                    it.withdrawFee,
                    it.withdrawMin,
                    it.decimal
                )
            }
    }

    @PostMapping("/token")
    suspend fun addCurrencyImplementation(@RequestBody body: TokenRequest): TokenResponse {
        val ex = OpexException(OpexError.InvalidRequestBody)
        with(body) {
            if (symbol.isNullOrEmpty() || chain.isNullOrEmpty()) throw ex
            if (isToken && (tokenName.isNullOrEmpty() || tokenAddress.isNullOrEmpty())) throw ex
            if (withdrawFee < 0 || minimumWithdraw < 0 || decimal < 0) throw ex
        }

        return with(service.addToken(body)) {
            TokenResponse(
                currency.symbol,
                chain.name,
                token,
                tokenAddress,
                tokenName,
                withdrawEnabled,
                withdrawFee,
                withdrawMin,
                decimal
            )
        }
    }

    @PutMapping("/token/{symbol}_{chain}/withdraw")
    suspend fun changeWithdrawStatus(
        @PathVariable symbol: String,
        @PathVariable chain: String,
        @RequestParam("enabled") status: Boolean
    ) {
        service.changeTokenWithdrawStatus(symbol, chain, status)
    }

}