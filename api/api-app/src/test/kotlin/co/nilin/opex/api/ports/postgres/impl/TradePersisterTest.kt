package co.nilin.opex.api.ports.postgres.impl

import co.nilin.opex.api.ports.postgres.dao.TradeRepository
import co.nilin.opex.api.ports.postgres.impl.sample.Valid
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stubbing
import reactor.core.publisher.Mono

class TradePersisterTest {
    private val tradeRepository: TradeRepository = mock()
    private val tradePersister = TradePersisterImpl(tradeRepository)

    @Test
    fun givenTradeRepo_whenSaveRichTrade_thenSuccess(): Unit = runBlocking {
        stubbing(tradeRepository) {
            on {
                save(any())
            } doReturn Mono.just(Valid.TRADE_MODEL)
        }

        assertThatNoException().isThrownBy { runBlocking { tradePersister.save(Valid.RICH_TRADE) } }
    }
}
