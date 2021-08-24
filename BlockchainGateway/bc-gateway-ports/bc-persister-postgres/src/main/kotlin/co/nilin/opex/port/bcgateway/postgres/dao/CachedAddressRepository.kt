package co.nilin.opex.port.bcgateway.postgres.dao

import co.nilin.opex.port.bcgateway.postgres.model.AssignedAddressChainModel
import co.nilin.opex.port.bcgateway.postgres.model.CachedAddressModel
import co.nilin.opex.port.bcgateway.postgres.model.ChainModel
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface CachedAddressRepository : ReactiveCrudRepository<CachedAddressModel, Long> {

}