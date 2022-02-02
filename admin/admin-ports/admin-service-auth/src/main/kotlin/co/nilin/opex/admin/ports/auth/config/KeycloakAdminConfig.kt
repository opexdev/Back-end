package co.nilin.opex.admin.ports.auth.config

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KeycloakAdminConfig {

    @Value("\${app.auth.keycloak.url}")
    private lateinit var url: String

    @Value("\${app.auth.keycloak.realm}")
    private lateinit var realm: String

    @Value("\${app.auth.keycloak.client-id}")
    private lateinit var clientId: String

    @Value("\${app.auth.keycloak.client-secret}")
    private lateinit var clientSecret: String

    @Bean
    fun keycloak(): Keycloak {
        return KeycloakBuilder.builder()
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .serverUrl(url)
            .realm(realm)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build()
    }

    @Bean
    fun opexRealm(keycloak: Keycloak): RealmResource {
        return keycloak.realm(realm)
    }

}