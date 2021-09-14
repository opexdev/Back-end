package co.nilin.opex.port.bcgateway.postgres.impl

import co.nilin.opex.bcgateway.core.model.ChainSyncRecord
import co.nilin.opex.bcgateway.core.model.Deposit
import co.nilin.opex.bcgateway.core.model.Endpoint
import co.nilin.opex.bcgateway.core.spi.SyncRecordHandler
import co.nilin.opex.port.bcgateway.postgres.dao.ChainSyncRecordRepository
import co.nilin.opex.port.bcgateway.postgres.dao.DepositRepository
import co.nilin.opex.port.bcgateway.postgres.model.DepositModel
import co.nilin.opex.port.bcgateway.postgres.model.SyncRecordModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitSingleOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SyncRecordHandlerImpl(
    private val chainSyncRecordRepository: ChainSyncRecordRepository,
    private val depositRepository: DepositRepository
) : SyncRecordHandler {
    override suspend fun loadLastSuccessRecord(chainName: String): ChainSyncRecord? {
        val chainSyncRecordDao = chainSyncRecordRepository.findByChain(chainName).awaitSingleOrNull()
        return if (chainSyncRecordDao !== null) {
            val deposits = depositRepository.findByChain(chainName).map {
                Deposit(it.depositor, it.depositorMemo, it.amount, it.chain, it.token, it.tokenAddress)
            }
            ChainSyncRecord(
                chainSyncRecordDao.chain,
                chainSyncRecordDao.time,
                Endpoint(chainSyncRecordDao.endpointUrl),
                chainSyncRecordDao.latestBlock,
                chainSyncRecordDao.success,
                chainSyncRecordDao.error,
                deposits.toList()
            )
        } else {
            null
        }
    }

    @Transactional
    override suspend fun saveSyncRecord(syncRecord: ChainSyncRecord) {
        val chainSyncRecordDao =
            SyncRecordModel(
                syncRecord.chainName,
                syncRecord.time,
                syncRecord.endpoint.url,
                syncRecord.latestBlock,
                syncRecord.success,
                syncRecord.error
            )
        chainSyncRecordRepository.save(chainSyncRecordDao).awaitFirst()
        val depositsDao = syncRecord.records.map {
            DepositModel(
                null,
                it.depositor,
                it.depositorMemo,
                it.amount,
                it.chain,
                it.token,
                it.tokenAddress
            )
        }
        depositRepository.saveAll(depositsDao).awaitFirst()
    }
}