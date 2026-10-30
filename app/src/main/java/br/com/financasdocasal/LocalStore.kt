package br.com.financasdocasal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class LocalStore(context: Context) : SQLiteOpenHelper(context, "financas.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE expenses(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT NOT NULL UNIQUE,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                payer TEXT NOT NULL,
                description TEXT NOT NULL,
                date TEXT NOT NULL,
                created_by TEXT,
                synced INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN remote_id TEXT")
            db.execSQL("ALTER TABLE expenses ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            val ids = mutableListOf<Long>()
            db.rawQuery("SELECT id FROM expenses WHERE remote_id IS NULL", null).use { cursor ->
                while (cursor.moveToNext()) ids += cursor.getLong(0)
            }
            ids.forEach { id ->
                db.execSQL("UPDATE expenses SET remote_id=? WHERE id=?", arrayOf(UUID.randomUUID().toString(), id))
            }
            db.execSQL("CREATE UNIQUE INDEX expenses_remote_id_idx ON expenses(remote_id)")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE expenses ADD COLUMN created_by TEXT")
    }

    fun add(expense: Expense): Expense {
        val id = writableDatabase.insertWithOnConflict("expenses", null, expenseValues(expense, expense.synced), SQLiteDatabase.CONFLICT_REPLACE)
        return expense.copy(id = id)
    }

    fun all(): List<Expense> = query("SELECT id, remote_id, amount, category, payer, description, date, created_by, synced FROM expenses ORDER BY date DESC, id DESC")

    fun pending(): List<Expense> = query("SELECT id, remote_id, amount, category, payer, description, date, created_by, synced FROM expenses WHERE synced=0 ORDER BY id ASC")

    fun markSynced(remoteId: String, createdBy: String) {
        writableDatabase.execSQL("UPDATE expenses SET synced=1, created_by=? WHERE remote_id=?", arrayOf(createdBy, remoteId))
    }

    fun upsertRemote(expense: Expense) {
        writableDatabase.insertWithOnConflict("expenses", null, expenseValues(expense, synced = true), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(remoteId: String) {
        writableDatabase.delete("expenses", "remote_id=?", arrayOf(remoteId))
    }

    fun importJson(raw: String) {
        val array = JSONArray(raw)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val amount = item.optDouble("valor", item.optDouble("amount", 0.0))
                if (amount <= 0) continue
                add(
                    Expense(
                        amount = amount,
                        category = item.optString("categoria", item.optString("category", "Outros")),
                        payer = item.optString("quem", item.optString("payer", "Não informado")),
                        description = item.optString("descricao", item.optString("description", "Importado")),
                        date = item.optString("data", item.optString("date", "")).ifBlank { LocalDate.now().toString() }
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun exportJson(): String {
        val array = JSONArray()
        all().forEach { expense ->
            array.put(
                JSONObject()
                    .put("valor", expense.amount)
                    .put("categoria", expense.category)
                    .put("quem", expense.payer)
                    .put("descricao", expense.description)
                    .put("data", expense.date)
            )
        }
        return array.toString()
    }

    private fun expenseValues(expense: Expense, synced: Boolean) = ContentValues().apply {
        put("remote_id", expense.remoteId)
        put("amount", expense.amount)
        put("category", expense.category)
        put("payer", expense.payer)
        put("description", expense.description)
        put("date", expense.date)
        put("created_by", expense.createdBy)
        put("synced", if (synced) 1 else 0)
    }

    private fun query(sql: String): List<Expense> {
        val expenses = mutableListOf<Expense>()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                expenses += Expense(
                    id = cursor.getLong(0),
                    remoteId = cursor.getString(1),
                    amount = cursor.getDouble(2),
                    category = cursor.getString(3),
                    payer = cursor.getString(4),
                    description = cursor.getString(5),
                    date = cursor.getString(6),
                    createdBy = if (cursor.isNull(7)) null else cursor.getString(7),
                    synced = cursor.getInt(8) != 0
                )
            }
        }
        return expenses
    }
}
