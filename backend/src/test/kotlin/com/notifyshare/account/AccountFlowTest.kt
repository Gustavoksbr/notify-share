package com.notifyshare.account

import com.notifyshare.PostgresTestContainer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Consentimento LGPD no cadastro, exportacao e exclusao de conta com cascata. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["notifyshare.fcm.enabled=false", "notifyshare.rate-limit.enabled=false"],
)
@Import(PostgresTestContainer::class)
class AccountFlowTest {

    @LocalServerPort private var port: Int = 0
    private lateinit var client: RestTestClient

    @BeforeEach
    fun setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    private data class Res(val status: Int, val body: String)

    private fun post(path: String, token: String? = null, json: String? = null): Res {
        val spec = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
        token?.let { spec.header("Authorization", "Bearer $it") }
        val r = (if (json != null) spec.body(json) else spec)
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun get(path: String, token: String): Res {
        val r = client.get().uri(path).header("Authorization", "Bearer $token")
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun delete(path: String, token: String): Res {
        val r = client.delete().uri(path).header("Authorization", "Bearer $token")
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun field(body: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: error("sem $name em $body")

    private fun register(nick: String, accept: Boolean = true): Res = post(
        "/auth/register",
        json = """{"nickname":"$nick","email":"$nick@t.com","password":"senha-forte-123","acceptedPrivacy":$accept}""",
    )

    @Test
    fun `cadastro sem aceitar a privacidade e recusado`() {
        val r = register("acc_noconsent", accept = false)
        assertEquals(400, r.status)
        assertTrue(r.body.contains("privacy_not_accepted"), r.body)
    }

    @Test
    fun `cadastro com aceite grava privacyAccepted`() {
        val token = field(register("acc_consent").body, "accessToken")
        assertTrue(get("/me", token).body.contains("\"privacyAccepted\":true"))
    }

    @Test
    fun `export traz o perfil e o que a pessoa compartilhou`() {
        val a = field(register("acc_exp_a").body, "accessToken")
        val b = field(register("acc_exp_b").body, "accessToken")
        post("/friends/requests", a, """{"nickname":"acc_exp_b"}""")
        val rid = field(get("/friends/requests", b).body, "id")
        post("/friends/requests/$rid/accept", b)

        val export = get("/me/export", a)
        assertEquals(200, export.status)
        assertTrue(export.body.contains("acc_exp_a@t.com"), export.body)
        assertTrue(export.body.contains("acc_exp_b"), export.body)
    }

    @Test
    fun `excluir conta apaga tudo e nao quebra o amigo`() {
        val a = field(register("acc_del_a").body, "accessToken")
        val b = field(register("acc_del_b").body, "accessToken")
        post("/friends/requests", a, """{"nickname":"acc_del_b"}""")
        val rid = field(get("/friends/requests", b).body, "id")
        post("/friends/requests/$rid/accept", b)
        val gid = field(post("/grants/offers", a, """{"nickname":"acc_del_b"}""").body, "id")
        post("/grants/$gid/accept", b)
        post("/conversations/acc_del_b/messages", a, """{"body":"oi b"}""")

        assertEquals(204, delete("/me", a).status)

        // a conta de a sumiu (o JWT ainda e "valido" ate expirar, mas nao ha usuario)
        assertTrue(get("/me", a).status in listOf(401, 404))
        // b continua funcionando, sem o grant e sem a conversa
        assertEquals(200, get("/me", b).status)
        assertFalse(get("/grants?role=sharer", b).body.contains("acc_del_a"))
        assertFalse(get("/grants?role=recipient", b).body.contains("acc_del_a"))
    }
}
