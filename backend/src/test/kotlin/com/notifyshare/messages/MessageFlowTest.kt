package com.notifyshare.messages

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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["notifyshare.fcm.enabled=false", "notifyshare.rate-limit.enabled=false", "notifyshare.auth.login-lockout-enabled=false"],
)
@Import(PostgresTestContainer::class)
class MessageFlowTest {

    @LocalServerPort
    private var port: Int = 0
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

    private fun patch(path: String, token: String, json: String): Res {
        val r = client.patch().uri(path).header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).body(json)
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun delete(path: String, token: String): Res {
        val r = client.delete().uri(path).header("Authorization", "Bearer $token")
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun befriend(a: String, b: String, bNick: String) {
        post("/friends/requests", a, """{"nickname":"$bNick"}""")
        val rid = field(get("/friends/requests", b).body, "id")
        post("/friends/requests/$rid/accept", b)
    }

    private fun field(body: String, name: String) =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: error("sem $name em $body")

    private fun register(nick: String) =
        field(post("/auth/register", json = """{"nickname":"$nick","email":"$nick@t.com","password":"senha-forte-123","acceptedPrivacy":true}""").body, "accessToken")

    @Test
    fun `mensagem exige amizade`() {
        val a = register("msg_a"); register("msg_b")
        val blocked = post("/conversations/msg_b/messages", a, """{"body":"oi"}""")
        assertEquals(403, blocked.status)
        assertTrue(blocked.body.contains("not_friends"), blocked.body)
    }

    @Test
    fun `timeline junta mensagem e auditoria de grant`() {
        val a = register("tl_a"); val b = register("tl_b")
        post("/friends/requests", a, """{"nickname":"tl_b"}""")
        val rid = field(get("/friends/requests", b).body, "id")
        post("/friends/requests/$rid/accept", b)

        val gid = field(post("/grants/offers", a, """{"nickname":"tl_b"}""").body, "id")
        post("/grants/$gid/accept", b)
        post("/conversations/tl_b/messages", a, """{"body":"consegue liberar o Telegram?"}""")

        val timeline = get("/conversations/tl_b", a)
        assertEquals(200, timeline.status)
        assertTrue(timeline.body.contains("\"kind\":\"message\""), timeline.body)
        assertTrue(timeline.body.contains("\"kind\":\"audit\""), timeline.body)
        assertTrue(timeline.body.contains("\"action\":\"activated\""), timeline.body)
    }

    @Test
    fun `editar mensagem marca como editada e so o autor edita`() {
        val a = register("ed_a"); val b = register("ed_b")
        befriend(a, b, "ed_b")
        val mid = field(post("/conversations/ed_b/messages", a, """{"body":"oii"}""").body, "id")

        assertEquals(403, patch("/conversations/messages/$mid", b, """{"body":"invadindo"}""").status)

        val edited = patch("/conversations/messages/$mid", a, """{"body":"oi, tudo bem?"}""")
        assertEquals(200, edited.status)
        assertTrue(edited.body.contains("\"edited\":true"), edited.body)

        val tl = get("/conversations/ed_b", a).body
        assertTrue(tl.contains("oi, tudo bem?") && tl.contains("\"edited\":true"), tl)
        assertFalse(tl.contains("\"oii\""), tl)
    }

    @Test
    fun `apagar mensagem vira mensagem apagada`() {
        val a = register("del_a"); val b = register("del_b")
        befriend(a, b, "del_b")
        val mid = field(post("/conversations/del_b/messages", a, """{"body":"segredo"}""").body, "id")

        assertEquals(403, delete("/conversations/messages/$mid", b).status)
        assertEquals(204, delete("/conversations/messages/$mid", a).status)
        assertEquals(204, delete("/conversations/messages/$mid", a).status) // idempotente

        val tl = get("/conversations/del_a", b).body
        assertTrue(tl.contains("\"deleted\":true"), tl)
        assertFalse(tl.contains("segredo"), tl)
    }

    @Test
    fun `responder vincula a mensagem anterior da mesma conversa`() {
        val a = register("rp_a"); val b = register("rp_b"); val c = register("rp_c")
        befriend(a, b, "rp_b")
        befriend(a, c, "rp_c")
        val first = field(post("/conversations/rp_b/messages", a, """{"body":"pergunta"}""").body, "id")

        // responder de outra conversa: recusado
        assertEquals(400, post("/conversations/rp_c/messages", a, """{"body":"x","replyToId":"$first"}""").status)

        assertEquals(200, post("/conversations/rp_a/messages", b, """{"body":"resposta","replyToId":"$first"}""").status)
        val tl = get("/conversations/rp_b", a).body
        assertTrue(tl.contains("\"replyTo\"") && tl.contains("pergunta"), tl)
    }

    @Test
    fun `marcar como lida zera o contador do outro lado`() {
        val a = register("rd_a"); val b = register("rd_b")
        post("/friends/requests", a, """{"nickname":"rd_b"}""")
        val rid = field(get("/friends/requests", b).body, "id")
        post("/friends/requests/$rid/accept", b)

        post("/conversations/rd_b/messages", a, """{"body":"oi"}""")
        assertTrue(get("/conversations/unread-count", b).body.contains("\"count\":1"))

        post("/conversations/rd_a/read", b)
        assertTrue(get("/conversations/unread-count", b).body.contains("\"count\":0"))
    }
}
