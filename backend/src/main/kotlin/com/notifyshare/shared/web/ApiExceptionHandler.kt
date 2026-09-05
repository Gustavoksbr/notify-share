package com.notifyshare.shared.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(e.status)
            .body(ApiErrorResponse(e.code, e.message, e.field, e.attemptsRemaining, e.retryAfterSeconds))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val first = e.bindingResult.fieldErrors.firstOrNull()
        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                code = "validation_error",
                message = first?.defaultMessage ?: "Requisicao invalida",
                field = first?.field,
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiErrorResponse> {
        log.error("Erro nao tratado", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse("internal_error", "Algo deu errado do nosso lado")
        )
    }
}
