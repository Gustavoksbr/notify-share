package com.notifyshare.blocks

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
 * Bloqueio: barra mensagem nos dois sentidos, com codigos distintos, e quem foi
 * bloqueado descobre ao tentar enviar. Desbloquear restaura. O perfil reflete.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["notifyshare.fcm.enabled=false"],
)
@Import(PostgresTestContainer::class)
class BlockFlowTest {

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

    private fun delete(path: String, token: String): Res {
        val r = client.delete().uri(path).header("Authorization", "Bearer $token")
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun get(path: String, token: String): Res {
        val r = client.get().uri(path).header("Authorization", "Bearer $token")
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun field(body: String, name: String) =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: error("sem $name em $body")

    private fun register(nick: String) = field(
        post("/auth/register", json = """{"nickname":"$nick","email":"$nick@t.com","password":"senha-forte-123"}""").body,
        "accessToken",
    )

    private fun befriend(a: String, b: String, bNick: String) {
        post("/friends/requests", a, """{"nickname":"$bNick"}""")
        val rid = field(get("/friends/requests", b).body, "id")
        post("/friends/requests/$rid/accept", b)
    }

    @Test
    fun `bloqueio barra os dois sentidos com codigos distintos`() {
        val a = register("blk_a"); val b = register("blk_b")
        befriend(a, b, "blk_b")

        assertEquals(200, post("/blocks", a, """{"nickname":"blk_b"}""").status)

        val fromBlocker = post("/conversations/blk_b/messages", a, """{"body":"oi"}""")
        assertEquals(403, fromBlocker.status)
        assertTrue(fromBlocker.body.contains("you_blocked_them"), fromBlocker.body)

        val fromBlocked = post("/conversations/blk_a/messages", b, """{"body":"me bloqueou?"}""")
        assertEquals(403, fromBlocked.status)
        assertTrue(fromBlocked.body.contains("blocked_by_them"), fromBlocked.body)
    }

    @Test
    fun `desbloquear restaura a conversa`() {
        val a = register("unb_a"); val b = register("unb_b")
        befriend(a, b, "unb_b")
        post("/blocks", a, """{"nickname":"unb_b"}""")

        assertEquals(204, delete("/blocks/unb_b", a).status)
        assertEquals(200, post("/conversations/unb_b/messages", a, """{"body":"de novo"}""").status)
    }

    @Test
    fun `perfil reflete amizade bloqueio e grants`() {
        val a = register("prof_a"); val b = register("prof_b")
        befriend(a, b, "prof_b")
        val gid = field(post("/grants/offers", a, """{"nickname":"prof_b"}""").body, "id")
        post("/grants/$gid/accept", b)

        val before = get("/users/prof_b/profile", a)
        assertEquals(200, before.status)
        assertTrue(before.body.contains("\"friend\":true"), before.body)
        assertTrue(before.body.contains("\"blockedByMe\":false"), before.body)
        assertTrue(before.body.contains("sharingWithThem"), before.body)

        post("/blocks", a, """{"nickname":"prof_b"}""")
        assertTrue(get("/users/prof_b/profile", a).body.contains("\"blockedByMe\":true"))
    }
}
