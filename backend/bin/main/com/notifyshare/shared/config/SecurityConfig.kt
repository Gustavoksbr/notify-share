package com.notifyshare.shared.config

import com.notifyshare.auth.adapter.web.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(private val jwtFilter: JwtAuthenticationFilter) {

    /**
     * Delegating encoder: grava o hash com o prefixo do algoritmo ({bcrypt}...),
     * o que permite trocar para Argon2 depois sem invalidar as senhas antigas.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // API sem cookie de sessao: nao ha o que um CSRF possa sequestrar.
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // /auth/logout e publico de proposito: apresentar o refresh token
                // ja e prova de posse suficiente para revogar aquele token. Exigir
                // access token aqui impediria sair da conta depois que ele expira.
                //
                // /auth/logout-all continua autenticado: derrubar TODAS as sessoes
                // e uma acao sobre a conta inteira, nao sobre um aparelho.
                it.requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/auth/logout")
                    .permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    // O handshake do WebSocket se autentica sozinho pelo ?token=
                    // (WsHandshakeInterceptor). Aqui so liberamos a rota do filtro HTTP.
                    .requestMatchers("/ws").permitAll()
                    // Swagger. Em producao isso sai do ar por SWAGGER_ENABLED=false,
                    // e ai estas rotas simplesmente nao existem.
                    .requestMatchers("/swagger", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { handling ->
                // JSON escrito na mao: o corpo e fixo, e assim a configuracao de
                // seguranca nao depende de qual Jackson esta no classpath.
                handling.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpStatus.UNAUTHORIZED.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = Charsets.UTF_8.name()
                    response.writer.write(
                        """{"code":"unauthenticated","message":"Faca login para continuar"}"""
                    )
                }
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
