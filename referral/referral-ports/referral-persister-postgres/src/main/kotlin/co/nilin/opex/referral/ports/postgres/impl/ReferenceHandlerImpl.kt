package co.nilin.opex.referral.ports.postgres.impl

import co.nilin.opex.referral.core.model.Reference
import co.nilin.opex.referral.core.model.ReferralCode
import co.nilin.opex.referral.core.spi.ReferenceHandler
import co.nilin.opex.referral.ports.postgres.repository.ReferenceRepository
import co.nilin.opex.referral.ports.postgres.repository.ReferralCodeRepository
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.awaitSingleOrNull
import org.springframework.stereotype.Service

@Service
class ReferenceHandlerImpl(
    private val referralCodeRepository: ReferralCodeRepository,
    private val referenceRepository: ReferenceRepository
) : ReferenceHandler {
    override suspend fun findAll(): List<Reference> {
        val refs = referenceRepository.findAll().collectList().awaitSingle()
        return refs.map { ref ->
            val referralCode = referralCodeRepository.findById(ref.referralCodeId).map {
                ReferralCode(it.uuid, it.code, it.referentCommission)
            }.awaitSingle()
            Reference(referralCode, ref.uuid)
        }
    }

    override suspend fun findByUuid(uuid: String): Reference? {
        val ref = referenceRepository.findByUuid(uuid).awaitSingleOrNull() ?: return null
        val referralCode = referralCodeRepository.findById(ref.referralCodeId).map {
            ReferralCode(it.uuid, it.code, it.referentCommission)
        }.awaitSingle()
        return Reference(referralCode, ref.uuid)
    }

    override suspend fun findByCode(code: String): Reference? {
        val ref = referenceRepository.findByCode(code).awaitSingle() ?: return null
        val referralCode = referralCodeRepository.findById(ref.referralCodeId).map {
            ReferralCode(it.uuid, it.code, it.referentCommission)
        }.awaitSingle()
        return Reference(referralCode, ref.uuid)
    }
}
