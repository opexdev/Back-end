package co.nilin.opex.referral.ports.postgres.repository

import co.nilin.opex.referral.ports.postgres.dao.Config
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ConfigRepository : ReactiveCrudRepository<Config, String>