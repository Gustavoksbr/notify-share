package com.notifyshare.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun notifyShareOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Notify Share API")
                .version("v1")
                .description(
                    """
                    API do Notify Share.

                    **Como autenticar aqui:** chame `POST /auth/register` ou `POST /auth/login`,
                    copie o `accessToken` da resposta e cole no botao **Authorize** no topo
                    da pagina. Sem o prefixo `Bearer` — o Swagger acrescenta sozinho.

                    O `accessToken` dura 15 minutos. Quando comecar a dar 401, use
                    `POST /auth/refresh` com o `refreshToken` e autorize de novo com o
                    token novo.

                    **Atencao com o refresh:** cada refresh invalida o anterior. Se voce
                    reenviar um refreshToken ja usado, o servidor entende como vazamento e
                    derruba todas as sessoes daquele login. E o comportamento esperado,
                    nao um bug.
                    """.trimIndent()
                )
        )
        .addServersItem(Server().url("/").description("Este servidor"))
        .components(
            Components().addSecuritySchemes(
                BEARER_SCHEME,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Cole apenas o accessToken, sem escrever 'Bearer' na frente."),
            )
        )

    companion object {
        const val BEARER_SCHEME = "bearer-jwt"
    }
}
