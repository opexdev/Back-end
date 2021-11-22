package co.nilin.opex.port.websocket.socket

import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent
import org.springframework.web.socket.messaging.*

@Configuration
class StompEventsConfig {

    @Bean
    fun brokerAvailabilityListener() = ApplicationListener<BrokerAvailabilityEvent> { event ->
        println("Is broker available: ${event.isBrokerAvailable}")
    }

    @Bean
    fun sessionConnectListener() = ApplicationListener<SessionConnectEvent> { event ->
        println("* session connect received: ${event.message}")
    }

    @Bean
    fun sessionConnectedListener() = ApplicationListener<SessionConnectedEvent> { event ->
        println("* connected: ${event.message}")
    }

    @Bean
    fun sessionDisconnectedListener() = ApplicationListener<SessionDisconnectEvent> { event ->
        println("* disconnected: ${event.message}")
    }

    @Bean
    fun sessionSubscribeListener() = ApplicationListener<SessionSubscribeEvent> { event ->
        println("* subscribed: ${event.message}")
    }

    @Bean
    fun sessionUnsubscribeEventListener() = ApplicationListener<SessionUnsubscribeEvent> { event ->
        println("- unsubscribed: ${event.message}")
    }

}