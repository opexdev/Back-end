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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
class AppConfig {

    @Bean
    fun assignAddressService(
        currencyLoader: CurrencyLoader,
        assignedAddressHandler: AssignedAddressHandler,
        reservedAddressHandler: ReservedAddressHandler
    ): AssignAddressService {
        return AssignAddressServiceImpl(currencyLoader, assignedAddressHandler, reservedAddressHandler)
    }

    @Bean
    fun chainSyncService(
        chainSyncSchedulerHandler: ChainSyncSchedulerHandler,
        chainEndpointProxyFinder: ChainEndpointProxyFinder,
        chainSyncRecordHandler: ChainSyncRecordHandler,
        walletSyncRecordHandler: WalletSyncRecordHandler,
        currencyLoader: CurrencyLoader,
        operator: TransactionalOperator
    ): ChainSyncService {
        return ChainSyncServiceImpl(
            chainSyncSchedulerHandler,
            chainEndpointProxyFinder,
            chainSyncRecordHandler,
            walletSyncRecordHandler,
            currencyLoader,
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
        currencyLoader: CurrencyLoader
    ): WalletSyncService {
        return WalletSyncServiceImpl(
            syncSchedulerHandler,
            walletProxy,
            walletSyncRecordHandler,
            assignedAddressHandler,
            currencyLoader,
            AppDispatchers.walletSyncExecutor
        )
    }

    @Bean
    fun infoService(): InfoService {
        return InfoServiceImpl()
    }
}
