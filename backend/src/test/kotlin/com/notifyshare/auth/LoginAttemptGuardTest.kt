package com.notifyshare.auth

import com.notifyshare.auth.application.LoginAttemptGuard
import com.notifyshare.shared.web.TooManyRequestsException
import com.notifyshare.shared.web.UnauthorizedException
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LoginAttemptGuardTest {

    private fun guard(maxAttempts: Int = 3, lockout: Duration = Duration.ofMinutes(1)) =
        LoginAttemptGuard(maxAttempts, lockout, enabled = true)

    @Test
    fun `mostra tentativas restantes a cada erro, ate bloquear na ultima`() {
        val g = guard(maxAttempts = 3)

        val first = assertFailsWith<UnauthorizedException> { g.recordFailure("gustavo") }
        assertEquals(2, first.attemptsRemaining)

        val second = assertFailsWith<UnauthorizedException> { g.recordFailure("gustavo") }
        assertEquals(1, second.attemptsRemaining)

        val third = assertFailsWith<TooManyRequestsException> { g.recordFailure("gustavo") }
        assertEquals("account_locked", third.code)
    }

    @Test
    fun `bloqueia novas tentativas enquanto o bloqueio estiver ativo`() {
        val g = guard(maxAttempts = 1, lockout = Duration.ofMinutes(5))

        assertFailsWith<TooManyRequestsException> { g.recordFailure("presa") }
        val blocked = assertFailsWith<TooManyRequestsException> { g.assertNotLocked("presa") }
        assertEquals("account_locked", blocked.code)
        assert(blocked.retryAfterSeconds!! in 1..300)
    }

    @Test
    fun `identificadores diferentes nao se afetam`() {
        val g = guard(maxAttempts = 1)

        assertFailsWith<TooManyRequestsException> { g.recordFailure("a") }
        g.assertNotLocked("b") // nao lanca
    }

    @Test
    fun `login certo zera o historico de erros`() {
        val g = guard(maxAttempts = 2)

        assertFailsWith<UnauthorizedException> { g.recordFailure("volta") }
        g.recordSuccess("volta")
        g.assertNotLocked("volta") // nao lanca

        // o contador voltou a zero: precisa de 2 erros de novo pra bloquear, nao 1
        val afterReset = assertFailsWith<UnauthorizedException> { g.recordFailure("volta") }
        assertEquals(1, afterReset.attemptsRemaining)
    }

    @Test
    fun `desligado nunca bloqueia e nao informa tentativas restantes`() {
        val g = LoginAttemptGuard(maxAttempts = 1, lockoutDuration = Duration.ofMinutes(1), enabled = false)

        val error = assertFailsWith<UnauthorizedException> { g.recordFailure("qualquer") }
        assertNull(error.attemptsRemaining)
        g.assertNotLocked("qualquer") // nao lanca mesmo depois de "estourar" o limite
    }
}
