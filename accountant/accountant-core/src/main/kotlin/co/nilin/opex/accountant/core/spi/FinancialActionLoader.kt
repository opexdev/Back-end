package co.nilin.opex.accountant.core.spi

import co.nilin.opex.accountant.core.model.FinancialAction

interface FinancialActionLoader {
    suspend fun findLast(userUuid: String, ouid: String): FinancialAction?
    suspend fun loadUnprocessed(offset: Long, size: Long): List<FinancialAction>
    suspend fun countUnprocessed(userUuid: String, symbol: String, eventType: String): Long
    suspend fun loadReadyToProcess(offset: Long, size: Long): List<FinancialAction>
    suspend fun loadFinancialAction(id: Long?): FinancialAction?
    suspend fun loadRetries(limit: Int): List<FinancialAction>
}