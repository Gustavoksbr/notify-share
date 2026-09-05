package com.notifyshare.shared.web

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

class RateLimitFilterTest {

    private val noopChain = FilterChain { _, res ->
        (res as MockHttpServletResponse).status = 200
    }

    @Test
    fun `permite ate o limite e barra a partir dai, por IP`() {
        val filter = RateLimitFilter()

        repeat(10) { i ->
            val request = MockHttpServletRequest("POST", "/auth/login").apply { remoteAddr = "1.2.3.4" }
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, noopChain)
            assertEquals(200, response.status, "requisicao ${i + 1}/10 deveria passar")
        }

        val eleventh = MockHttpServletRequest("POST", "/auth/login").apply { remoteAddr = "1.2.3.4" }
        val blocked = MockHttpServletResponse()
        filter.doFilter(eleventh, blocked, noopChain)
        assertEquals(429, blocked.status)
    }

    @Test
    fun `IPs diferentes tem baldes independentes`() {
        val filter = RateLimitFilter()

        repeat(10) {
            val request = MockHttpServletRequest("POST", "/auth/login").apply { remoteAddr = "1.1.1.1" }
            filter.doFilter(request, MockHttpServletResponse(), noopChain)
        }

        val fromAnotherIp = MockHttpServletRequest("POST", "/auth/login").apply { remoteAddr = "2.2.2.2" }
        val response = MockHttpServletResponse()
        filter.doFilter(fromAnotherIp, response, noopChain)
        assertEquals(200, response.status)
    }

    @Test
    fun `rotas fora da lista nao sao limitadas`() {
        val filter = RateLimitFilter()

        repeat(50) {
            val request = MockHttpServletRequest("GET", "/me").apply { remoteAddr = "9.9.9.9" }
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, noopChain)
            assertEquals(200, response.status)
        }
    }
}
