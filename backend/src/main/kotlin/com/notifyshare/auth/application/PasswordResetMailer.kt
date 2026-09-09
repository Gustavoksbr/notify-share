package com.notifyshare.auth.application

/** Porta de envio do codigo de recuperacao. Impl. de producao: ResendMailSender. */
interface PasswordResetMailer {
    fun sendPasswordResetCode(to: String, code: String, ttlMinutes: Long)
}
