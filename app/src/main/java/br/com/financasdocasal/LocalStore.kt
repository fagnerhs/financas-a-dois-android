package br.com.financasdocasal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class LocalStore(context: Context) : SQLiteOpenHelper(context, "financas.db", null, 7) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,remote_id TEXT NOT NULL UNIQUE,amount TEXT NOT NULL,category TEXT NOT NULL,payer TEXT NOT NULL,description TEXT NOT NULL,date TEXT NOT NULL,created_by TEXT,synced INTEGER NOT NULL DEFAULT 0,visibility TEXT NOT NULL DEFAULT 'shared',creator_share_amount TEXT NOT NULL DEFAULT '0',review_status TEXT NOT NULL DEFAULT 'pending',reviewed_by TEXT,reviewed_at TEXT,kind TEXT NOT NULL DEFAULT 'expense')""")
        createProductTables(db)
    }
    private fun createProductTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS recurring_bills(id TEXT PRIMARY KEY,space_id TEXT NOT NULL,amount TEXT NOT NULL,category TEXT NOT NULL,payer TEXT NOT NULL,description TEXT NOT NULL,next_date TEXT NOT NULL,frequency TEXT NOT NULL,is_active INTEGER NOT NULL,visibility TEXT NOT NULL,created_by TEXT,synced INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS savings_goals(id TEXT PRIMARY KEY,space_id TEXT NOT NULL,name TEXT NOT NULL,target_amount TEXT NOT NULL,saved_amount TEXT NOT NULL,monthly_contribution TEXT NOT NULL,target_date TEXT,created_by TEXT,synced INTEGER NOT NULL DEFAULT 0)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN remote_id TEXT"); db.execSQL("ALTER TABLE expenses ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            val ids = mutableListOf<Long>(); db.rawQuery("SELECT id FROM expenses WHERE remote_id IS NULL", null).use { c -> while(c.moveToNext()) ids += c.getLong(0) }
            ids.forEach { db.execSQL("UPDATE expenses SET remote_id=? WHERE id=?", arrayOf<Any?>(UUID.randomUUID().toString(), it)) }; db.execSQL("CREATE UNIQUE INDEX expenses_remote_id_idx ON expenses(remote_id)")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE expenses ADD COLUMN created_by TEXT")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN visibility TEXT NOT NULL DEFAULT 'shared'")
            db.execSQL("ALTER TABLE expenses ADD COLUMN creator_share_amount REAL NOT NULL DEFAULT 0")
            db.execSQL("UPDATE expenses SET creator_share_amount=amount")
            createProductTables(db)
            encryptLegacyExpenses(db)
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN review_status TEXT NOT NULL DEFAULT 'legacy'")
            db.execSQL("ALTER TABLE expenses ADD COLUMN reviewed_by TEXT")
            db.execSQL("ALTER TABLE expenses ADD COLUMN reviewed_at TEXT")
            encryptLegacyCreatorShares(db)
        }
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE expenses_v6(id INTEGER PRIMARY KEY AUTOINCREMENT,remote_id TEXT NOT NULL UNIQUE,amount TEXT NOT NULL,category TEXT NOT NULL,payer TEXT NOT NULL,description TEXT NOT NULL,date TEXT NOT NULL,created_by TEXT,synced INTEGER NOT NULL DEFAULT 0,visibility TEXT NOT NULL DEFAULT 'shared',creator_share_amount TEXT NOT NULL DEFAULT '0',review_status TEXT NOT NULL DEFAULT 'pending',reviewed_by TEXT,reviewed_at TEXT,kind TEXT NOT NULL DEFAULT 'expense')")
            db.execSQL("INSERT INTO expenses_v6(id,remote_id,amount,category,payer,description,date,created_by,synced,visibility,creator_share_amount,review_status,reviewed_by,reviewed_at,kind) SELECT id,remote_id,amount,category,payer,description,date,created_by,synced,visibility,creator_share_amount,review_status,reviewed_by,reviewed_at,'expense' FROM expenses")
            db.execSQL("DROP TABLE expenses")
            db.execSQL("ALTER TABLE expenses_v6 RENAME TO expenses")
        }
        if (oldVersion == 6) db.execSQL("ALTER TABLE expenses ADD COLUMN kind TEXT NOT NULL DEFAULT 'expense'")
    }

    fun add(expense: Expense): Expense {
        val stable = if (runCatching { UUID.fromString(expense.remoteId) }.isSuccess) expense else expense.copy(remoteId = UUID.randomUUID().toString())
        val id = writableDatabase.insertWithOnConflict("expenses", null, expenseValues(stable, stable.synced), SQLiteDatabase.CONFLICT_REPLACE)
        return stable.copy(id = id)
    }
    fun recordRecurringPayment(expense: Expense, advancedBill: RecurringBill): Pair<Expense, RecurringBill> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val savedExpense = add(expense)
            val savedBill = addRecurringBill(advancedBill)
            db.setTransactionSuccessful()
            return savedExpense to savedBill
        } finally { db.endTransaction() }
    }
    fun all(): List<Expense> = query("SELECT id,remote_id,amount,category,payer,description,date,created_by,synced,visibility,creator_share_amount,review_status,reviewed_by,reviewed_at,kind FROM expenses ORDER BY date DESC,id DESC")
    fun pending(): List<Expense> = query("SELECT id,remote_id,amount,category,payer,description,date,created_by,synced,visibility,creator_share_amount,review_status,reviewed_by,reviewed_at,kind FROM expenses WHERE synced=0 ORDER BY id ASC")
    fun markSynced(remoteId: String, createdBy: String) { writableDatabase.execSQL("UPDATE expenses SET synced=1,created_by=? WHERE remote_id=?", arrayOf(createdBy, remoteId)) }
    fun upsertRemote(expense: Expense) { writableDatabase.insertWithOnConflict("expenses", null, expenseValues(expense, true), SQLiteDatabase.CONFLICT_REPLACE) }
    fun delete(remoteId: String) { writableDatabase.delete("expenses", "remote_id=?", arrayOf(remoteId)) }
    fun reconcileVisibleExpenses(remoteIds: Set<String>) = deleteMissingSynced("expenses", "remote_id", remoteIds)

    fun addRecurringBill(bill: RecurringBill): RecurringBill { val b = bill.withStableId(); writableDatabase.insertWithOnConflict("recurring_bills", null, billValues(b), SQLiteDatabase.CONFLICT_REPLACE); return b }
    fun recurringBills(): List<RecurringBill> = readBills("SELECT * FROM recurring_bills ORDER BY next_date,id")
    fun pendingRecurringBills(): List<RecurringBill> = readBills("SELECT * FROM recurring_bills WHERE synced=0 ORDER BY next_date,id")
    fun markRecurringBillSynced(id: String) { writableDatabase.execSQL("UPDATE recurring_bills SET synced=1 WHERE id=?", arrayOf(id)) }
    fun upsertRemoteRecurringBill(bill: RecurringBill) { writableDatabase.insertWithOnConflict("recurring_bills", null, billValues(bill.copy(synced=true)), SQLiteDatabase.CONFLICT_REPLACE) }
    fun deleteRecurringBill(id: String) { writableDatabase.delete("recurring_bills", "id=?", arrayOf(id)) }
    fun reconcileVisibleRecurringBills(ids: Set<String>) = deleteMissingSynced("recurring_bills", "id", ids)

    fun addSavingsGoal(goal: SavingsGoal): SavingsGoal { val g=goal.withStableId(); writableDatabase.insertWithOnConflict("savings_goals",null,goalValues(g),SQLiteDatabase.CONFLICT_REPLACE); return g }
    fun savingsGoals(): List<SavingsGoal> = readGoals("SELECT * FROM savings_goals ORDER BY target_date,id")
    fun pendingSavingsGoals(): List<SavingsGoal> = readGoals("SELECT * FROM savings_goals WHERE synced=0 ORDER BY id")
    fun markSavingsGoalSynced(id: String) { writableDatabase.execSQL("UPDATE savings_goals SET synced=1 WHERE id=?", arrayOf(id)) }
    fun upsertRemoteSavingsGoal(goal: SavingsGoal) { writableDatabase.insertWithOnConflict("savings_goals",null,goalValues(goal.copy(synced=true)),SQLiteDatabase.CONFLICT_REPLACE) }
    fun deleteSavingsGoal(id: String) { writableDatabase.delete("savings_goals", "id=?", arrayOf(id)) }
    fun reconcileVisibleSavingsGoals(ids: Set<String>) = deleteMissingSynced("savings_goals", "id", ids)
    /** Remove only cloud-cached rows during an account switch; the remote data and offline queues remain untouched. */
    fun clearSyncedCache() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("expenses", "synced=1", null)
            db.delete("recurring_bills", "synced=1", null)
            db.delete("savings_goals", "synced=1", null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** Adopt pre-cloud offline rows once, for the first account linked on this installation. */
    fun claimLegacyPending(ownerId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE expenses SET created_by=? WHERE synced=0 AND created_by IS NULL", arrayOf(ownerId))
            db.execSQL("UPDATE recurring_bills SET created_by=? WHERE synced=0 AND created_by IS NULL", arrayOf(ownerId))
            db.execSQL("UPDATE savings_goals SET created_by=? WHERE synced=0 AND created_by IS NULL", arrayOf(ownerId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** Events are remote audit data; this maps DTO values without persisting sensitive snapshots. */
    fun expenseEvents(events: List<ExpenseEvent>): List<ExpenseEvent> = events

    fun importJson(raw: String, ownerId: String? = null) {
        val root = runCatching { JSONObject(raw) }.getOrNull()
        val expenseArray = if (root == null) runCatching { JSONArray(raw) }.getOrElse { throw IllegalArgumentException("Formato de backup inválido") }
            else root.optJSONArray("expenses") ?: root.optJSONArray("data") ?: JSONArray()
        val version = root?.optInt("schemaVersion", 0) ?: 0
        require(version in 0..4) { "Versão de backup não suportada" }
        require(expenseArray.length() > 0 || root?.has("recurringBills") == true || root?.has("savingsGoals") == true) { "Nenhum dado de backup reconhecido" }
        writableDatabase.beginTransaction()
        try {
            for (i in 0 until expenseArray.length()) {
                val item=expenseArray.optJSONObject(i) ?: continue
                val amount=item.optDouble("valor",item.optDouble("amount",0.0))
                if(!amount.isFinite() || amount !in 0.01..1_000_000.0) continue
                val visibility=item.optString("visibility","shared").takeIf { it in setOf("shared","private","balances_only") } ?: "shared"
                val share=item.optDouble("creator_share_amount",item.optDouble("creatorShareAmount",amount)).let { if(it.isFinite() && it in 0.0..amount) it else amount }
                val date=runCatching { LocalDate.parse(item.optString("data",item.optString("date",LocalDate.now().toString()))) }.getOrElse { LocalDate.now() }.toString()
                val kind=item.optString("kind",EntryKind.EXPENSE).takeIf { it in setOf(EntryKind.EXPENSE,EntryKind.INCOME) } ?: EntryKind.EXPENSE
                val source=item.optString("categoria",item.optString("category","Outros")).trim()
                if (kind==EntryKind.INCOME && (source.isEmpty() || source.length>60)) continue
                val category=if(kind==EntryKind.INCOME) source else source.takeIf { it in Categories.all } ?: "Outros"
                val safeVisibility=if(kind==EntryKind.INCOME && visibility=="balances_only") "shared" else visibility
                add(Expense(amount,category,item.optString("quem",item.optString("payer","Não informado")).take(60),item.optString("descricao",item.optString("description","Importado")).take(240),date,createdBy=ownerId,visibility=safeVisibility,creatorShareAmount=if(safeVisibility=="private") amount else share,reviewStatus="pending",kind=kind))
            }
            root?.optJSONArray("recurringBills")?.let { a -> for(i in 0 until a.length()) a.optJSONObject(i)?.let { o ->
                val amount=o.optDouble("amount",0.0); val frequency=o.optString("frequency","monthly")
                val date=runCatching { LocalDate.parse(o.optString("nextDate")) }.getOrNull()
                if(amount.isFinite() && amount in 0.01..1_000_000.0 && date!=null && frequency in setOf("weekly","biweekly","monthly","yearly")) {
                    val category=o.optString("category","Outros").takeIf { it in Categories.all } ?: "Outros"
                    val visibility=o.optString("visibility","shared").takeIf { it in setOf("shared","private") } ?: "shared"
                    addRecurringBill(RecurringBill(id=UUID.randomUUID().toString(),amount=amount,category=category,payer=o.optString("payer","Não informado").take(60),description=o.optString("description").take(240),nextDate=date.toString(),frequency=frequency,isActive=o.optBoolean("isActive",true),visibility=visibility,createdBy=ownerId))
                }
            } }
            root?.optJSONArray("savingsGoals")?.let { a -> for(i in 0 until a.length()) a.optJSONObject(i)?.let { o ->
                val target=o.optDouble("targetAmount",0.0); val saved=o.optDouble("savedAmount",0.0); val monthly=o.optDouble("monthlyContribution",0.0)
                val date=o.optString("targetDate").takeIf { it.isNotBlank() && runCatching { LocalDate.parse(it) }.isSuccess }
                if(o.optString("name").isNotBlank() && o.optString("name").length<=100 && target.isFinite() && target>0 && saved.isFinite() && saved>=0 && monthly.isFinite() && monthly>=0) {
                    addSavingsGoal(SavingsGoal(id=UUID.randomUUID().toString(),name=o.optString("name"),targetAmount=target,savedAmount=saved,monthlyContribution=monthly,targetDate=date,createdBy=ownerId))
                }
            } }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun exportJson(ownerId: String? = null): String {
        fun ownedOrSynced(synced: Boolean, createdBy: String?) = ownerId == null || synced || createdBy == ownerId
        val ex=JSONArray(); all().filter { ownedOrSynced(it.synced,it.createdBy) }.forEach { ex.put(JSONObject().put("amount",it.amount).put("category",it.category).put("payer",it.payer).put("description",it.description).put("date",it.date).put("visibility",it.visibility).put("creator_share_amount",it.creatorShareAmount).put("kind",it.kind)) }
        val bills=JSONArray(); recurringBills().filter { ownedOrSynced(it.synced,it.createdBy) }.forEach { bills.put(JSONObject().put("amount",it.amount).put("category",it.category).put("payer",it.payer).put("description",it.description).put("nextDate",it.nextDate).put("frequency",it.frequency).put("isActive",it.isActive).put("visibility",it.visibility)) }
        val goals=JSONArray(); savingsGoals().filter { ownedOrSynced(it.synced,it.createdBy) }.forEach { goals.put(JSONObject().put("name",it.name).put("targetAmount",it.targetAmount).put("savedAmount",it.savedAmount).put("monthlyContribution",it.monthlyContribution).put("targetDate",it.targetDate)) }
        return JSONObject().put("schemaVersion",4).put("expenses",ex).put("recurringBills",bills).put("savingsGoals",goals).toString()
    }
    private fun expenseValues(e:Expense,synced:Boolean)=ContentValues().apply { put("remote_id",e.remoteId); put("amount",LocalFieldCrypto.encrypt(e.amount.toString())); put("category",LocalFieldCrypto.encrypt(e.category)); put("payer",LocalFieldCrypto.encrypt(e.payer)); put("description",LocalFieldCrypto.encrypt(e.description)); put("date",e.date); put("created_by",e.createdBy); put("synced",if(synced)1 else 0); put("visibility",e.visibility); put("creator_share_amount",LocalFieldCrypto.encrypt(e.creatorShareAmount.toString())); put("review_status",e.reviewStatus); put("reviewed_by",e.reviewedBy); put("reviewed_at",e.reviewedAt); put("kind",if(e.kind in setOf(EntryKind.EXPENSE,EntryKind.INCOME)) e.kind else EntryKind.EXPENSE) }
    private fun query(sql:String):List<Expense> { val out=mutableListOf<Expense>(); readableDatabase.rawQuery(sql,null).use { c -> while(c.moveToNext()) out+=Expense(id=c.getLong(0),remoteId=c.getString(1),amount=LocalFieldCrypto.decrypt(c.getString(2)).toDouble(),category=LocalFieldCrypto.decrypt(c.getString(3)),payer=LocalFieldCrypto.decrypt(c.getString(4)),description=LocalFieldCrypto.decrypt(c.getString(5)),date=c.getString(6),createdBy=if(c.isNull(7))null else c.getString(7),synced=c.getInt(8)!=0,visibility=c.getString(9),creatorShareAmount=LocalFieldCrypto.decrypt(c.getString(10)).toDouble(),reviewStatus=c.getString(11),reviewedBy=if(c.isNull(12))null else c.getString(12),reviewedAt=if(c.isNull(13))null else c.getString(13),kind=c.getString(14)) }; return out }
    private fun billValues(b:RecurringBill)=ContentValues().apply { put("id",b.id); put("space_id",b.spaceId); put("amount",LocalFieldCrypto.encrypt(b.amount.toString())); put("category",LocalFieldCrypto.encrypt(b.category)); put("payer",LocalFieldCrypto.encrypt(b.payer)); put("description",LocalFieldCrypto.encrypt(b.description)); put("next_date",b.nextDate); put("frequency",b.frequency); put("is_active",if(b.isActive)1 else 0); put("visibility",b.visibility); put("created_by",b.createdBy); put("synced",if(b.synced)1 else 0) }
    private fun readBills(sql:String):List<RecurringBill> { val out=mutableListOf<RecurringBill>(); readableDatabase.rawQuery(sql,null).use { c -> while(c.moveToNext()) out+=RecurringBill(c.getString(0),c.getString(1),LocalFieldCrypto.decrypt(c.getString(2)).toDouble(),LocalFieldCrypto.decrypt(c.getString(3)),LocalFieldCrypto.decrypt(c.getString(4)),LocalFieldCrypto.decrypt(c.getString(5)),c.getString(6),c.getString(7),c.getInt(8)!=0,c.getString(9),if(c.isNull(10))null else c.getString(10),c.getInt(11)!=0) }; return out }
    private fun goalValues(g:SavingsGoal)=ContentValues().apply { put("id",g.id); put("space_id",g.spaceId); put("name",LocalFieldCrypto.encrypt(g.name)); put("target_amount",LocalFieldCrypto.encrypt(g.targetAmount.toString())); put("saved_amount",LocalFieldCrypto.encrypt(g.savedAmount.toString())); put("monthly_contribution",LocalFieldCrypto.encrypt(g.monthlyContribution.toString())); put("target_date",g.targetDate); put("created_by",g.createdBy); put("synced",if(g.synced)1 else 0) }
    private fun readGoals(sql:String):List<SavingsGoal> { val out=mutableListOf<SavingsGoal>(); readableDatabase.rawQuery(sql,null).use { c -> while(c.moveToNext()) out+=SavingsGoal(c.getString(0),c.getString(1),LocalFieldCrypto.decrypt(c.getString(2)),LocalFieldCrypto.decrypt(c.getString(3)).toDouble(),LocalFieldCrypto.decrypt(c.getString(4)).toDouble(),LocalFieldCrypto.decrypt(c.getString(5)).toDouble(),if(c.isNull(6))null else c.getString(6),if(c.isNull(7))null else c.getString(7),c.getInt(8)!=0) }; return out }
    private fun encryptLegacyExpenses(db: SQLiteDatabase) {
        val rows = mutableListOf<Pair<Long, ContentValues>>()
        db.rawQuery("SELECT id,amount,category,payer,description,creator_share_amount FROM expenses", null).use { c ->
            while (c.moveToNext()) {
                val values = ContentValues()
                values.put("amount", LocalFieldCrypto.encrypt(c.getString(1)))
                values.put("category", LocalFieldCrypto.encrypt(c.getString(2)))
                values.put("payer", LocalFieldCrypto.encrypt(c.getString(3)))
                values.put("description", LocalFieldCrypto.encrypt(c.getString(4)))
                values.put("creator_share_amount", LocalFieldCrypto.encrypt(c.getString(5)))
                rows += c.getLong(0) to values
            }
        }
        rows.forEach { (id, values) -> db.update("expenses", values, "id=?", arrayOf(id.toString())) }
    }
    private fun encryptLegacyCreatorShares(db: SQLiteDatabase) {
        val rows = mutableListOf<Pair<Long, String>>()
        db.rawQuery("SELECT id,creator_share_amount FROM expenses", null).use { c ->
            while (c.moveToNext()) rows += c.getLong(0) to LocalFieldCrypto.encrypt(c.getString(1))
        }
        rows.forEach { (id, encrypted) ->
            db.execSQL("UPDATE expenses SET creator_share_amount=? WHERE id=?", arrayOf<Any?>(encrypted, id))
        }
    }
    private fun deleteMissingSynced(table: String, idColumn: String, ids: Set<String>) {
        require(table in setOf("expenses", "recurring_bills", "savings_goals"))
        require(idColumn in setOf("remote_id", "id"))
        val db = writableDatabase
        if (ids.isEmpty()) {
            db.delete(table, "synced=1", null)
            return
        }
        db.beginTransaction()
        try {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS visible_remote_ids(id TEXT PRIMARY KEY)")
            db.execSQL("DELETE FROM visible_remote_ids")
            ids.forEach { id -> db.execSQL("INSERT OR IGNORE INTO visible_remote_ids(id) VALUES (?)", arrayOf(id)) }
            db.execSQL("DELETE FROM $table WHERE synced=1 AND NOT EXISTS (SELECT 1 FROM visible_remote_ids v WHERE v.id=$table.$idColumn)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    private fun RecurringBill.withStableId()=if(runCatching{UUID.fromString(id)}.isSuccess)this else copy(id=UUID.randomUUID().toString())
    private fun SavingsGoal.withStableId()=if(runCatching{UUID.fromString(id)}.isSuccess)this else copy(id=UUID.randomUUID().toString())
}
