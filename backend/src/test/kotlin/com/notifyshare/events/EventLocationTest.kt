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
import kotlin.test.assertTrue

/**
 * Testa a funcionalidade de localizar um evento especifico no feed e buscar
 * eventos ao redor dele. Usado para scroll direto ao clicar em mensagem que
 * referencia uma notificacao.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["notifyshare.fcm.enabled=false"],
)
@Import(PostgresTestContainer::class)
class EventLocationTest {

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

    private fun field(body: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) 
            ?: error("sem $name em $body")

    private fun intField(body: String, name: String): Int =
        Regex("\"$name\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toInt() 
            ?: error("sem $name em $body")

    /** Cria origem e destino ja amigos, grant ativo, regra de content para tudo. */
    private fun scenario(tag: String): Pair<String, String> {
        val origin = field(
            post(
                "/auth/register", 
                json = """{"nickname":"o$tag","email":"o$tag@t.com","password":"senha-forte-123","acceptedPrivacy":true}"""
            ).body,
            "accessToken",
        )
        val dest = field(
            post(
                "/auth/register", 
                json = """{"nickname":"d$tag","email":"d$tag@t.com","password":"senha-forte-123","acceptedPrivacy":true}"""
            ).body,
            "accessToken",
        )
        post("/friends/requests", origin, """{"nickname":"d$tag"}""")
        val rid = field(get("/friends/requests", dest).body, "id")
        post("/friends/requests/$rid/accept", dest)
        val gid = field(post("/grants/offers", origin, """{"nickname":"d$tag"}""").body, "id")
        post("/grants/$gid/accept", dest)
        // sem regra nenhum evento e entregue — libera os apps usados nos testes
        put(
            "/grants/$gid/rules", origin,
            """{"rules":[
                {"packageName":"com.test","enabled":true,"contentMode":"content","allSenders":true},
                {"packageName":"com.whatsapp","enabled":true,"contentMode":"content","allSenders":true},
                {"packageName":"org.telegram.messenger","enabled":true,"contentMode":"content","allSenders":true}
            ]}""",
        )
        return origin to dest
    }

    private fun put(path: String, token: String, json: String): Res {
        val r = client.put().uri(path).header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).body(json)
            .exchange().expectBody(String::class.java).returnResult()
        return Res(r.status.value(), r.responseBody ?: "")
    }

    @Test
    fun `localiza evento no inicio da primeira pagina`() {
        val (origin, dest) = scenario("loc1")

        // Cria 5 eventos
        val events = (1..5).map { i ->
            val r = post(
                "/events", origin,
                """{"packageName":"com.test","eventType":"message","dedupKey":"k$i","content":"{}"}"""
            )
            field(r.body, "eventId")
        }

        // O evento mais recente (ultimo criado) deve estar em page=0, index=0
        val lastEventId = events.last()
        val location = get("/events/events/$lastEventId/location?pageSize=10", dest)

        assertEquals(200, location.status, location.body)
        assertEquals(0, intField(location.body, "page"), "Deve estar na primeira pagina")
        assertEquals(0, intField(location.body, "indexInPage"), "Deve estar no inicio")
        assertEquals(0, intField(location.body, "totalBefore"), "Nenhum evento antes dele")
    }

    @Test
    fun `localiza evento em pagina intermediaria`() {
        val (origin, dest) = scenario("loc2")

        // Cria 25 eventos (pageSize=10 -> 3 paginas)
        val events = (1..25).map { i ->
            val r = post(
                "/events", origin,
                """{"packageName":"com.test","eventType":"message","dedupKey":"k$i","content":"{}"}"""
            )
            field(r.body, "eventId")
        }

        // O evento do meio (indice 12, o 13º criado) deve estar na pagina 1
        // Ordem desc: [25,24,23...14] [13,12,11...5] [4,3,2,1]
        //              page 0           page 1          page 2
        val middleEventId = events[12]
        val location = get("/events/events/$middleEventId/location?pageSize=10", dest)

        assertEquals(200, location.status, location.body)
        assertEquals(1, intField(location.body, "page"), "Deve estar na segunda pagina (indice 1)")
        assertEquals(12, intField(location.body, "totalBefore"), "12 eventos antes dele")
    }

    @Test
    fun `localiza evento no final da ultima pagina`() {
        val (origin, dest) = scenario("loc3")

        // Cria 15 eventos (pageSize=10 -> 2 paginas)
        val events = (1..15).map { i ->
            val r = post(
                "/events", origin,
                """{"packageName":"com.test","eventType":"message","dedupKey":"k$i","content":"{}"}"""
            )
            field(r.body, "eventId")
        }

        // O primeiro evento criado deve estar no final da ultima pagina
        val firstEventId = events.first()
        val location = get("/events/events/$firstEventId/location?pageSize=10", dest)

        assertEquals(200, location.status, location.body)
        assertEquals(1, intField(location.body, "page"), "Deve estar na segunda pagina")
        assertEquals(4, intField(location.body, "indexInPage"), "Posicao 4 na pagina (5º item)")
        assertEquals(14, intField(location.body, "totalBefore"), "14 eventos antes dele")
    }

    @Test
    fun `retorna 404 quando evento nao existe`() {
        val (_, dest) = scenario("loc4")

        val location = get("/events/events/00000000-0000-0000-0000-000000000000/location", dest)

        assertEquals(404, location.status)
        assertTrue(location.body.contains("event_not_found"), location.body)
    }

    @Test
    fun `retorna 403 quando usuario nao tem acesso ao evento`() {
        val (origin1, dest1) = scenario("loc5a")
        val (origin2, dest2) = scenario("loc5b")

        // Origin1 cria evento que só dest1 recebe
        val r = post(
            "/events", origin1,
            """{"packageName":"com.test","eventType":"message","dedupKey":"k1","content":"{}"}"""
        )
        val eventId = field(r.body, "eventId")

        // Dest2 tenta localizar o evento de dest1 -> deve dar 403
        val location = get("/events/events/$eventId/location", dest2)

        assertEquals(403, location.status)
        assertTrue(location.body.contains("event_not_yours"), location.body)
    }

    @Test
    fun `busca eventos ao redor de um evento especifico`() {
        val (origin, dest) = scenario("ctx1")

        // Cria 10 eventos
        val events = (1..10).map { i ->
            val r = post(
                "/events", origin,
                """{"packageName":"com.test","eventType":"message","dedupKey":"k$i","content":"{}"}"""
            )
            field(r.body, "eventId")
        }

        // Busca contexto ao redor do evento do meio (indice 5)
        val middleEventId = events[5]
        val context = get("/events/events/$middleEventId/context?size=5", dest)

        assertEquals(200, context.status, context.body)
        
        // Deve retornar até 5 eventos (os mais proximos do alvo)
        val eventIdMatches = Regex("\"eventId\"\\s*:\\s*\"([^\"]+)\"").findAll(context.body).toList()
        assertTrue(eventIdMatches.size <= 5, "Deve retornar no maximo 5 eventos")
        
        // Deve incluir o evento alvo
        assertTrue(context.body.contains(middleEventId), "Deve incluir o evento alvo")
    }

    @Test
    fun `location respeita filtros aplicados`() {
        val (origin, dest) = scenario("loc6")

        // Cria 10 eventos de WhatsApp e 10 de Telegram
        val whatsappEvents = (1..10).map { i ->
            val r = post(
                "/events", origin,
                """{"packageName":"com.whatsapp","eventType":"message","dedupKey":"wa$i","content":"{}"}"""
            )
            field(r.body, "eventId")
        }
        val telegramEvents = (1..10).map { i ->
            val r = post(
                "/events", origin,
                """{"packageName":"org.telegram.messenger","eventType":"message","dedupKey":"tg$i","content":"{}"}"""
            )
            field(r.body, "eventId")
        }

        // Localiza o 5º evento de WhatsApp COM filtro de package
        // No feed filtrado, ele deve estar em posição específica
        val targetId = whatsappEvents[4]
        val location = get("/events/events/$targetId/location?package=com.whatsapp&pageSize=10", dest)

        assertEquals(200, location.status, location.body)
        // Com filtro, só 10 eventos (todos WhatsApp). O 5º criado é o 6º na ordem desc (indice 5)
        // Todos na mesma página (page 0)
        assertEquals(0, intField(location.body, "page"), "Com filtro, deve estar na primeira pagina")
        assertEquals(5, intField(location.body, "totalBefore"), "5 eventos de WhatsApp antes dele")
    }

    @Test
    fun `originador pode localizar seu proprio evento`() {
        val (origin, dest) = scenario("loc7")

        val r = post(
            "/events", origin,
            """{"packageName":"com.test","eventType":"message","dedupKey":"k1","content":"{}"}"""
        )
        val eventId = field(r.body, "eventId")

        // Originador localiza seu proprio evento (mesmo sem ser destinatario)
        val location = get("/events/events/$eventId/location", origin)

        assertEquals(200, location.status, location.body)
    }
}
