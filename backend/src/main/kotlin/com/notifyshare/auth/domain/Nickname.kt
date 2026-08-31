package com.notifyshare.auth.domain

/**
 * Regras do identificador publico.
 *
 * Sempre normalizado para minusculas antes de gravar, entao "Gustavo" e
 * "gustavo" sao a mesma conta e ninguem consegue se passar por outra pessoa
 * so trocando a caixa das letras.
 */
object Nickname {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    /** Comeca e termina com letra ou numero; no meio aceita ponto e underscore. */
    private val SHAPE = Regex("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$")

    private val RESERVED = setOf(
        "admin", "api", "auth", "me", "notifyshare", "notify", "share",
        "root", "support", "suporte", "system", "sistema", "null", "undefined",
    )

    fun normalize(raw: String): String = raw.trim().lowercase()

    /** Retorna null quando valido, ou o motivo da recusa. */
    fun validate(normalized: String): String? = when {
        normalized.length < MIN_LENGTH -> "Nickname precisa de pelo menos $MIN_LENGTH caracteres"
        normalized.length > MAX_LENGTH -> "Nickname pode ter no maximo $MAX_LENGTH caracteres"
        !SHAPE.matches(normalized) ->
            "Nickname aceita apenas letras, numeros, ponto e underscore, e precisa comecar e terminar com letra ou numero"
        normalized.contains("..") -> "Nickname nao pode ter dois pontos seguidos"
        normalized.contains("__") -> "Nickname nao pode ter dois underscores seguidos"
        normalized in RESERVED -> "Esse nickname e reservado"
        else -> null
    }
}
