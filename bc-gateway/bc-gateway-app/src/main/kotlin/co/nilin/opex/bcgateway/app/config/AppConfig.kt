package co.nilin.opex.bcgateway.app.config


import co.nilin.opex.bcgateway.core.api.AssignAddressService
import co.nilin.opex.bcgateway.core.api.InfoService
import co.nilin.opex.bcgateway.core.service.AssignAddressServiceImpl
import co.nilin.opex.bcgateway.core.service.InfoServiceImpl
import co.nilin.opex.bcgateway.core.spi.AssignedAddressHandler
import co.nilin.opex.bcgateway.core.spi.ChainLoader
import co.nilin.opex.bcgateway.core.spi.ReservedAddressHandler
import co.nilin.opex.bcgateway.ports.kafka.listener.consumer.AdminEventKafkaListener
import co.nilin.opex.bcgateway.ports.kafka.listener.spi.AdminEventListener
import co.nilin.opex.bcgateway.ports.postgres.impl.CurrencyHandlerImplV2
import co.nilin.opex.common.utils.CustomErrorTranslator
import co.nilin.opex.utility.error.spi.ErrorTranslator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource


@Configuration
class AppConfig {

    @Bean
    fun assignAddressService(
            currencyHandler: CurrencyHandlerImplV2,
            assignedAddressHandler: AssignedAddressHandler,
            reservedAddressHandler: ReservedAddressHandler,
            chainLoader: ChainLoader
    ): AssignAddressService {
        return AssignAddressServiceImpl(currencyHandler, assignedAddressHandler, reservedAddressHandler, chainLoader)
    }

    @Bean
    fun infoService(): InfoService {
        return InfoServiceImpl()
    }

    @Autowired
    fun configureEventListeners(
            adminKafkaEventListener: AdminEventKafkaListener,
            adminEventListener: AdminEventListener,
    ) {
        adminKafkaEventListener.addEventListener(adminEventListener)
    }

}
