package co.nilin.opex.matching.gateway.ports.kafka.submitter.service

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.DescribeClusterOptions
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class KafkaHealthIndicator(private val adminClient: AdminClient) {

    private val logger = LoggerFactory.getLogger(KafkaHealthIndicator::class.java)
    private val options = DescribeClusterOptions().timeoutMs(1000)
    private val healthyNodeSize = 3
    var isHealthy = false
        protected set

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    fun check() {
        isHealthy = try {
            val description = adminClient.describeCluster(options)
            if (description.nodes().get().size < healthyNodeSize)
                throw IllegalStateException("Insufficient nodes")
            true
        } catch (e: Exception) {
            logger.warn("Kafka is not healthy!: ${e.message}")
            false
        }
    }

}
