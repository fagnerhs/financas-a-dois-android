package br.com.financasdocasal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorMessagesTest {
    @Test
    fun emailConfirmationRateLimitHasActionableMessage() {
        val message = authErrorNotice(IllegalStateException("over_email_send_rate_limit"), creatingAccount = true)
        assertTrue(message.contains("limite temporário"))
        assertTrue(message.contains("apenas uma vez"))
    }

    @Test
    fun invalidCredentialsAreExplainedWithoutTechnicalCodes() {
        val message = authErrorNotice(IllegalStateException("invalid_credentials"), creatingAccount = false)
        assertTrue(message.contains("E-mail ou senha incorretos"))
        assertFalse(message.contains("invalid_credentials"))
    }

    @Test
    fun unknownServerDetailsAreNeverShownToTheUser() {
        val secretLikeDetail = "unexpected response with token=do-not-display"
        val message = authErrorNotice(IllegalStateException(secretLikeDetail), creatingAccount = true)
        assertFalse(message.contains("token="))
        assertFalse(message.contains(secretLikeDetail))
    }
}
