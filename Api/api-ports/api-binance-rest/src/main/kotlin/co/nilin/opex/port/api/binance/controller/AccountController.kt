package co.nilin.opex.port.api.binance.controller

import co.nilin.opex.api.core.inout.*
import co.nilin.opex.api.core.spi.MEGatewayProxy
import co.nilin.opex.api.core.spi.UserQueryHandler
import co.nilin.opex.api.core.spi.WalletProxy
import co.nilin.opex.port.api.binance.data.AccountInfoResponse
import co.nilin.opex.port.api.binance.security.CustomAuthToken
import co.nilin.opex.port.api.binance.util.BalanceParser
import co.nilin.opex.port.api.binance.util.asMatchConstraint
import co.nilin.opex.port.api.binance.util.asMatchingOrderType
import co.nilin.opex.port.api.binance.util.asOrderDirection
import co.nilin.opex.utility.error.data.OpexError
import co.nilin.opex.utility.error.data.OpexException
import co.nilin.opex.api.core.spi.SymbolAdapter
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.security.Principal
import java.util.*

@RestController
@CrossOrigin(origins = ["https://opex.dev", "http://localhost:3000"], allowedHeaders = ["*"])
class AccountController(
    val queryHandler: UserQueryHandler,
    val matchingGatewayProxy: MEGatewayProxy,
    val walletProxy: WalletProxy,
    val symbolAdapter: SymbolAdapter
) {

    data class FillsData(
        val price: BigDecimal,
        val qty: BigDecimal,
        val commission: BigDecimal,
        val commissionAsset: String
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class NewOrderResponse(
        val symbol: String,
        val orderId: Long,
        val orderListId: Long, //Unless OCO, value will be -1
        val clientOrderId: String?,
        val transactTime: Date,
        val price: BigDecimal?,
        val origQty: BigDecimal?,
        val executedQty: BigDecimal?,
        val cummulativeQuoteQty: BigDecimal?,
        val status: OrderStatus?,
        val timeInForce: TimeInForce?,
        val type: OrderType?,
        val side: OrderSide?,
        val fills: List<FillsData>?
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class QueryOrderResponse(
        val symbol: String,
        val orderId: Long,
        val orderListId: Long, //Unless part of an OCO, the value will always be -1.
        val clientOrderId: String,
        val price: BigDecimal,
        val origQty: BigDecimal,
        val executedQty: BigDecimal,
        val cummulativeQuoteQty: BigDecimal,
        val status: OrderStatus,
        val timeInForce: TimeInForce,
        val type: OrderType,
        val side: OrderSide,
        val stopPrice: BigDecimal?,
        val icebergQty: BigDecimal?,
        val time: Date,
        val updateTime: Date,
        val isWorking: Boolean,
        val origQuoteOrderQty: BigDecimal
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class TradeResponse(
        val symbol: String,
        val id: Long,
        val orderId: Long,
        val orderListId: Long = -1,
        val price: BigDecimal,
        val qty: BigDecimal,
        val quoteQty: BigDecimal,
        val commission: BigDecimal,
        val commissionAsset: String,
        val time: Date,
        val isBuyer: Boolean,
        val isMaker: Boolean,
        val isBestMatch: Boolean
    )

    private val logger = LoggerFactory.getLogger(AccountController::class.java)

    /*
   Send in a new order.
   Weight: 1
   Data Source: Matching Engine
    */
    @PostMapping(
        "/v3/order",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ApiResponse(
        message = "OK",
        code = 200,
        examples = Example(
            ExampleProperty(
                value = "{ \"symbol\": \"btc_usdt\", \"orderId\": -1, \"orderListId\": -1, \"transactTime\": \"2021-08-03T11:09:23.190+00:00\" }",
                mediaType = "application/json"
            )
        )
    )
    suspend fun createNewOrder(
        @RequestParam(name = "symbol")
        symbol: String,
        @RequestParam(name = "side")
        side: OrderSide,
        @RequestParam(name = "type")
        type: OrderType,
        @RequestParam(name = "timeInForce", required = false)
        timeInForce: TimeInForce?,
        @RequestParam(name = "quantity", required = false)
        quantity: BigDecimal?,
        @RequestParam(name = "quoteOrderQty", required = false)
        quoteOrderQty: BigDecimal?,
        @RequestParam(name = "price", required = false)
        price: BigDecimal?,
        @ApiParam(
            value = "A unique id among open orders. Automatically generated if not sent.\n" +
                    "Orders with the same newClientOrderID can be accepted only when the previous one is filled, otherwise the order will be rejected."
        )
        @RequestParam(name = "newClientOrderId", required = false)
        newClientOrderId: String?,    /* A unique id among open orders. Automatically generated if not sent.
    Orders with the same newClientOrderID can be accepted only when the previous one is filled, otherwise the order will be rejected.
    */
        @ApiParam(value = "Used with STOP_LOSS, STOP_LOSS_LIMIT, TAKE_PROFIT, and TAKE_PROFIT_LIMIT orders.")
        @RequestParam(name = "stopPrice", required = false)
        stopPrice: BigDecimal?, //Used with STOP_LOSS, STOP_LOSS_LIMIT, TAKE_PROFIT, and TAKE_PROFIT_LIMIT orders.
        @RequestParam(name = "icebergQty", required = false)
        @ApiParam(value = "Used with LIMIT, STOP_LOSS_LIMIT, and TAKE_PROFIT_LIMIT to create an iceberg order.")
        icebergQty: BigDecimal?, //Used with LIMIT, STOP_LOSS_LIMIT, and TAKE_PROFIT_LIMIT to create an iceberg order.
        @RequestParam(name = "newOrderRespType", required = false)
        @ApiParam(value = "Set the response JSON. ACK, RESULT, or FULL; MARKET and LIMIT order types default to FULL, all other orders default to ACK.")
        newOrderRespType: OrderResponseType?,  //Set the response JSON. ACK, RESULT, or FULL; MARKET and LIMIT order types default to FULL, all other orders default to ACK.
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(name = "recvWindow", required = false)
        recvWindow: Long?, //The value cannot be greater than 60000
        @RequestParam(name = "timestamp")
        timestamp: Long,
        @AuthenticationPrincipal auth: CustomAuthToken
    ): NewOrderResponse {
        val pairSymbol = symbolAdapter.mapFrom(symbol)!!
        val request = MEGatewayProxy.CreateOrderRequest(
            auth.uuid,
            pairSymbol,
            price ?: BigDecimal.ZERO, // Maybe make this nullable as well?
            quantity ?: BigDecimal.ZERO,
            side.asOrderDirection(),
            timeInForce?.asMatchConstraint(),
            type.asMatchingOrderType()
        )

        matchingGatewayProxy.createNewOrder(request, auth.tokenValue)
        return NewOrderResponse(
            pairSymbol,
            -1,
            -1,
            null,
            Date(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
    }

    /*
  Check an order's status.

  Weight: 2
  Data Source: Database
  */
    @GetMapping(
        "/v3/order",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ApiResponse(
        message = "OK",
        code = 200,
        examples = Example(
            ExampleProperty(
                value = "{ \"symbol\": \"btc_usdt\", \"orderId\": 12, \"orderListId\": -1, \"clientOrderId\": \"\", \"price\": 1, \"origQty\": 10, \"executedQty\": 0, \"cummulativeQuoteQty\": 0, \"status\": \"NEW\", \"timeInForce\": \"GTC\", \"type\": \"LIMIT\", \"side\": \"SELL\", \"time\": \"2021-08-04T12:10:13.488+00:00\", \"updateTime\": \"2021-08-04T12:10:13.488+00:00\", \"isWorking\": true, \"origQuoteOrderQty\": 10 }",
                mediaType = "application/json"
            )
        )
    )
    suspend fun queryOrder(
        principal: Principal,
        @RequestParam(name = "symbol")
        symbol: String,
        @RequestParam(name = "orderId", required = false)
        orderId: Long?,
        @RequestParam(name = "origClientOrderId", required = false)
        origClientOrderId: String?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(name = "recvWindow", required = false)
        recvWindow: Long?, //The value cannot be greater than 60000
        @RequestParam(name = "timestamp")
        timestamp: Long
    ): QueryOrderResponse {
        val pairSymbol = symbolAdapter.mapFrom(symbol)!!
        val response = queryHandler.queryOrder(principal, QueryOrderRequest(pairSymbol, orderId, origClientOrderId))
            ?: throw OpexException(OpexError.OrderNotFound)
        return QueryOrderResponse(
            symbolAdapter.mapFrom(response.symbol)!!,
            response.orderId,
            response.orderListId,
            response.clientOrderId,
            response.price,
            response.origQty,
            response.executedQty,
            response.cummulativeQuoteQty,
            response.status,
            response.timeInForce,
            response.type,
            response.side,
            response.stopPrice,
            response.icebergQty,
            response.time,
            response.updateTime,
            response.isWorking,
            response.origQuoteOrderQty
        )
    }


    /*
      Get all open orders on a symbol. Careful when accessing this with no symbol.

      Weight: 3 for a single symbol; 40 when the symbol parameter is omitted

      Data Source: Database
    */
    @GetMapping(
        "/v3/openOrders",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ApiResponse(
        message = "OK",
        code = 200,
        examples = Example(
            ExampleProperty(
                value = "[ { \"symbol\": \"btc_usdt\", \"orderId\": 12, \"orderListId\": -1, \"clientOrderId\": \"\", \"price\": 1, \"origQty\": 10, \"executedQty\": 0, \"cummulativeQuoteQty\": 0, \"status\": \"NEW\", \"timeInForce\": \"GTC\", \"type\": \"LIMIT\", \"side\": \"SELL\", \"time\": \"2021-08-04T12:10:13.488+00:00\", \"updateTime\": \"2021-08-04T12:10:13.488+00:00\", \"isWorking\": true, \"origQuoteOrderQty\": 10 } ]",
                mediaType = "application/json"
            )
        )
    )
    suspend fun fetchOpenOrders(
        principal: Principal,
        @RequestParam(name = "symbol", required = false)
        symbol: String?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(name = "recvWindow", required = false)
        recvWindow: Long?, //The value cannot be greater than 60000
        @RequestParam(name = "timestamp")
        timestamp: Long
    ): Flow<QueryOrderResponse> {
        val pairSymbol = symbolAdapter.mapFrom(symbol)
        return queryHandler.openOrders(principal, pairSymbol)
            .map { response ->
                QueryOrderResponse(
                    symbolAdapter.mapFrom(response.symbol)!!,
                    response.orderId,
                    response.orderListId,
                    response.clientOrderId,
                    response.price,
                    response.origQty,
                    response.executedQty,
                    response.cummulativeQuoteQty,
                    response.status,
                    response.timeInForce,
                    response.type,
                    response.side,
                    response.stopPrice,
                    response.icebergQty,
                    response.time,
                    response.updateTime,
                    response.isWorking,
                    response.origQuoteOrderQty
                )
            }
    }

    /*
   Get all account orders; active, canceled, or filled.
   Weight: 10 with symbol
   Data Source: Database
   */
    @GetMapping(
        "/v3/allOrders",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
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
    suspend fun fetchAllOrders(
        principal: Principal,
        @RequestParam(name = "symbol", required = false)
        symbol: String?,
        @RequestParam(name = "startTime", required = false)
        startTime: Date?,
        @RequestParam(name = "endTime", required = false)
        endTime: Date?,
        @ApiParam(value = "Default 500; max 1000.")
        @RequestParam(name = "limit", required = false)
        limit: Int? = 500, //Default 500; max 1000.
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(name = "recvWindow", required = false)
        recvWindow: Long?, //The value cannot be greater than 60000
        @RequestParam(name = "timestamp")
        timestamp: Long
    ): Flow<QueryOrderResponse> {
        val pairSymbol = symbolAdapter.mapFrom(symbol)
        return queryHandler.allOrders(principal, AllOrderRequest(pairSymbol, startTime, endTime, limit))
            .map { response ->
                QueryOrderResponse(
                    symbolAdapter.mapFrom(response.symbol)!!,
                    response.orderId,
                    response.orderListId,
                    response.clientOrderId,
                    response.price,
                    response.origQty,
                    response.executedQty,
                    response.cummulativeQuoteQty,
                    response.status,
                    response.timeInForce,
                    response.type,
                    response.side,
                    response.stopPrice,
                    response.icebergQty,
                    response.time,
                    response.updateTime,
                    response.isWorking,
                    response.origQuoteOrderQty
                )
            }
    }

    /*
    Get trades for a specific account and symbol.
    If fromId is set, it will get trades >= that fromId. Otherwise most recent trades are returned.
    Weight: 10 with symbol
    Data Source: Database
    */
    @GetMapping(
        "/v3/myTrades",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
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
    suspend fun fetchAllTrades(
        principal: Principal,
        @RequestParam(name = "symbol")
        symbol: String?,
        @RequestParam(name = "startTime", required = false)
        startTime: Date?,
        @RequestParam(name = "endTime", required = false)
        endTime: Date?,
        @ApiParam(value = "TradeId to fetch from. Default gets most recent trades.")
        @RequestParam(name = "fromId", required = false)
        fromId: Long?,//TradeId to fetch from. Default gets most recent trades.
        @ApiParam(value = "Default 500; max 1000.")
        @RequestParam(name = "limit", required = false)
        limit: Int? = 500, //Default 500; max 1000.
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(name = "recvWindow", required = false)
        recvWindow: Long?, //The value cannot be greater than 60000
        @RequestParam(name = "timestamp")
        timestamp: Long
    ): Flow<TradeResponse> {
        val pairSymbol = symbolAdapter.mapFrom(symbol)
        return queryHandler.allTrades(principal, TradeRequest(pairSymbol, fromId, startTime, endTime, limit))
            .map { response ->
                TradeResponse(
                    symbolAdapter.mapFrom(response.symbol)!!, response.id,
                    response.orderId, -1, response.price, response.qty, response.quoteQty,
                    response.commission, response.commissionAsset, response.time, response.isBuyer,
                    response.isMaker, response.isBestMatch
                )
            }
    }

    @GetMapping(
        "/v3/account",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ApiResponse(
        message = "OK",
        code = 200,
        examples = Example(
            ExampleProperty(
                value = "{ \"makerCommission\": 0, \"takerCommission\": 0, \"buyerCommission\": 0, \"sellerCommission\": 0, \"canTrade\": true, \"canWithdraw\": true, \"canDeposit\": true, \"updateTime\": 1628420513843, \"accountType\": \"SPOT\", \"balances\": [ { \"asset\": \"usdt\", \"free\": 1000, \"locked\": 0 } ], \"permissions\": [ \"SPOT\" ] }",
                mediaType = "application/json"
            )
        )
    )
    suspend fun accountInfo(
        @AuthenticationPrincipal
        auth: CustomAuthToken,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(name = "recvWindow", required = false)
        recvWindow: Long?, //The value cannot be greater than 60000
        @RequestParam(name = "timestamp")
        timestamp: Long
    ): AccountInfoResponse {
        val wallets = walletProxy.getWallets(auth.uuid, auth.tokenValue)
        val limits = walletProxy.getOwnerLimits(auth.uuid, auth.tokenValue)
        val parsedBalances = BalanceParser.parse(wallets)
        val accountType = "SPOT"

        //TODO replace commissions and accountType with actual data
        return AccountInfoResponse(
            0,
            0,
            0,
            0,
            limits.canTrade,
            limits.canWithdraw,
            limits.canDeposit,
            Date().time,
            accountType,
            parsedBalances,
            listOf(accountType)
        )
    }

}