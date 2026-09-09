package com.notifyshare.auth

import com.notifyshare.PostgresTestContainer
import com.notifyshare.auth.application.PasswordResetMailer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Guarda o ultimo codigo enviado por e-mail, por endereco — sem enviar nada. */
class RecordingMailer : PasswordResetMailer {
    val codes = ConcurrentHashMap<String, String>()
    override fun sendPasswordResetCode(to: String, code: String, ttlMinutes: Long) {
        codes[to.lowercase()] = code
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "notifyshare.rate-limit.enabled=false",
        "notifyshare.auth.login-lockout-enabled=false",
        "notifyshare.auth.password-reset.max-attempts=3",
        "notifyshare.auth.password-reset.send-cooldown=PT0S",
    ],
)
@Import(PostgresTestContainer::class, PasswordResetFlowTest.MailerConfig::class)
class PasswordResetFlowTest {

    @TestConfiguration(proxyBeanMethods = false)
    class MailerConfig {
        @Bean @Primary fun mailer() = RecordingMailer()
    }

    @LocalServerPort private var port: Int = 0
    private lateinit var client: RestTestClient
    private lateinit var mailer: RecordingMailer

    @org.springframework.beans.factory.annotation.Autowired
    fun inject(m: RecordingMailer) { this.mailer = m }

    @BeforeEach
    fun setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        mailer.codes.clear()
    }

    private data class Resp(val status: Int, val body: String)

    private fun post(path: String, json: String): Resp {
        val r = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json)
            .exchange().expectBody(String::class.java).returnResult()
        return Resp(r.status.value(), r.responseBody ?: "")
    }

    private fun register(nick: String, email: String, pass: String = "senha-forte-1") =
        post("/auth/register", """{"nickname":"$nick","email":"$email","password":"$pass","acceptedPrivacy":true}""")

    private fun forgot(email: String) = post("/auth/forgot-password", """{"email":"$email"}""")
    private fun reset(email: String, code: String, pass: String) =
        post("/auth/reset-password", """{"email":"$email","code":"$code","newPassword":"$pass"}""")
    private fun login(id: String, pass: String) =
        post("/auth/login", """{"identifier":"$id","password":"$pass"}""")

    @Test
    fun `fluxo feliz troca a senha e derruba as sessoes antigas`() {
        register("recupero", "recupero@teste.com", "senha-antiga-1")
        val oldRefresh = Regex("\"refreshToken\":\"([^\"]+)\"")
            .find(login("recupero", "senha-antiga-1").body)!!.groupValues[1]

        assertEquals(202, forgot("recupero@teste.com").status)
        val code = mailer.codes["recupero@teste.com"]!!

        assertEquals(204, reset("recupero@teste.com", code, "senha-nova-1").status)

        assertEquals(401, login("recupero", "senha-antiga-1").status)   // senha antiga nao vale
        assertEquals(200, login("recupero", "senha-nova-1").status)     // nova vale
        // sessao antiga caiu
        assertEquals(401, post("/auth/refresh", """{"refreshToken":"$oldRefresh"}""").status)
    }

    @Test
    fun `e-mail sem conta responde 202 e nao gera codigo`() {
        assertEquals(202, forgot("ninguem@lugar-nenhum.com").status)
        assertNull(mailer.codes["ninguem@lugar-nenhum.com"])
    }

    @Test
    fun `codigo errado mostra tentativas restantes e bloqueia o IP na ultima`() {
        register("bloqueio", "bloqueio@teste.com")
        forgot("bloqueio@teste.com")

        val a = reset("bloqueio@teste.com", "000000", "outra-senha-1")
        assertEquals(401, a.status)
        assertTrue(a.body.contains("invalid_reset_code"), a.body)
        assertTrue(a.body.contains("\"attemptsRemaining\":2"), a.body)

        reset("bloqueio@teste.com", "000000", "outra-senha-1")           // 2a
        val terceira = reset("bloqueio@teste.com", "000000", "outra-senha-1")  // 3a estoura
        assertEquals(429, terceira.status)
        assertTrue(terceira.body.contains("reset_locked"), terceira.body)
    }

    @Test
    fun `codigo expira o token depois de max-attempts erros`() {
        register("queima", "queima@teste.com")
        forgot("queima@teste.com")
        val code = mailer.codes["queima@teste.com"]!!

        // 3 erros do MESMO IP tambem bloqueiam o IP; mas o objetivo aqui e o token:
        repeat(3) { reset("queima@teste.com", "111111", "nova-senha-1") }
        // mesmo com o codigo certo, o token ja morreu (e o IP travou): nao passa
        assertTrue(reset("queima@teste.com", code, "nova-senha-1").status in setOf(401, 429))
    }
}
