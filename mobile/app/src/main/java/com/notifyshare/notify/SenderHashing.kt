package com.notifyshare.notify

import java.security.MessageDigest

/**
 * Hash do nome do remetente. sender_hash, nao sender_name: o servidor filtra por
 * igualdade sem nunca saber que a pessoa se chama Jose. O nome legivel so vive
 * no aparelho (dentro do `content`, opaco para o servidor).
 */
fun hashSender(name: String): String {
    val normalized = name.trim().lowercase()
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
