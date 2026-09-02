package com.notifyshare.social

import com.notifyshare.PostgresTestContainer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Amizade -> grant -> regras, contra o servidor e o Postgres de verdade.
 * O push fica desligado: aqui interessa a maquina de estados, nao o FCM.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["notifyshare.fcm.enabled=false"],
)
@Import(PostgresTestContainer::class)
class SocialFlowTest {

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @BeforeEach
    fun setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    // --- helpers --------------------------------------------------------

    private data class Res(val status: Int, val body: String)

    private fun post(path: String, token: String? = null, json: String? = null): Res {
        val spec = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
        token?.let { spec.header("Authorization", "Bearer $it") }
        val result = (if (json != null) spec.body(json) else spec)
            .exchange().expectBody(String::class.java).returnResult()
        return Res(result.status.value(), result.responseBody ?: "")
    }

    private fun put(path: String, token: String, json: String): Res {
        val result = client.put().uri(path)
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange().expectBody(String::class.java).returnResult()
        return Res(result.status.value(), result.responseBody ?: "")
    }

    private fun get(path: String, token: String): Res {
        val result = client.get().uri(path)
            .header("Authorization", "Bearer $token")
            .exchange().expectBody(String::class.java).returnResult()
        return Res(result.status.value(), result.responseBody ?: "")
    }

    private fun field(body: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: error("campo $name ausente em: $body")

    private fun register(nick: String): String {
        val body = post(
            "/auth/register",
            json = """{"nickname":"$nick","email":"$nick@teste.com","password":"senha-forte-123"}""",
        ).body
        return field(body, "accessToken")
    }

    private fun befriend(aToken: String, bToken: String, bNick: String) {
        post("/friends/requests", aToken, """{"nickname":"$bNick"}""")
        val requestId = field(get("/friends/requests", bToken).body, "id")
        assertEquals(204, post("/friends/requests/$requestId/accept", bToken).status)
    }

    // --- amizade -------------------------------------------------------

    @Test
    fun `pedido de amizade reverso e aceito na hora`() {
        val a = register("amirev_a"); val b = register("amirev_b")

        assertEquals(200, post("/friends/requests", a, """{"nickname":"amirev_b"}""").status)
        val reverse = post("/friends/requests", b, """{"nickname":"amirev_a"}""")

        assertEquals(200, reverse.status)
        assertTrue(reverse.body.contains("\"relation\":\"friend\""), reverse.body)
        assertTrue(get("/friends", a).body.contains("amirev_b"))
    }

    @Test
    fun `busca mostra a relacao atual`() {
        val a = register("busca_a"); register("busca_alvo")
        post("/friends/requests", a, """{"nickname":"busca_alvo"}""")

        val hit = get("/users/search?q=busca_alvo", a)
        assertTrue(hit.body.contains("\"relation\":\"request_sent\""), hit.body)
    }

    // --- grant --------------------------------------------------------

    @Test
    fun `grant so entre amigos`() {
        val a = register("grant_a"); register("grant_b")
        val blocked = post("/grants/offers", a, """{"nickname":"grant_b"}""")
        assertEquals(403, blocked.status)
        assertTrue(blocked.body.contains("not_friends"), blocked.body)
    }

    @Test
    fun `oferta para quem ja pediu ativa direto`() {
        val a = register("match_a"); val b = register("match_b")
        befriend(a, b, "match_b")

        // b pede para receber de a
        assertEquals(200, post("/grants/requests", b, """{"nickname":"match_a"}""").status)
        // a oferece para b: casa com o pedido e ativa
        val activated = post("/grants/offers", a, """{"nickname":"match_b"}""")
        assertTrue(activated.body.contains("\"status\":\"active\""), activated.body)
    }

    @Test
    fun `so quem pausou retoma`() {
        val a = register("pausa_a"); val b = register("pausa_b")
        befriend(a, b, "pausa_b")
        val grantId = field(post("/grants/offers", a, """{"nickname":"pausa_b"}""").body, "id")
        post("/grants/$grantId/accept", b)

        assertEquals(200, post("/grants/$grantId/pause", b).status)          // b pausa
        assertEquals(403, post("/grants/$grantId/resume", a).status)         // a nao pode retomar
        assertEquals(200, post("/grants/$grantId/resume", b).status)         // b pode
    }

    // --- regras ------------------------------------------------------

    @Test
    fun `so o sharer edita regras, o recipient so le`() {
        val a = register("regra_a"); val b = register("regra_b")
        befriend(a, b, "regra_b")
        val grantId = field(post("/grants/offers", a, """{"nickname":"regra_b"}""").body, "id")
        post("/grants/$grantId/accept", b)

        val payload = """{"rules":[{"packageName":"com.whatsapp","allSenders":false,
            "senders":[{"senderHash":"h1","senderLabel":"Jose"}]}]}"""
        assertEquals(403, put("/grants/$grantId/rules", b, payload).status)
        assertEquals(200, put("/grants/$grantId/rules", a, payload).status)

        val asRecipient = get("/grants/$grantId/rules", b)
        assertEquals(200, asRecipient.status)
        assertTrue(asRecipient.body.contains("com.whatsapp"), asRecipient.body)
        assertTrue(asRecipient.body.contains("h1"))
    }
}
