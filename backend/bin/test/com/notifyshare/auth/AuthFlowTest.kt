package com.notifyshare.auth

import com.notifyshare.PostgresTestContainer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Roda contra o servidor de verdade, num Postgres de verdade. Nao ha mock:
 * o ponto destes testes e justamente a cadeia de filtros de seguranca e o
 * comportamento transacional, que sao exatamente as partes que um mock apaga.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainer::class)
class AuthFlowTest {

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @BeforeEach
    fun setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    // --- helpers ---------------------------------------------------------

    private data class Response(val status: Int, val body: String)

    private fun post(path: String, json: String): Response {
        val result = client.post().uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()
            .expectBody(String::class.java)
            .returnResult()
        return Response(result.status.value(), result.responseBody ?: "")
    }

    private fun get(path: String, accessToken: String? = null): Response {
        val spec = client.get().uri(path)
        if (accessToken != null) spec.header("Authorization", "Bearer $accessToken")
        val result = spec.exchange().expectBody(String::class.java).returnResult()
        return Response(result.status.value(), result.responseBody ?: "")
    }

    private fun register(nickname: String, email: String, password: String = SENHA) =
        post("/auth/register", """{"nickname":"$nickname","email":"$email","password":"$password"}""")

    private fun refreshToken(body: String) = field(body, "refreshToken")
    private fun accessToken(body: String) = field(body, "accessToken")

    private fun field(body: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: error("campo $name ausente na resposta: $body")

    // --- cadastro --------------------------------------------------------

    @Test
    fun `cadastro normaliza nickname e email para minusculas`() {
        val result = register("MaiusculasAqui", "Pessoa.Teste@Gmail.COM")

        assertEquals(201, result.status)
        assertTrue(result.body.contains("\"nickname\":\"maiusculasaqui\""), result.body)
        assertTrue(result.body.contains("\"email\":\"pessoa.teste@gmail.com\""), result.body)
    }

    @Test
    fun `nickname ja usado e recusado mesmo com caixa diferente`() {
        register("duplicado", "duplicado@teste.com")

        val result = register("DUPLICADO", "outro-email@teste.com")

        assertEquals(409, result.status)
        assertTrue(result.body.contains("nickname_taken"), result.body)
    }

    @Test
    fun `email ja usado e recusado`() {
        register("primeiro", "mesmo@teste.com")

        val result = register("segundo", "mesmo@teste.com")

        assertEquals(409, result.status)
        assertTrue(result.body.contains("email_taken"), result.body)
    }

    @Test
    fun `nickname invalido explica o motivo`() {
        assertEquals(400, register("ab", "curto@teste.com").status)
        assertEquals(400, register("admin", "reservado@teste.com").status)
        assertEquals(400, register("com espaco", "espaco@teste.com").status)
        assertTrue(register("ab", "curto2@teste.com").body.contains("invalid_nickname"))
    }

    // --- login -----------------------------------------------------------

    @Test
    fun `senha errada e usuario inexistente devolvem exatamente o mesmo erro`() {
        register("existente", "existente@teste.com")

        val senhaErrada = post("/auth/login", """{"identifier":"existente","password":"errada12345"}""")
        val naoExiste = post("/auth/login", """{"identifier":"fantasma","password":"errada12345"}""")

        assertEquals(401, senhaErrada.status)
        assertEquals(401, naoExiste.status)
        // Identicos de proposito: a API nao pode virar um oraculo de quais
        // nicknames existem.
        assertEquals(senhaErrada.body, naoExiste.body)
    }

    @Test
    fun `login aceita nickname ou email`() {
        register("porduasvias", "porduasvias@teste.com")

        assertEquals(200, post("/auth/login", """{"identifier":"porduasvias","password":"$SENHA"}""").status)
        assertEquals(200, post("/auth/login", """{"identifier":"porduasvias@teste.com","password":"$SENHA"}""").status)
    }

    @Test
    fun `me exige token e devolve o proprio perfil`() {
        val body = register("comtoken", "comtoken@teste.com").body

        assertEquals(401, get("/me").status)

        val autenticado = get("/me", accessToken(body))
        assertEquals(200, autenticado.status)
        assertTrue(autenticado.body.contains("\"nickname\":\"comtoken\""), autenticado.body)
    }

    // --- rotacao de refresh ----------------------------------------------

    @Test
    fun `refresh rotaciona o token`() {
        val original = refreshToken(register("rotaciona", "rotaciona@teste.com").body)

        val result = post("/auth/refresh", """{"refreshToken":"$original"}""")

        assertEquals(200, result.status)
        assertNotEquals(original, refreshToken(result.body))
    }

    /**
     * O teste que mais importa neste arquivo.
     *
     * A revogacao da familia roda imediatamente antes de um throw. Se ela
     * acontecer na mesma transacao, o rollback a desfaz: o servidor devolve 401
     * e continua comprometido, porque o token vazado segue valendo. Foi
     * exatamente o que a primeira versao fazia, e passava despercebido porque
     * a resposta HTTP ja estava correta.
     */
    @Test
    fun `reuso de refresh token derruba a familia inteira`() {
        val original = refreshToken(register("reuso", "reuso@teste.com").body)
        val rotacionado = refreshToken(post("/auth/refresh", """{"refreshToken":"$original"}""").body)

        // Alguem apresenta o token antigo: so pode ser copia.
        val reuso = post("/auth/refresh", """{"refreshToken":"$original"}""")
        assertEquals(401, reuso.status)
        assertTrue(reuso.body.contains("refresh_token_reused"), reuso.body)

        // O token legitimo morre junto: nao da para saber qual das duas pontas
        // e a atacante, entao as duas voltam para o login.
        assertEquals(401, post("/auth/refresh", """{"refreshToken":"$rotacionado"}""").status)

        // Mas quem sabe a senha continua entrando normalmente.
        assertEquals(200, post("/auth/login", """{"identifier":"reuso","password":"$SENHA"}""").status)
    }

    @Test
    fun `logout revoga apenas a sessao daquele aparelho`() {
        val primeiro = refreshToken(register("duassessoes", "duassessoes@teste.com").body)
        val segundo = refreshToken(post("/auth/login", """{"identifier":"duassessoes","password":"$SENHA"}""").body)

        assertEquals(204, post("/auth/logout", """{"refreshToken":"$primeiro"}""").status)

        assertEquals(401, post("/auth/refresh", """{"refreshToken":"$primeiro"}""").status)
        assertEquals(200, post("/auth/refresh", """{"refreshToken":"$segundo"}""").status)
    }

    private companion object {
        const val SENHA = "senha-forte-123"
    }
}
