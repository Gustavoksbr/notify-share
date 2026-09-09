package com.notifyshare.auth.adapter.email

import com.notifyshare.auth.application.PasswordResetMailer
import com.notifyshare.auth.application.ResendProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Envio de e-mail transacional pelo Resend (https://resend.com/docs/api-reference).
 *
 * `java.net.http.HttpClient` direto, sem dependencia nova — mesmo padrao do
 * GoogleTokenVerifier. Falha de envio nao derruba a requisicao: o endpoint de
 * "esqueci a senha" sempre responde 202 (nao pode virar um oraculo de quais
 * e-mails tem conta), entao aqui so logamos.
 *
 * Sem RESEND_API_KEY/RESEND_EMAIL_FROM (dev): nao envia nada, escreve o codigo
 * no log — util pra testar o fluxo local.
 */
@Component
class ResendMailSender(private val props: ResendProperties) : PasswordResetMailer {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override fun sendPasswordResetCode(to: String, code: String, ttlMinutes: Long) {
        if (!props.enabled) {
            log.warn("[Resend desligado] codigo de recuperacao para {}: {}", to, code)
            return
        }
        val body = """
            {"from":${jsonStr(props.from)},"to":[${jsonStr(to)}],
             "subject":"Notify Share — código para redefinir a senha",
             "text":${jsonStr(plainText(code, ttlMinutes))},
             "html":${jsonStr(html(code, ttlMinutes))}}
        """.trimIndent().replace("\n", "")

        val request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(12))
            .header("Authorization", "Bearer ${props.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        runCatching {
            val res = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() !in 200..299) {
                log.warn("Resend recusou o e-mail para {}: HTTP {} {}", to, res.statusCode(), res.body())
            } else {
                log.info("E-mail de recuperacao enviado para {}", to)
            }
        }.onFailure { log.warn("Falha ao chamar o Resend para {}: {}", to, it.message) }
    }

    private fun plainText(code: String, ttl: Long) =
        "Seu código para redefinir a senha do Notify Share é $code. " +
            "Ele vale por $ttl minutos. Se você não pediu isso, ignore este e-mail."

    private fun html(code: String, ttl: Long) = """
        <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:420px;margin:0 auto">
          <h2 style="color:#8a5a2e">Redefinir a senha</h2>
          <p>Use este código no app para escolher uma senha nova:</p>
          <p style="font-size:32px;font-weight:700;letter-spacing:6px;background:#f6efe6;
                    padding:14px 0;text-align:center;border-radius:10px">$code</p>
          <p style="color:#666">Vale por $ttl minutos. Se você não pediu isso, é só ignorar — nada muda.</p>
        </div>
    """.trimIndent()

    /** Escape minimo de string JSON (aspas, barra, controles). */
    private fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        return sb.append("\"").toString()
    }
}
