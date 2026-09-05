package com.notifyshare.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val nickname: String,
    val email: String,
    val password: String,
    val acceptedPrivacy: Boolean = false,
    val deviceLabel: String? = null,
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
    val deviceLabel: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
    val deviceLabel: String? = null,
)

@Serializable
data class GoogleLoginRequest(
    val idToken: String,
    val nickname: String? = null,
    val acceptedPrivacy: Boolean = false,
    val deviceLabel: String? = null,
)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class UserDto(
    val id: String,
    val nickname: String,
    val email: String,
    val createdAt: String,
    val google: Boolean = false,
    val hasPassword: Boolean = true,
    val privacyAccepted: Boolean = true,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto,
)

/**
 * Formato unico de erro do backend. O `code` e o contrato estavel — a tela
 * decide o que fazer por ele, nunca pela `message`, que pode mudar.
 */
@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    val field: String? = null,
)
