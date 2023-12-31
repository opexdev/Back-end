package co.nilin.opex.bcgateway.app.utils

import co.nilin.opex.utility.error.data.OpexError
import co.nilin.opex.utility.error.data.OpexException
import com.nimbusds.jose.shaded.json.JSONArray
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt

fun ServerHttpSecurity.AuthorizeExchangeSpec.Access.hasRole(
        authority: String,
        role: String
): ServerHttpSecurity.AuthorizeExchangeSpec = access { mono, _ ->
    mono.map { auth ->
        val hasAuthority = auth.authorities.any { it.authority == authority }
        val hasRole = ((auth.principal as Jwt).claims["roles"] as JSONArray?)?.contains(role) == true
        AuthorizationDecision(hasAuthority && hasRole)
    }
}

fun ServerHttpSecurity.AuthorizeExchangeSpec.Access.hasRoles(
        roles: List<String>
): ServerHttpSecurity.AuthorizeExchangeSpec = access { mono, _ ->
    mono.map { auth ->
        roles.forEach { r ->
            if (((auth.principal as Jwt).claims["roles"] as JSONArray?)?.contains(r) != true)
                throw OpexException(OpexError.Forbidden)
        }
        AuthorizationDecision(true)
    }
}