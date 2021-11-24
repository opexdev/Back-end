package co.nilin.opex.api.ports.binance.proxy

import co.nilin.opex.api.core.inout.CancelOrderRequest
import co.nilin.opex.api.core.inout.OrderSubmitResult
import co.nilin.opex.api.core.spi.MEGatewayProxy
import co.nilin.opex.api.ports.binance.util.LoggerDelegate
import kotlinx.coroutines.reactive.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import reactor.core.publisher.Mono
import java.net.URI

private inline fun <reified T : Any?> typeRef(): ParameterizedTypeReference<T> =
    object : ParameterizedTypeReference<T>() {}

@Component
class MEGatewayProxyImpl(private val client: WebClient) : MEGatewayProxy {

    private val logger by LoggerDelegate()

    @Value("\${app.matching-gateway.url}")
    private lateinit var baseUrl: String

    override suspend fun createNewOrder(order: MEGatewayProxy.CreateOrderRequest, token: String?): OrderSubmitResult? {
        logger.info("calling matching-gateway order create")
        return client.post()
            .uri(URI.create("$baseUrl/order"))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .body(Mono.just(order))
            .retrieve()
            .onStatus({ t -> t.isError }, { it.createException() })
            .bodyToMono(typeRef<OrderSubmitResult>())
            .awaitSingleOrNull()
    }

    override suspend fun cancelOrder(request: CancelOrderRequest, token: String?): OrderSubmitResult? {
        logger.info("calling matching-gateway order cancel")
        return client.post()
            .uri(URI.create("$baseUrl/order/cancel"))
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .body(Mono.just(request))
            .retrieve()
            .onStatus({ t -> t.isError }, { it.createException() })
            .bodyToMono(typeRef<OrderSubmitResult>())
            .awaitSingleOrNull()
    }
}