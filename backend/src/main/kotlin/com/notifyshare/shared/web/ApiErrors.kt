package com.notifyshare.shared.web

import org.springframework.http.HttpStatus

/**
 * Erro de negocio com codigo estavel. O mobile decide o que mostrar pelo
 * `code`, nunca pela `message` — a mensagem pode mudar, o codigo nao.
 */
open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val field: String? = null,
    // So preenchido por UnauthorizedException("invalid_credentials", ...) vindo
    // do LoginAttemptGuard: quantas tentativas erradas ainda restam antes do
    // bloqueio temporario da conta.
    val attemptsRemaining: Int? = null,
    // So preenchido por TooManyRequestsException("account_locked", ...):
    // quanto falta, em segundos, para o bloqueio acabar.
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)

class ValidationException(code: String, message: String, field: String? = null) :
    ApiException(HttpStatus.BAD_REQUEST, code, message, field)

class ConflictException(code: String, message: String, field: String? = null) :
    ApiException(HttpStatus.CONFLICT, code, message, field)

class UnauthorizedException(code: String, message: String, attemptsRemaining: Int? = null) :
    ApiException(HttpStatus.UNAUTHORIZED, code, message, attemptsRemaining = attemptsRemaining)

class ForbiddenException(code: String, message: String) :
    ApiException(HttpStatus.FORBIDDEN, code, message)

class NotFoundException(code: String, message: String) :
    ApiException(HttpStatus.NOT_FOUND, code, message)

class TooManyRequestsException(code: String, message: String, retryAfterSeconds: Long? = null) :
    ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message, retryAfterSeconds = retryAfterSeconds)

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val field: String? = null,
    val attemptsRemaining: Int? = null,
    val retryAfterSeconds: Long? = null,
)
