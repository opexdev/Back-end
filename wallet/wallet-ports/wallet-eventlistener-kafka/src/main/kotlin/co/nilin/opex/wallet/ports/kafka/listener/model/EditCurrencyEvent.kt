package co.nilin.opex.wallet.ports.kafka.listener.model

data class EditCurrencyEvent(
    val name: String,
    val symbol: String,
    val precision: Double
) : AdminEvent()