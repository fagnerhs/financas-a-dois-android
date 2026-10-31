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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object SupabaseCloud {
    val client = createSupabaseClient(supabaseUrl = BuildConfig.SUPABASE_URL, supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true }); install(Auth) { scheme = "financas-a-dois"; host = "auth"; flowType = io.github.jan.supabase.auth.FlowType.PKCE }; install(Postgrest); install(Realtime)
    }
    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id
    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email
    suspend fun awaitSessionRestore() { client.auth.sessionStatus.first { it != SessionStatus.Initializing } }
    suspend fun signUp(email:String,password:String) { client.auth.signUpWith(Email) { this.email=email.trim(); this.password=password } }
    suspend fun signIn(email:String,password:String) { client.auth.signInWith(Email) { this.email=email.trim(); this.password=password } }
    suspend fun signOut()=client.auth.signOut()
    suspend fun signOutEverywhere()=client.auth.signOut(io.github.jan.supabase.auth.SignOutScope.GLOBAL)
    suspend fun createSpace(name:String,displayName:String):SpaceInviteDto=client.postgrest.rpc("create_couple_space",CreateSpaceArgs(name.trim(),displayName.trim())).decodeAs<SpaceInviteDto>()
    suspend fun joinSpace(code:String,displayName:String) { client.postgrest.rpc("join_couple_space",JoinSpaceArgs(code.trim(),displayName.trim())).decodeAs<String>() }
    suspend fun refreshInvite(spaceId:String):SpaceInviteRefreshDto=client.postgrest.rpc("refresh_couple_invite",RefreshInviteArgs(spaceId)).decodeAs<SpaceInviteRefreshDto>()
    suspend fun findMySpace(userId:String):CoupleSpaceDto? {
        val membership=client.from("couple_members").select { filter { eq("user_id",userId) } }.decodeList<CoupleMemberDto>().minByOrNull { it.spaceId } ?: return null
        val space=client.from("couple_spaces").select { filter { eq("id",membership.spaceId) } }.decodeList<CoupleSpaceDto>().firstOrNull() ?: return null
        return space.copy(members=client.from("couple_members").select { filter { eq("space_id",space.id) } }.decodeList())
    }
    suspend fun fetchExpenses(spaceId:String):List<ExpenseDto> = client.from("expenses").select { filter { eq("space_id",spaceId) } }.decodeList()
    @OptIn(SupabaseExperimental::class) fun observeExpenses(spaceId:String):Flow<List<ExpenseDto>> = client.from("expenses").selectAsFlow(ExpenseDto::id) { eq("space_id",spaceId) }
    suspend fun fetchExpenseBalances(spaceId:String):List<ExpenseBalanceDto> = client.from("expense_balances").select { filter { eq("space_id",spaceId) } }.decodeList()
    @OptIn(SupabaseExperimental::class) fun observeExpenseBalances(spaceId:String):Flow<List<ExpenseBalanceDto>> = client.from("expense_balances").selectAsFlow(ExpenseBalanceDto::expenseId) { eq("space_id",spaceId) }
    suspend fun upsertExpense(spaceId:String,userId:String,expense:Expense) { client.from("expenses").upsert(expense.toDto(spaceId,userId)) }
    suspend fun uploadPending(spaceId:String,userId:String,store:LocalStore):Int {
        var uploaded=0
        store.pending().filter { it.createdBy == userId }.forEach { expense -> client.from("expenses").upsert(expense.toDto(spaceId,userId)); store.markSynced(expense.remoteId,userId); uploaded++ }
        return uploaded
    }
    suspend fun deleteExpense(spaceId:String,remoteId:String) { client.from("expenses").delete { filter { eq("id",remoteId); eq("space_id",spaceId) } } }
    suspend fun markExpenseReviewed(spaceId:String,expenseId:String) { client.postgrest.rpc("mark_expense_reviewed",MarkExpenseReviewedArgs(expenseId,spaceId)).decodeAs<Boolean>() }
    suspend fun fetchRecurringBills(spaceId:String):List<RecurringBillDto> = client.from("recurring_bills").select { filter { eq("space_id",spaceId) } }.decodeList()
    @OptIn(SupabaseExperimental::class) fun observeRecurringBills(spaceId:String):Flow<List<RecurringBillDto>> = client.from("recurring_bills").selectAsFlow(RecurringBillDto::id) { eq("space_id",spaceId) }
    suspend fun upsertRecurringBill(spaceId:String,userId:String,bill:RecurringBill) { client.from("recurring_bills").upsert(bill.toDto(spaceId,userId)) }
    suspend fun deleteRecurringBill(spaceId:String,id:String) { client.from("recurring_bills").delete { filter { eq("id",id); eq("space_id",spaceId) } } }
    suspend fun uploadPendingRecurringBills(spaceId:String,userId:String,store:LocalStore):Int { var n=0; store.pendingRecurringBills().filter { it.createdBy == userId }.forEach { upsertRecurringBill(spaceId,userId,it); store.markRecurringBillSynced(it.id); n++ }; return n }
    suspend fun fetchSavingsGoals(spaceId:String):List<SavingsGoalDto> = client.from("savings_goals").select { filter { eq("space_id",spaceId) } }.decodeList()
    @OptIn(SupabaseExperimental::class) fun observeSavingsGoals(spaceId:String):Flow<List<SavingsGoalDto>> = client.from("savings_goals").selectAsFlow(SavingsGoalDto::id) { eq("space_id",spaceId) }
    suspend fun upsertSavingsGoal(spaceId:String,userId:String,goal:SavingsGoal) { client.postgrest.rpc("save_savings_goal",SaveSavingsGoalArgs(goal.id,spaceId,goal.name,goal.targetAmount,goal.savedAmount,goal.monthlyContribution,goal.targetDate)).decodeAs<String>() }
    suspend fun deleteSavingsGoal(spaceId:String,id:String) { client.from("savings_goals").delete { filter { eq("id",id); eq("space_id",spaceId) } } }
    suspend fun uploadPendingSavingsGoals(spaceId:String,userId:String,store:LocalStore):Int { var n=0; store.pendingSavingsGoals().filter { it.createdBy == userId }.forEach { upsertSavingsGoal(spaceId,userId,it); store.markSavingsGoalSynced(it.id); n++ }; return n }
    suspend fun fetchExpenseEvents(spaceId:String):List<ExpenseEventDto> = client.from("expense_events").select { filter { eq("space_id",spaceId) } }.decodeList()
    @OptIn(SupabaseExperimental::class) fun observeExpenseEvents(spaceId:String):Flow<List<ExpenseEventDto>> = client.from("expense_events").selectAsFlow(ExpenseEventDto::id) { eq("space_id",spaceId) }
    suspend fun fetchBudgets(spaceId:String):Map<String,Double> = client.from("category_budgets").select { filter { eq("space_id",spaceId) } }.decodeList<BudgetDto>().associate { it.category to it.monthlyLimit }
    suspend fun saveBudgets(spaceId:String,budgets:Map<String,Double>) {
        val existing=fetchBudgets(spaceId)
        (existing.keys-budgets.keys).forEach { category -> client.from("category_budgets").delete { filter { eq("space_id",spaceId); eq("category",category) } } }
        val rows=budgets.filterValues { it>=0.0 }.map { (category,limit)->BudgetDto(spaceId,category,limit) }
        if(rows.isNotEmpty()) client.from("category_budgets").upsert(rows) { onConflict="space_id,category" }
    }
    suspend fun fetchSettings(spaceId:String):CoupleSettingsDto?=client.from("couple_settings").select { filter { eq("space_id",spaceId) } }.decodeList<CoupleSettingsDto>().firstOrNull()
    suspend fun saveSettings(settings:CoupleSettingsDto) { client.from("couple_settings").upsert(settings) }
}

@Serializable data class CreateSpaceArgs(@SerialName("p_name") val name:String,@SerialName("p_display_name") val displayName:String)
@Serializable data class JoinSpaceArgs(@SerialName("p_invite_code") val inviteCode:String,@SerialName("p_display_name") val displayName:String)
@Serializable data class RefreshInviteArgs(@SerialName("p_space_id") val spaceId:String)
@Serializable data class SpaceInviteDto(@SerialName("space_id") val spaceId:String,@SerialName("invite_code") val inviteCode:String)
@Serializable data class SpaceInviteRefreshDto(@SerialName("invite_code") val inviteCode:String,@SerialName("expires_at") val expiresAt:String)
@Serializable data class CoupleSpaceDto(val id:String,val name:String,@SerialName("invite_code") val inviteCode:String,@SerialName("invite_expires_at") val inviteExpiresAt:String,val members:List<CoupleMemberDto> = emptyList())
@Serializable data class CoupleMemberDto(@SerialName("space_id") val spaceId:String,@SerialName("user_id") val userId:String,@SerialName("display_name") val displayName:String,val role:String)
@Serializable data class ExpenseDto(
    val id:String,@SerialName("space_id") val spaceId:String,val amount:Double,val category:String,val payer:String,val description:String,
    @SerialName("expense_date") val expenseDate:String,@SerialName("created_by") val createdBy:String,
    val visibility:String="shared",@SerialName("creator_share_amount") val creatorShareAmount:Double=amount,
    @SerialName("review_status") val reviewStatus:String="pending",@SerialName("reviewed_by") val reviewedBy:String?=null,
    @SerialName("reviewed_at") val reviewedAt:String?=null,@SerialName("kind") val kind:String=EntryKind.EXPENSE
) {
    fun toLocal()=Expense(amount,category,payer,description,expenseDate,id,createdBy,synced=true,visibility=visibility,creatorShareAmount=creatorShareAmount,reviewStatus=reviewStatus,reviewedBy=reviewedBy,reviewedAt=reviewedAt,kind=kind)
}
private fun Expense.toDto(spaceId:String,userId:String)=ExpenseDto(remoteId,spaceId,amount,category,payer,description,date,userId,visibility,creatorShareAmount,reviewStatus,reviewedBy,reviewedAt,kind)
@Serializable data class ExpenseBalanceDto(@SerialName("expense_id") val expenseId:String,@SerialName("space_id") val spaceId:String,val amount:Double,@SerialName("expense_date") val expenseDate:String,@SerialName("created_by") val createdBy:String)
@Serializable data class RecurringBillDto(val id:String,@SerialName("space_id") val spaceId:String,val amount:Double,val category:String,val payer:String,val description:String,@SerialName("next_date") val nextDate:String,val frequency:String,@SerialName("is_active") val isActive:Boolean,val visibility:String,@SerialName("created_by") val createdBy:String) {
    fun toLocal()=RecurringBill(id,spaceId,amount,category,payer,description,nextDate,frequency,isActive,visibility,createdBy,true)
}
private fun RecurringBill.toDto(space:String,user:String)=RecurringBillDto(id,space,amount,category,payer,description,nextDate,frequency,isActive,visibility,user)
@Serializable data class SavingsGoalDto(val id:String,@SerialName("space_id") val spaceId:String,val name:String,@SerialName("target_amount") val targetAmount:Double,@SerialName("saved_amount") val savedAmount:Double,@SerialName("monthly_contribution") val monthlyContribution:Double,@SerialName("target_date") val targetDate:String?=null,@SerialName("created_by") val createdBy:String) {
    fun toLocal()=SavingsGoal(id,spaceId,name,targetAmount,savedAmount,monthlyContribution,targetDate,createdBy,true)
}
private fun SavingsGoal.toDto(space:String,user:String)=SavingsGoalDto(id,space,name,targetAmount,savedAmount,monthlyContribution,targetDate,user)
@Serializable data class ExpenseEventDto(val id:Long,@SerialName("expense_id") val expenseId:String,@SerialName("space_id") val spaceId:String,@SerialName("actor_id") val actorId:String?=null,@SerialName("event_type") val eventType:String,val visibility:String,val snapshot:JsonElement,@SerialName("occurred_at") val occurredAt:String) {
    fun toLocal()=ExpenseEvent(id,expenseId,spaceId,actorId,eventType,visibility,snapshot.toString(),occurredAt)
}
@Serializable data class SaveSavingsGoalArgs(@SerialName("p_id") val id:String,@SerialName("p_space_id") val spaceId:String,@SerialName("p_name") val name:String,@SerialName("p_target_amount") val targetAmount:Double,@SerialName("p_saved_amount") val savedAmount:Double,@SerialName("p_monthly_contribution") val monthlyContribution:Double,@SerialName("p_target_date") val targetDate:String?)
@Serializable data class MarkExpenseReviewedArgs(@SerialName("p_expense_id") val expenseId:String,@SerialName("p_space_id") val spaceId:String)
@Serializable data class BudgetDto(@SerialName("space_id") val spaceId:String,val category:String,@SerialName("monthly_limit") val monthlyLimit:Double)
@Serializable data class CoupleSettingsDto(@SerialName("space_id") val spaceId:String,@SerialName("emergency_goal") val emergencyGoal:Double,@SerialName("emergency_saved") val emergencySaved:Double,@SerialName("monthly_contribution") val monthlyContribution:Double,@SerialName("monthly_income") val monthlyIncome:Double=0.0,@SerialName("available_balance") val availableBalance:Double=0.0,@SerialName("balance_as_of") val balanceAsOf:String="",@SerialName("next_payday") val nextPayday:String?=null)
