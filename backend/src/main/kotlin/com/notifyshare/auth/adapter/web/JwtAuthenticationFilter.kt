package com.notifyshare.auth.adapter.web

import com.notifyshare.auth.adapter.token.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** Identidade autenticada disponivel nos controllers via @AuthenticationPrincipal. */
data class AuthenticatedUser(val id: UUID, val nickname: String)

@Component
class JwtAuthenticationFilter(private val jwt: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith(BEARER) && SecurityContextHolder.getContext().authentication == null) {
            val claims = jwt.parseAccessToken(header.substring(BEARER.length))
            if (claims != null) {
                val principal = AuthenticatedUser(
                    id = UUID.fromString(claims.subject),
                    nickname = claims["nickname"] as? String ?: "",
                )
                val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val BEARER = "Bearer "
    }
}
