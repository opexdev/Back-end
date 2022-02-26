package co.nilin.opex.bcgateway.app.config


import co.nilin.opex.bcgateway.core.api.AssignAddressService
import co.nilin.opex.bcgateway.core.api.ChainSyncService
import co.nilin.opex.bcgateway.core.api.InfoService
import co.nilin.opex.bcgateway.core.api.WalletSyncService
import co.nilin.opex.bcgateway.core.service.AssignAddressServiceImpl
import co.nilin.opex.bcgateway.core.service.ChainSyncServiceImpl
import co.nilin.opex.bcgateway.core.service.InfoServiceImpl
import co.nilin.opex.bcgateway.core.service.WalletSyncServiceImpl
import co.nilin.opex.bcgateway.core.spi.*
import co.nilin.opex.bcgateway.ports.kafka.listener.consumer.AdminEventKafkaListener
import co.nilin.opex.bcgateway.ports.kafka.listener.spi.AdminEventListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
class AppConfig {

    @Bean
    fun assignAddressService(
        currencyHandler: CurrencyHandler,
        assignedAddressHandler: AssignedAddressHandler,
        reservedAddressHandler: ReservedAddressHandler
    ): AssignAddressService {
        return AssignAddressServiceImpl(currencyHandler, assignedAddressHandler, reservedAddressHandler)
    }

    @Bean
    fun chainSyncService(
        chainSyncSchedulerHandler: ChainSyncSchedulerHandler,
        chainEndpointHandler: ChainEndpointHandler,
        chainSyncRecordHandler: ChainSyncRecordHandler,
        walletSyncRecordHandler: WalletSyncRecordHandler,
        chainSyncRetryHandler: ChainSyncRetryHandler,
        currencyHandler: CurrencyHandler,
        operator: TransactionalOperator
    ): ChainSyncService {
        return ChainSyncServiceImpl(
            chainSyncSchedulerHandler,
            chainEndpointHandler,
            chainSyncRecordHandler,
            walletSyncRecordHandler,
            chainSyncRetryHandler,
            currencyHandler,
            operator,
            AppDispatchers.chainSyncExecutor
        )
    }

    @Bean
    fun walletSyncService(
        syncSchedulerHandler: WalletSyncSchedulerHandler,
        walletProxy: WalletProxy,
        walletSyncRecordHandler: WalletSyncRecordHandler,
        assignedAddressHandler: AssignedAddressHandler,
        currencyHandler: CurrencyHandler
    ): WalletSyncService {
        return WalletSyncServiceImpl(
            syncSchedulerHandler,
            walletProxy,
            walletSyncRecordHandler,
            assignedAddressHandler,
            currencyHandler,
            AppDispatchers.walletSyncExecutor
        )
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
