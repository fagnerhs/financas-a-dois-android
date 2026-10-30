package br.com.financasdocasal

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object SupabaseCloud {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    val currentUserId: String?
        get() = client.auth.currentUserOrNull()?.id

    val currentEmail: String?
        get() = client.auth.currentUserOrNull()?.email

    suspend fun awaitSessionRestore() {
        client.auth.sessionStatus.first { it != SessionStatus.Initializing }
    }

    suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() = client.auth.signOut()

    suspend fun createSpace(name: String, displayName: String): SpaceInviteDto =
        client.postgrest.rpc(
            "create_couple_space",
            CreateSpaceArgs(name = name.trim(), displayName = displayName.trim())
        ).decodeSingle()

    suspend fun joinSpace(code: String, displayName: String) {
        client.postgrest.rpc(
            "join_couple_space",
            JoinSpaceArgs(inviteCode = code.trim(), displayName = displayName.trim())
        ).decodeAs<String>()
    }

    suspend fun refreshInvite(spaceId: String): SpaceInviteRefreshDto =
        client.postgrest.rpc(
            "refresh_couple_invite",
            RefreshInviteArgs(spaceId = spaceId)
        ).decodeSingle()

    suspend fun findMySpace(userId: String): CoupleSpaceDto? {
        val membership = client.from("couple_members").select {
            filter { eq("user_id", userId) }
        }.decodeList<CoupleMemberDto>().firstOrNull() ?: return null

        val space = client.from("couple_spaces").select {
            filter { eq("id", membership.spaceId) }
        }.decodeList<CoupleSpaceDto>().firstOrNull() ?: return null

        val members = client.from("couple_members").select {
            filter { eq("space_id", space.id) }
        }.decodeList<CoupleMemberDto>()
        return space.copy(members = members)
    }

    suspend fun fetchExpenses(spaceId: String): List<ExpenseDto> =
        client.from("expenses").select {
            filter { eq("space_id", spaceId) }
        }.decodeList()

    @OptIn(SupabaseExperimental::class)
    fun observeExpenses(spaceId: String): Flow<List<ExpenseDto>> =
        client.from("expenses").selectAsFlow(ExpenseDto::id) {
            eq("space_id", spaceId)
        }

    suspend fun uploadPending(spaceId: String, userId: String, store: LocalStore): Int {
        var uploaded = 0
        store.pending().forEach { expense ->
            val row = ExpenseDto(
                id = expense.remoteId,
                spaceId = spaceId,
                amount = expense.amount,
                category = expense.category,
                payer = expense.payer,
                description = expense.description,
                expenseDate = expense.date,
                createdBy = userId
            )
            client.from("expenses").upsert(row)
            store.markSynced(expense.remoteId, userId)
            uploaded++
        }
        return uploaded
    }

    suspend fun deleteExpense(spaceId: String, remoteId: String) {
        client.from("expenses").delete {
            filter {
                eq("id", remoteId)
                eq("space_id", spaceId)
            }
        }
    }

    suspend fun fetchBudgets(spaceId: String): Map<String, Double> =
        client.from("category_budgets").select {
            filter { eq("space_id", spaceId) }
        }.decodeList<BudgetDto>().associate { it.category to it.monthlyLimit }

    suspend fun saveBudgets(spaceId: String, budgets: Map<String, Double>) {
        val existing = fetchBudgets(spaceId)
        (existing.keys - budgets.keys).forEach { obsoleteCategory ->
            client.from("category_budgets").delete {
                filter {
                    eq("space_id", spaceId)
                    eq("category", obsoleteCategory)
                }
            }
        }
        val rows = budgets.filterValues { it >= 0.0 }.map { (category, limit) ->
            BudgetDto(spaceId = spaceId, category = category, monthlyLimit = limit)
        }
        if (rows.isNotEmpty()) {
            client.from("category_budgets").upsert(rows) {
                onConflict = "space_id,category"
            }
        }
    }

    suspend fun fetchSettings(spaceId: String): CoupleSettingsDto? =
        client.from("couple_settings").select {
            filter { eq("space_id", spaceId) }
        }.decodeList<CoupleSettingsDto>().firstOrNull()

    suspend fun saveSettings(settings: CoupleSettingsDto) {
        client.from("couple_settings").upsert(settings)
    }
}

@Serializable
data class CreateSpaceArgs(
    @SerialName("p_name") val name: String,
    @SerialName("p_display_name") val displayName: String
)

@Serializable
data class JoinSpaceArgs(
    @SerialName("p_invite_code") val inviteCode: String,
    @SerialName("p_display_name") val displayName: String
)

@Serializable
data class RefreshInviteArgs(@SerialName("p_space_id") val spaceId: String)

@Serializable
data class SpaceInviteDto(
    @SerialName("space_id") val spaceId: String,
    @SerialName("invite_code") val inviteCode: String
)

@Serializable
data class SpaceInviteRefreshDto(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class CoupleSpaceDto(
    val id: String,
    val name: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("invite_expires_at") val inviteExpiresAt: String,
    val members: List<CoupleMemberDto> = emptyList()
)

@Serializable
data class CoupleMemberDto(
    @SerialName("space_id") val spaceId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val role: String
)

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val amount: Double,
    val category: String,
    val payer: String,
    val description: String,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("created_by") val createdBy: String
) {
    fun toLocal() = Expense(
        amount = amount,
        category = category,
        payer = payer,
        description = description,
        date = expenseDate,
        remoteId = id,
        createdBy = createdBy,
        synced = true
    )
}

@Serializable
data class BudgetDto(
    @SerialName("space_id") val spaceId: String,
    val category: String,
    @SerialName("monthly_limit") val monthlyLimit: Double
)

@Serializable
data class CoupleSettingsDto(
    @SerialName("space_id") val spaceId: String,
    @SerialName("emergency_goal") val emergencyGoal: Double,
    @SerialName("emergency_saved") val emergencySaved: Double,
    @SerialName("monthly_contribution") val monthlyContribution: Double
)
