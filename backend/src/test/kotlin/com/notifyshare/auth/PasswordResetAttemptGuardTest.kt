package com.notifyshare.auth

import com.notifyshare.auth.application.PasswordResetAttemptGuard
import com.notifyshare.auth.application.PasswordResetProperties
import com.notifyshare.shared.web.TooManyRequestsException
import com.notifyshare.shared.web.UnauthorizedException
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PasswordResetAttemptGuardTest {

    private fun guard(max: Int = 3, lock: Duration = Duration.ofMinutes(5)) =
        PasswordResetAttemptGuard(PasswordResetProperties(maxAttempts = max, lockoutDuration = lock))

    @Test
    fun `conta tentativas restantes por IP e bloqueia na ultima`() {
        val g = guard(max = 3)

        assertEquals(2, assertFailsWith<UnauthorizedException> { g.recordFailure("1.2.3.4") }.attemptsRemaining)
        assertEquals(1, assertFailsWith<UnauthorizedException> { g.recordFailure("1.2.3.4") }.attemptsRemaining)

        val locked = assertFailsWith<TooManyRequestsException> { g.recordFailure("1.2.3.4") }
        assertEquals("reset_locked", locked.code)
        assert(locked.retryAfterSeconds!! in 1..300)
    }

    @Test
    fun `IPs diferentes nao se afetam — vitima nao trava por causa do atacante`() {
        val g = guard(max = 1)

        assertFailsWith<TooManyRequestsException> { g.recordFailure("attacker") }
        g.assertNotLocked("vitima") // nao lanca
    }

    @Test
    fun `reset concluido limpa o historico do IP`() {
        val g = guard(max = 2)
        assertFailsWith<UnauthorizedException> { g.recordFailure("5.6.7.8") }
        g.recordSuccess("5.6.7.8")
        // volta ao zero: o proximo erro ainda mostra 1 restante, nao bloqueia
        assertEquals(1, assertFailsWith<UnauthorizedException> { g.recordFailure("5.6.7.8") }.attemptsRemaining)
    }
}
