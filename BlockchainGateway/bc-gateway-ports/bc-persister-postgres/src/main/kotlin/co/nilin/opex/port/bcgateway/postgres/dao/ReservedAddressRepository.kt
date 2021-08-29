package co.nilin.opex.port.bcgateway.postgres.dao

import co.nilin.opex.port.bcgateway.postgres.model.ReservedAddressModel
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface ReservedAddressRepository : ReactiveCrudRepository<ReservedAddressModel, Long> {

}