package br.com.financasdocasal

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceRpcDecodeTest {
    @Test fun createSpaceJsonObjectDecodesAsDto() {
        val result = Json.decodeFromString<SpaceInviteDto>(
            """{"space_id":"00000000-0000-0000-0000-000000000001","invite_code":"00000000-0000-0000-0000-000000000002"}"""
        )
        assertEquals("00000000-0000-0000-0000-000000000001", result.spaceId)
        assertEquals("00000000-0000-0000-0000-000000000002", result.inviteCode)
    }

    @Test fun refreshedInviteJsonObjectDecodesAsDto() {
        val result = Json.decodeFromString<SpaceInviteRefreshDto>(
            """{"invite_code":"00000000-0000-0000-0000-000000000002","expires_at":"2026-10-31T02:00:00Z"}"""
        )
        assertEquals("00000000-0000-0000-0000-000000000002", result.inviteCode)
        assertEquals("2026-10-31T02:00:00Z", result.expiresAt)
    }
}
