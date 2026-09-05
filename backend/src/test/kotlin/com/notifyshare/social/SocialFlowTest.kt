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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Amizade -> grant -> regras, contra o servidor e o Postgres de verdade.
 * O push fica desligado: aqui interessa a maquina de estados, nao o FCM.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["notifyshare.fcm.enabled=false", "notifyshare.rate-limit.enabled=false", "notifyshare.auth.login-lockout-enabled=false"],
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

    private fun delete(path: String, token: String): Res {
        val result = client.delete().uri(path)
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
            json = """{"nickname":"$nick","email":"$nick@teste.com","password":"senha-forte-123","acceptedPrivacy":true}""",
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
    fun `pedir amizade ja pedindo compartilhamento cria o grant ao aceitar`() {
        val a = register("c3req_a"); val b = register("c3req_b")

        // A pede amizade e ja marca que quer receber as notificacoes de B
        assertEquals(
            200,
            post("/friends/requests", a, """{"nickname":"c3req_b","alsoRequestShare":true}""").status,
        )
        // enquanto pendente, nenhum grant existe
        assertFalse(get("/grants/pending", a).body.contains("c3req_b"), get("/grants/pending", a).body)

        val reqId = field(get("/friends/requests", b).body, "id")
        assertEquals(204, post("/friends/requests/$reqId/accept", b).status)

        // agora A tem um pedido de compartilhamento pendente com B (A iniciou)
        val pendingA = get("/grants/pending", a).body
        assertTrue(pendingA.contains("c3req_b") && pendingA.contains("\"outgoing\""), pendingA)
        // e do lado de B aparece como recebido
        assertTrue(get("/grants/pending", b).body.contains("c3req_a"), get("/grants/pending", b).body)
    }

    @Test
    fun `cancelar pedido de amizade leva junto a intencao de compartilhar`() {
        val a = register("c3canc_a"); val b = register("c3canc_b")

        assertEquals(
            200,
            post("/friends/requests", a, """{"nickname":"c3canc_b","alsoRequestShare":true}""").status,
        )
        // o proprio B nao pode cancelar o pedido de A
        val reqId = field(get("/friends/requests", b).body, "id")
        assertEquals(403, post("/friends/requests/$reqId/cancel", b).status)

        // A cancela o proprio pedido
        assertEquals(204, post("/friends/requests/$reqId/cancel", a).status)

        // sumiu dos pedidos e nenhum grant nasce se B "aceitar" (nem da mais)
        assertFalse(get("/friends/requests", b).body.contains("c3canc_a"), get("/friends/requests", b).body)
        assertEquals(404, post("/friends/requests/$reqId/accept", b).status)
    }

    @Test
    fun `desfazer amizade derruba os grants entre os dois`() {
        val a = register("unf_a"); val b = register("unf_b")
        befriend(a, b, "unf_b")
        assertEquals(200, post("/grants/offers", a, """{"nickname":"unf_b"}""").status)
        assertTrue(get("/grants/pending", b).body.contains("unf_a"))

        assertEquals(204, delete("/friends/unf_b", a).status)

        assertEquals("""{"incoming":[],"outgoing":[]}""", get("/grants/pending", a).body)
        assertEquals("""{"incoming":[],"outgoing":[]}""", get("/grants/pending", b).body)
    }

    @Test
    fun `pedir amizade oferecendo e pedindo cria os dois grants`() {
        val a = register("c3both_a"); val b = register("c3both_b")

        assertEquals(
            200,
            post(
                "/friends/requests", a,
                """{"nickname":"c3both_b","alsoOfferShare":true,"alsoRequestShare":true}""",
            ).status,
        )
        val reqId = field(get("/friends/requests", b).body, "id")
        assertEquals(204, post("/friends/requests/$reqId/accept", b).status)

        // A oferece (sharer) e pede (recipient): os dois grants pendentes
        val pendingA = get("/grants/pending", a).body
        assertTrue(pendingA.contains("\"role\":\"sharer\""), pendingA)
        assertTrue(pendingA.contains("\"role\":\"recipient\""), pendingA)
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
    fun `quem iniciou pode cancelar o proprio pedido pendente`() {
        val a = register("canc_a"); val b = register("canc_b")
        befriend(a, b, "canc_b")

        // b pede para receber de a
        val gid = field(post("/grants/requests", b, """{"nickname":"canc_a"}""").body, "id")
        assertTrue(get("/grants/pending", a).body.contains(gid), "pedido devia estar na caixa de a")

        // b (quem iniciou) cancela
        assertEquals(204, post("/grants/$gid/decline", b).status)
        assertFalse(get("/grants/pending", a).body.contains(gid), "pedido devia ter sumido")

        // cancelar de novo: a linha ja nao existe
        assertEquals(404, post("/grants/$gid/decline", b).status)
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
