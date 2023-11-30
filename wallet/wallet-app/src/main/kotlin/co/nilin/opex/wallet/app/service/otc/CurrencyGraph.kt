package co.nilin.opex.wallet.app.service.otc

import co.nilin.opex.utility.error.data.OpexError
import co.nilin.opex.utility.error.data.OpexException
import co.nilin.opex.wallet.core.model.Currency
import co.nilin.opex.wallet.core.model.otc.*
import co.nilin.opex.wallet.core.service.otc.GraphService
import co.nilin.opex.wallet.core.spi.CurrencyService
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class CurrencyGraph() {
    @Autowired
    lateinit var graphService: GraphService

    @Autowired
    lateinit var currencyService: CurrencyService

    data class Route(val rates: List<Rate>) {
        fun getSourceSymbol(): String {
            return rates.get(0).sourceSymbol
        }

        fun getDestSymbol(): String {
            return rates.get(rates.lastIndex).destSymbol
        }

        fun getRate(): BigDecimal {
            return rates.map { rate -> rate.rate }.reduce { accumulator, element ->
                accumulator.multiply(element)
            }
        }

    }


    @Throws(Exception::class)
    suspend fun addCurrencyRateV2(
            sourceSymbol: String, destSymbol: String, rate: BigDecimal
    ) {
        graphService.addRate(Rate(sourceSymbol, destSymbol, rate))
    }


    @Throws(Exception::class)
    suspend fun removeCurrencyRateV2(sourceSymbol: String, destSymbol: String): Rates {
        return graphService.deleteRate(Rate(sourceSymbol, destSymbol, BigDecimal.ZERO))
    }

    suspend fun getRatesV2(): Rates {
        return graphService.getRates()
    }


    suspend fun getRatesV2(sourceSymbol: String, destinationSymbol: String): Rate? {
        return graphService.getRates(sourceSymbol, destinationSymbol)
    }

    suspend fun updateRate(rate: Rate): Rates {
        return graphService.updateRate(rate)
    }


    @Throws(Exception::class)
    suspend fun addForbiddenRateNamesV2(sourceSymbol: String, destSymbol: String) {
        graphService.addForbiddenPair(ForbiddenPair(sourceSymbol, destSymbol))
    }


    @Throws(Exception::class)
    suspend fun removeForbiddenRateNamesV2(sourceSymbol: String, destSymbol: String): ForbiddenPairs {
        return graphService.deleteForbiddenPair(ForbiddenPair(sourceSymbol, destSymbol))
    }


    @Throws(Exception::class)
    suspend fun getForbiddenRateNamesV2(): ForbiddenPairs {
        return graphService.getForbiddenPairs()
    }


    private val forbiddenRateNames: MutableList<String> = mutableListOf()

    private val rates: MutableList<Rate> = mutableListOf()

    private val routesWithMax2Step: MutableList<Route> = mutableListOf()

    private val transitiveSymbols: MutableSet<String> = mutableSetOf()

    private fun findEdge(sourceSymbol: String, destSymbol: String): Rate? {
        return rates.find { edge ->
            edge.sourceSymbol.equals(sourceSymbol)
                    && edge.destSymbol.equals(destSymbol)
        }
    }

    @Throws(Exception::class)
    fun addCurrencyRate(
            sourceSymbol: String, destSymbol: String, rate: BigDecimal
    ) {
        if (forbiddenRateNames.contains(getRateName(sourceSymbol, destSymbol))) {
            throw Exception("This source & dest is forbidden")
        }
        val copyEdges = rates.toMutableList()
        val existingRate: Rate? = findEdge(sourceSymbol, destSymbol)
        if (existingRate != null) {
            rates.remove(existingRate)
        }
        rates.add(Rate(sourceSymbol, destSymbol, rate))
        try {
            rebuildRoutes()
        } catch (e: Exception) {
            rates.clear()
            rates.addAll(copyEdges)
            rebuildRoutes()
            throw e
        }
    }

    @Throws(Exception::class)
    fun removeCurrencyRate(sourceSymbol: String, destSymbol: String) {
        val copyEdges = rates.toMutableList()
        val existingRate: Rate? = findEdge(sourceSymbol, destSymbol)
        if (existingRate != null) {
            rates.remove(existingRate)
            try {
                rebuildRoutes()
            } catch (e: Exception) {
                rates.clear()
                rates.addAll(copyEdges)
                rebuildRoutes()
                throw e
            }
        }
    }

    @Throws(Exception::class)
    fun addForbiddenRateNames(sourceSymbol: String, destSymbol: String) {
        val rateName = getRateName(sourceSymbol, destSymbol)
        if (!forbiddenRateNames.contains(rateName)) {
            forbiddenRateNames.add(rateName)
            try {
                rebuildRoutes()
            } catch (e: Exception) {
                forbiddenRateNames.remove(rateName)
                rebuildRoutes()
                throw e
            }
        }
    }

    @Throws(Exception::class)
    fun removeForbiddenRateNames(sourceSymbol: String, destSymbol: String) {
        val rateName = getRateName(sourceSymbol, destSymbol)
        if (forbiddenRateNames.contains(rateName)) {
            forbiddenRateNames.remove(rateName)
            try {
                rebuildRoutes()
            } catch (e: Exception) {
                forbiddenRateNames.add(rateName)
                rebuildRoutes()
                throw e
            }
        }
    }


    @Throws(Exception::class)
    suspend fun addTransitiveSymbolsV2(symbols: Symbols) {
        graphService.addTransitiveSymbols(symbols)
    }

    @Throws(Exception::class)
    suspend fun removeTransitiveSymbolsV2(symbols: Symbols): Symbols {
        return graphService.deleteTransitiveSymbols(symbols)
    }

    suspend fun getTransitiveSymbolsV2(): Symbols {
        return graphService.getTransitiveSymbols()
    }

    @Throws(Exception::class)
    fun addTransitiveSymbols(symbols: List<String>) {
        if (!transitiveSymbols.containsAll(symbols)) {
            transitiveSymbols.addAll(symbols)
            try {
                rebuildRoutes()
            } catch (e: Exception) {
                transitiveSymbols.removeAll(symbols)
                rebuildRoutes()
                throw e
            }
        }
    }


    @Throws(Exception::class)
    fun removeTransitiveSymbols(symbols: List<String>) {
        if (transitiveSymbols.findLast { ts -> symbols.contains(ts) } != null) {
            val copyTransitiveSymbols = transitiveSymbols.toMutableList()
            transitiveSymbols.removeAll(symbols.toSet())
            try {
                rebuildRoutes()
            } catch (e: Exception) {
                transitiveSymbols.clear()
                transitiveSymbols.addAll(copyTransitiveSymbols)
                rebuildRoutes()
                throw e
            }
        }
    }

    fun findRoute(sourceSymbol: String, destSymbol: String): Rate? {
        val rateName = getRateName(sourceSymbol, destSymbol)
        //todo create routesagain
        return routesWithMax2Step
                .filter { route -> getRateName(route.getSourceSymbol(), route.getDestSymbol()) == rateName }
                .map { route -> Rate(route.getSourceSymbol(), route.getDestSymbol(), route.getRate()) }
                .firstOrNull()
    }


    private fun rebuildRoutes() {
        routesWithMax2Step.clear()
        val adjencyMap: Map<String, MutableList<Rate>> = createAdjacencyMap()
        val vertice: List<String> = fetchAllSymbols()
        for (vertex in vertice) {
            val visited = mutableSetOf<String>()
            findRoutesWithMax2Edges(vertex, adjencyMap, visited, 0, mutableListOf())
        }
    }

    private fun createAdjacencyMap(): Map<String, MutableList<Rate>> {
        val adjacencyMap = mutableMapOf<String, MutableList<Rate>>()
        rates.forEach { rate ->
            adjacencyMap.computeIfAbsent(rate.sourceSymbol) { mutableListOf() }.add(rate)
        }
        return adjacencyMap
    }

    private  fun findRoutesWithMax2Edges(
            currentVertex: String,
            adjacencyMap: Map<String, MutableList<Rate>>,
            visited: MutableSet<String>,
            currentLength: Int,
            currentRoute: MutableList<Rate>
    ) {
        if (currentLength == 3) {
            return
        }
        visited.add(currentVertex)
        if (currentLength >= 1) {
            if (!transitiveSymbols.contains(currentRoute.get(currentRoute.lastIndex).destSymbol)) {
                val existingRoute = routesWithMax2Step.find { route ->
                    route.getSourceSymbol() == currentRoute.get(0).sourceSymbol
                            &&
                            route.getDestSymbol() == currentRoute.get(currentRoute.lastIndex).destSymbol
                }
                if (existingRoute != null) {
                    if (existingRoute.rates.size > currentRoute.size) {
                        routesWithMax2Step.remove(existingRoute)
                        addCurrentRoute(currentRoute)
                    } else if (existingRoute.rates.size == currentRoute.size
                            && existingRoute.rates != currentRoute
                    ) {
                        throw Exception("Only one route should be available between two symbols")
                    }
                } else {
                    addCurrentRoute(currentRoute)
                }
            }
        }
        for (edge in adjacencyMap[currentVertex] ?: emptyList()) {
            if (!visited.contains(edge.destSymbol)) {
                currentRoute.add(edge)
                findRoutesWithMax2Edges(
                        edge.destSymbol,
                        adjacencyMap,
                        visited,
                        currentLength + 1,
                        currentRoute
                )
                currentRoute.removeAt(currentRoute.size - 1)
            }
        }
        visited.remove(currentVertex)
    }



    private fun addCurrentRoute(currentRoute: MutableList<Rate>) {
        val route = Route(currentRoute.toList());
        if (!forbiddenRateNames.contains(getRateName(route.getSourceSymbol(), route.getDestSymbol()))) {
            routesWithMax2Step.add(route)
        }
    }

    private fun fetchAllSymbols(): List<String> {
        return rates
                .flatMap { rate -> listOf(rate.sourceSymbol, rate.destSymbol) }
                .filter { symbol -> !transitiveSymbols.contains(symbol) }
                .distinct()
    }

    private fun getRateName(sourceSymbol: String, destSymbol: String): String {
        return sourceSymbol + "_" + destSymbol
    }

    private fun extractPairName(rate: String): Pair<String, String> {
        val symbols = rate.split("_")
        return Pair(symbols[0], symbols[1])
    }

    fun getRates(): List<Rate> {
        return rates.toList()
    }

    fun getAvailableRoutes(): List<Route> {
        return routesWithMax2Step.toList()
    }

    fun getForbiddenNames(): List<Pair<String, String>> {
        return forbiddenRateNames.map { pair -> extractPairName(pair) }
    }

    fun getTransitiveSymbols(): List<String> {
        return transitiveSymbols.toList()
    }

    fun reset() {
        transitiveSymbols.clear()
        forbiddenRateNames.clear()
        rates.clear()
        routesWithMax2Step.clear()
    }


    suspend fun buildRoutes() {
        val adjencyMap: Map<String, MutableList<Rate>> = createAdjacencyMap()
        val systemCurrencies = currencyService.getCurrencies()?.currencies
        val vertice: List<String> = systemCurrencies?.filter { it.isTransitive == false }?.map(Currency::symbol)
                ?: throw OpexException(OpexError.NoRecordFound)
        val transitiveSymbols: List<String> = systemCurrencies?.filter { it.isTransitive == true }?.map(Currency::symbol)
                ?: throw OpexException(OpexError.NoRecordFound)
        for (vertex in vertice) {
            val visited = mutableSetOf<String>()
            findRoutesWithMax2EdgesV2(vertex, adjencyMap, visited, 0, mutableListOf(), transitiveSymbols)
        }
    }

    private suspend fun findRoutesWithMax2EdgesV2(
            currentVertex: String,
            adjacencyMap: Map<String, MutableList<Rate>>,
            visited: MutableSet<String>,
            currentLength: Int,
            currentRoute: MutableList<Rate>,
            transitiveSymbols: List<String>
    ) {

        if (currentLength == 3) {
            return
        }
        visited.add(currentVertex)
        if (currentLength >= 1) {
            if (!transitiveSymbols.contains(currentRoute.get(currentRoute.lastIndex).destSymbol)) {
                val existingRoute = routesWithMax2Step.find { route ->
                    route.getSourceSymbol() == currentRoute.get(0).sourceSymbol
                            &&
                            route.getDestSymbol() == currentRoute.get(currentRoute.lastIndex).destSymbol
                }
                if (existingRoute != null) {
                    if (existingRoute.rates.size > currentRoute.size) {
                        routesWithMax2Step.remove(existingRoute)
                        addCurrentRouteV2(currentRoute)
                    } else if (existingRoute.rates.size == currentRoute.size
                            && existingRoute.rates != currentRoute
                    ) {
                        throw Exception("Only one route should be available between two symbols")
                    }
                } else {
                    addCurrentRouteV2(currentRoute)
                }
            }
        }
        val totalEdge=adjacencyMap[currentVertex] ?: emptyList()
        val forbiddenEdge= graphService.getForbiddenPairs().forbiddenPairs
        val validEdge=totalEdge.filter {forbiddenEdge?.contains(it) ==false  }
        for (edge in adjacencyMap[currentVertex] ?: emptyList()) {

            if (!visited.contains(edge.destSymbol)) {
                currentRoute.add(edge)
                findRoutesWithMax2Edges(
                        edge.destSymbol,
                        adjacencyMap,
                        visited,
                        currentLength + 1,
                        currentRoute
                )
                currentRoute.removeAt(currentRoute.size - 1)
            }
        }
        visited.remove(currentVertex)
    }

    private suspend fun addCurrentRouteV2(currentRoute: MutableList<Rate>) {
        val route = Route(currentRoute.toList());
        graphService.getForbiddenPairs().forbiddenPairs?.stream()?.filter{
            it.sourceSymbol==route.getSourceSymbol() && it.destSymbol==route.getDestSymbol()
        }?: routesWithMax2Step.add(route)

    }

}