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
) : RuntimeException(message)

class ValidationException(code: String, message: String, field: String? = null) :
    ApiException(HttpStatus.BAD_REQUEST, code, message, field)

class ConflictException(code: String, message: String, field: String? = null) :
    ApiException(HttpStatus.CONFLICT, code, message, field)

class UnauthorizedException(code: String, message: String) :
    ApiException(HttpStatus.UNAUTHORIZED, code, message)

class NotFoundException(code: String, message: String) :
    ApiException(HttpStatus.NOT_FOUND, code, message)

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val field: String? = null,
)
