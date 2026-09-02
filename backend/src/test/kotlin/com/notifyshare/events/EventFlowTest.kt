package com.notifyshare.events

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
 * Ingestao -> roteamento -> feed. O que interessa: o servidor respeita a regra
 * de remetente, "so remetente" nao vaza conteudo, e dedupKey e idempotente.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["notifyshare.fcm.enabled=false"],
)
@Import(PostgresTestContainer::class)
class EventFlowTest {

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

    private fun put(path: String, token: String, json: String): Res {
        val r = client.put().uri(path).header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).body(json)
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun get(path: String, token: String): Res {
        val r = client.get().uri(path).header("Authorization", "Bearer $token")
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    private fun field(body: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: error("sem $name em $body")

    /** origem e destino ja amigos, grant ativo, regras aplicadas. Devolve tokens. */
    private fun scenario(tag: String): Pair<String, String> {
        val origin = field(
            post("/auth/register", json = """{"nickname":"o$tag","email":"o$tag@t.com","password":"senha-forte-123"}""").body,
            "accessToken",
        )
        val dest = field(
            post("/auth/register", json = """{"nickname":"d$tag","email":"d$tag@t.com","password":"senha-forte-123"}""").body,
            "accessToken",
        )
        post("/friends/requests", origin, """{"nickname":"d$tag"}""")
        val rid = field(get("/friends/requests", dest).body, "id")
        post("/friends/requests/$rid/accept", dest)
        val gid = field(post("/grants/offers", origin, """{"nickname":"d$tag"}""").body, "id")
        post("/grants/$gid/accept", dest)
        put(
            "/grants/$gid/rules", origin,
            """{"rules":[
                {"packageName":"com.whatsapp","contentMode":"content","allSenders":false,"senders":[{"senderHash":"hJose"}]},
                {"packageName":"org.telegram.messenger","contentMode":"sender_only","allSenders":true}
            ]}""",
        )
        return origin to dest
    }

    @Test
    fun `remetente fora da regra nao e entregue`() {
        val (origin, dest) = scenario("sender")

        val allowed = post(
            "/events", origin,
            """{"packageName":"com.whatsapp","eventType":"message","senderHash":"hJose","dedupKey":"a1","content":"{}"}""",
        )
        val blocked = post(
            "/events", origin,
            """{"packageName":"com.whatsapp","eventType":"message","senderHash":"hMaria","dedupKey":"b1","content":"{}"}""",
        )

        assertTrue(allowed.body.contains("\"deliveries\":1"), allowed.body)
        assertTrue(blocked.body.contains("\"deliveries\":0"), blocked.body)
        assertEquals(1, Regex("\"deliveryId\"").findAll(get("/events", dest).body).count())
    }

    @Test
    fun `so remetente nao entrega o conteudo`() {
        val (origin, dest) = scenario("sonly")
        post(
            "/events", origin,
            """{"packageName":"org.telegram.messenger","eventType":"message","senderHash":"hG","dedupKey":"t1","content":"{\"body\":\"segredo\"}"}""",
        )
        val feed = get("/events", dest).body
        assertTrue(feed.contains("\"mode\":\"sender_only\""), feed)
        assertFalse(feed.contains("segredo"), feed)
    }

    @Test
    fun `feed pagina e filtra por app`() {
        val (origin, dest) = scenario("pag")
        // 3 do whatsapp (remetente liberado), 1 do telegram
        repeat(3) { i ->
            post(
                "/events", origin,
                """{"packageName":"com.whatsapp","eventType":"message","senderHash":"hJose","dedupKey":"w$i","content":"{}"}""",
            )
        }
        post(
            "/events", origin,
            """{"packageName":"org.telegram.messenger","eventType":"message","senderHash":"hX","dedupKey":"tg","content":"{}"}""",
        )

        val page0 = get("/events?size=2", dest).body
        assertEquals(2, Regex("\"deliveryId\"").findAll(page0).count(), page0)
        val page1 = get("/events?size=2&page=1", dest).body
        assertEquals(2, Regex("\"deliveryId\"").findAll(page1).count(), page1)

        val onlyWhats = get("/events?package=com.whatsapp", dest).body
        assertEquals(3, Regex("\"deliveryId\"").findAll(onlyWhats).count(), onlyWhats)
        assertFalse(onlyWhats.contains("telegram"), onlyWhats)
    }

    @Test
    fun `recipient silencia um app mas segue recebendo no feed`() {
        val (origin, dest) = scenario("mute")
        val gid = field(get("/grants?role=recipient", dest).body, "id")

        assertEquals(204, put("/grants/$gid/notify-rules", dest, """{"packageName":"com.whatsapp","notify":false}""").status)
        val rules = get("/grants/$gid/notify-rules", dest).body
        assertTrue(rules.contains("\"packageName\":\"com.whatsapp\""), rules)
        assertTrue(rules.contains("\"notify\":false"), rules)

        post(
            "/events", origin,
            """{"packageName":"com.whatsapp","eventType":"message","senderHash":"hJose","dedupKey":"m1","content":"{}"}""",
        )
        // continua no feed — silenciar so tira o push
        assertEquals(1, Regex("\"deliveryId\"").findAll(get("/events", dest).body).count())

        assertEquals(204, put("/grants/$gid/notify-rules", dest, """{"packageName":"com.whatsapp","notify":true}""").status)
        assertTrue(get("/grants/$gid/notify-rules", dest).body.contains("\"notify\":true"))
    }

    @Test
    fun `dedupKey e idempotente`() {
        val (origin, _) = scenario("dedup")
        val first = post(
            "/events", origin,
            """{"packageName":"com.whatsapp","eventType":"message","senderHash":"hJose","dedupKey":"same","content":"{}"}""",
        )
        val again = post(
            "/events", origin,
            """{"packageName":"com.whatsapp","eventType":"message","senderHash":"hJose","dedupKey":"same","content":"{}"}""",
        )
        assertTrue(first.body.contains("\"deduped\":false"))
        assertTrue(again.body.contains("\"deduped\":true"), again.body)
        assertEquals(field(first.body, "eventId"), field(again.body, "eventId"))
    }
}
