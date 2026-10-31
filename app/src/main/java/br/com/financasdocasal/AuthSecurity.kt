package br.com.financasdocasal

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.mfa.FactorType

/** Minimal typed results for the optional Supabase TOTP flow. */
data class PendingMfaChallenge(val factorId: String, val challengeId: String)
data class TotpEnrollment(val factorId: String, val secret: String, val uri: String)

object AuthSecurity {
    suspend fun updateRecoveredPassword(password: String) {
        require(password.length >= 8)
        SupabaseCloud.client.auth.updateUser { this.password = password }
    }

    suspend fun sendPasswordReset(email: String) {
        require(email.contains('@'))
        SupabaseCloud.client.auth.resetPasswordForEmail(email.trim())
    }

    /** Returns a challenge only when this account has a verified factor but the current session is AAL1. */
    suspend fun beginStepUpIfRequired(): PendingMfaChallenge? {
        val mfa = SupabaseCloud.client.auth.mfa
        val level = mfa.getAuthenticatorAssuranceLevel()
        if (level.next != AuthenticatorAssuranceLevel.AAL2 || level.current == AuthenticatorAssuranceLevel.AAL2) return null
        val factor = mfa.retrieveFactorsForCurrentUser().firstOrNull { it.isVerified }
            ?: error("MFA is enabled but no verified factor is available")
        val challenge = mfa.createChallenge(factor.id)
        return PendingMfaChallenge(factor.id, challenge.id)
    }

    suspend fun verifyStepUp(challenge: PendingMfaChallenge, code: String) {
        require(code.length == 6 && code.all(Char::isDigit))
        SupabaseCloud.client.auth.mfa.verifyChallenge(challenge.factorId, challenge.challengeId, code)
    }

    suspend fun enrollTotp(): TotpEnrollment {
        val factor = SupabaseCloud.client.auth.mfa.enroll(FactorType.TOTP, friendlyName = "Finanças a Dois") {
            issuer = "Finanças a Dois"
        }
        return TotpEnrollment(factor.id, factor.data.secret, factor.data.uri)
    }

    suspend fun verifyTotpEnrollment(factorId: String, code: String) {
        require(code.length == 6 && code.all(Char::isDigit))
        SupabaseCloud.client.auth.mfa.createChallengeAndVerify(factorId, code)
    }

    suspend fun verifiedTotpFactorIds(): List<String> =
        SupabaseCloud.client.auth.mfa.retrieveFactorsForCurrentUser()
            .filter { it.isVerified }
            .map { it.id }

    suspend fun disableTotp(factorId: String) {
        SupabaseCloud.client.auth.mfa.unenroll(factorId)
    }

    fun mfaIsSatisfiedForSession(): Boolean {
        val mfa = SupabaseCloud.client.auth.mfa
        val level = mfa.getAuthenticatorAssuranceLevel()
        return level.next != AuthenticatorAssuranceLevel.AAL2 || level.current == AuthenticatorAssuranceLevel.AAL2
    }
}
