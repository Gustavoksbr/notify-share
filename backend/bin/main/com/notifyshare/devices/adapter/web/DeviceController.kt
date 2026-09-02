package com.notifyshare.devices.adapter.web

import com.notifyshare.devices.application.DeviceService
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Schema(description = "Registro de um aparelho para receber push")
data class RegisterDeviceRequest(
    @field:NotBlank
    @field:Schema(description = "Token de registro do FCM, obtido no app.")
    val fcmToken: String = "",

    @field:Schema(example = "android")
    val platform: String? = null,

    @field:Schema(example = "Redmi 12C")
    val deviceLabel: String? = null,
)

data class UnregisterDeviceRequest(
    @field:NotBlank val fcmToken: String = "",
)

@RestController
@RequestMapping("/devices")
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Aparelhos", description = "Token do FCM e presenca")
class DeviceController(private val devices: DeviceService) {

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Registra ou atualiza o aparelho atual",
        description = "Idempotente. O app chama depois do login e sempre que o FCM " +
            "rotaciona o token (onNewToken).",
    )
    fun register(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody body: RegisterDeviceRequest,
    ) {
        devices.register(me.id, body.fcmToken, body.platform, body.deviceLabel)
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sinal de vida", description = "Atualiza a presenca de todos os aparelhos do usuario.")
    fun heartbeat(@AuthenticationPrincipal me: AuthenticatedUser) {
        devices.heartbeat(me.id)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Descadastra o aparelho", description = "Chamado no logout para parar o push.")
    fun unregister(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody body: UnregisterDeviceRequest,
    ) {
        devices.unregister(me.id, body.fcmToken)
    }
}
