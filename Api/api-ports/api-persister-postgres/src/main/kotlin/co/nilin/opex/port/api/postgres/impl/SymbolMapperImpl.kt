package co.nilin.opex.port.api.postgres.impl

import co.nilin.opex.api.core.spi.SymbolMapper
import co.nilin.opex.port.api.postgres.dao.SymbolMapRepository
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component

@Component
class SymbolMapperImpl(val symbolMapRepository: SymbolMapRepository) : SymbolMapper {
    override suspend fun map(symbol: String?): String? {
        if (symbol == null) return null
        return symbolMapRepository.findBySymbol(symbol).awaitFirstOrNull()?.value
    }

    override suspend fun unmap(value: String?): String? {
        if (value == null) return null
        return symbolMapRepository.findByValue(value).awaitFirstOrNull()?.symbol
    }
}