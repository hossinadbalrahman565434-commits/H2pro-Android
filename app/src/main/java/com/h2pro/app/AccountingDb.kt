package com.h2pro.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccountingDb(context: Context) : SQLiteOpenHelper(context, "h2pro_accounting.db", null, 1) {
    private val now: String get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL)")
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, sku TEXT, qty INTEGER NOT NULL DEFAULT 0, cost INTEGER NOT NULL DEFAULT 0, price INTEGER NOT NULL DEFAULT 0, reorder INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE parties(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, kind TEXT NOT NULL, balance INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE journal(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, description TEXT NOT NULL, debit TEXT NOT NULL, credit TEXT NOT NULL, amount INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, party_id INTEGER, product_id INTEGER, qty INTEGER, unit_price INTEGER, total INTEGER, paid INTEGER, note TEXT)")
        db.execSQL("CREATE TABLE purchases(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, party_id INTEGER, product_id INTEGER, qty INTEGER, unit_cost INTEGER, total INTEGER, paid INTEGER, note TEXT)")
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, account TEXT NOT NULL, amount INTEGER, note TEXT)")
        db.execSQL("CREATE TABLE receipts(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, party_id INTEGER, amount INTEGER, note TEXT)")
        seedAccounts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun seedAccounts(db: SQLiteDatabase) {
        val rows = listOf(
            arrayOf(1000, "1000", "الصندوق", "ASSET"),
            arrayOf(1010, "1010", "البنك", "ASSET"),
            arrayOf(1100, "1100", "العملاء", "ASSET"),
            arrayOf(1200, "1200", "المخزون", "ASSET"),
            arrayOf(2000, "2000", "الموردون", "LIABILITY"),
            arrayOf(3000, "3000", "رأس المال", "EQUITY"),
            arrayOf(4000, "4000", "المبيعات", "REVENUE"),
            arrayOf(4100, "4100", "إيرادات أخرى", "REVENUE"),
            arrayOf(5000, "5000", "تكلفة المبيعات", "EXPENSE"),
            arrayOf(6000, "6000", "المصروفات التشغيلية", "EXPENSE")
        )
        rows.forEach { r ->
            val v = ContentValues().apply { put("id", r[0] as Int); put("code", r[1] as String); put("name", r[2] as String); put("type", r[3] as String) }
            db.insert("accounts", null, v)
        }
    }

    fun dashboard(): Map<String, Long> {
        val db = readableDatabase
        fun sum(sql: String): Long = db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        return mapOf(
            "sales" to sum("SELECT COALESCE(SUM(amount),0) FROM journal WHERE credit='4000'"),
            "expenses" to sum("SELECT COALESCE(SUM(amount),0) FROM journal WHERE credit='1000' AND debit='6000'"),
            "cogs" to sum("SELECT COALESCE(SUM(amount),0) FROM journal WHERE debit='5000'"),
            "cash" to sum("SELECT COALESCE(SUM(CASE WHEN debit='1000' THEN amount ELSE 0 END),0)-COALESCE(SUM(CASE WHEN credit='1000' THEN amount ELSE 0 END),0) FROM journal"),
            "bank" to sum("SELECT COALESCE(SUM(CASE WHEN debit='1010' THEN amount ELSE 0 END),0)-COALESCE(SUM(CASE WHEN credit='1010' THEN amount ELSE 0 END),0) FROM journal"),
            "receivables" to sum("SELECT COALESCE(SUM(CASE WHEN debit='1100' THEN amount ELSE 0 END),0)-COALESCE(SUM(CASE WHEN credit='1100' THEN amount ELSE 0 END),0) FROM journal"),
            "payables" to sum("SELECT COALESCE(SUM(CASE WHEN credit='2000' THEN amount ELSE 0 END),0)-COALESCE(SUM(CASE WHEN debit='2000' THEN amount ELSE 0 END),0) FROM journal"),
            "inventory" to sum("SELECT COALESCE(SUM(CASE WHEN debit='1200' THEN amount ELSE 0 END),0)-COALESCE(SUM(CASE WHEN credit='1200' THEN amount ELSE 0 END),0) FROM journal")
        )
    }

    fun addProduct(name: String, sku: String, qty: Int, cost: Long, price: Long, reorder: Int) {
        val v = ContentValues().apply { put("name", name); put("sku", sku); put("qty", qty); put("cost", cost); put("price", price); put("reorder", reorder) }
        writableDatabase.insert("products", null, v)
        if (qty > 0 && cost > 0) journal("إضافة مخزون $name", "1200", "3000", qty * cost)
    }

    fun addParty(name: String, phone: String, kind: String) {
        val v = ContentValues().apply { put("name", name); put("phone", phone); put("kind", kind); put("balance", 0) }
        writableDatabase.insert("parties", null, v)
    }

    fun addSale(productId: Long, partyId: Long?, qty: Int, price: Long, paid: Long, note: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val p = db.rawQuery("SELECT name, cost, qty FROM products WHERE id=?", arrayOf(productId.toString()))
            if (!p.moveToFirst()) throw IllegalArgumentException("المنتج غير موجود")
            val name = p.getString(0); val cost = p.getLong(1); val stock = p.getInt(2); p.close()
            if (qty <= 0 || qty > stock) throw IllegalArgumentException("الكمية غير كافية")
            val total = qty * price
            if (paid < 0 || paid > total) throw IllegalArgumentException("المدفوع غير صحيح")
            val due = total - paid
            val sale = ContentValues().apply { put("date", now); put("party_id", partyId); put("product_id", productId); put("qty", qty); put("unit_price", price); put("total", total); put("paid", paid); put("note", note) }
            db.insert("sales", null, sale)
            db.execSQL("UPDATE products SET qty=qty-? WHERE id=?", arrayOf(qty, productId))
            if (paid > 0) journal("تحصيل فاتورة بيع $name", "1000", "4000", paid)
            if (due > 0) {
                if (partyId == null) throw IllegalArgumentException("اختر عميلاً للبيع الآجل")
                journal("بيع آجل $name", "1100", "4000", due)
                db.execSQL("UPDATE parties SET balance=balance+? WHERE id=?", arrayOf(due, partyId))
            }
            journal("تكلفة بيع $name", "5000", "1200", qty * cost)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun addPurchase(productId: Long, partyId: Long?, qty: Int, cost: Long, paid: Long, note: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (qty <= 0 || cost <= 0 || paid < 0 || paid > qty * cost) throw IllegalArgumentException("بيانات الشراء غير صحيحة")
            val total = qty * cost; val due = total - paid
            val purchase = ContentValues().apply { put("date", now); put("party_id", partyId); put("product_id", productId); put("qty", qty); put("unit_cost", cost); put("total", total); put("paid", paid); put("note", note) }
            db.insert("purchases", null, purchase)
            db.execSQL("UPDATE products SET qty=qty+?, cost=? WHERE id=?", arrayOf(qty, cost, productId))
            if (paid > 0) journal("شراء نقدي", "1200", "1000", paid)
            if (due > 0) {
                if (partyId == null) throw IllegalArgumentException("اختر مورداً للشراء الآجل")
                journal("شراء آجل", "1200", "2000", due)
                db.execSQL("UPDATE parties SET balance=balance+? WHERE id=?", arrayOf(due, partyId))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun addExpense(amount: Long, note: String) {
        if (amount <= 0) throw IllegalArgumentException("المبلغ غير صحيح")
        val v = ContentValues().apply { put("date", now); put("account", "6000"); put("amount", amount); put("note", note) }
        writableDatabase.insert("expenses", null, v)
        journal("مصروف: $note", "6000", "1000", amount)
    }

    fun addReceipt(partyId: Long, amount: Long, note: String) {
        if (amount <= 0) throw IllegalArgumentException("المبلغ غير صحيح")
        val db = writableDatabase
        val v = ContentValues().apply { put("date", now); put("party_id", partyId); put("amount", amount); put("note", note) }
        db.insert("receipts", null, v)
        db.execSQL("UPDATE parties SET balance=MAX(balance-?,0) WHERE id=?", arrayOf(amount, partyId))
        journal("تحصيل من عميل", "1000", "1100", amount)
    }

    fun journal(description: String, debit: String, credit: String, amount: Long) {
        require(amount > 0) { "المبلغ يجب أن يكون أكبر من صفر" }
        require(debit != credit) { "لا يمكن أن يكون المدين والدائن نفس الحساب" }
        val db = writableDatabase
        val valid = db.rawQuery("SELECT COUNT(*) FROM accounts WHERE code IN (?,?)", arrayOf(debit, credit)).use { it.moveToFirst() && it.getInt(0) == 2 }
        require(valid) { "كود الحساب غير موجود" }
        val v = ContentValues().apply { put("date", now); put("description", description.ifBlank { "قيد محاسبي" }); put("debit", debit); put("credit", credit); put("amount", amount) }
        db.insert("journal", null, v)
    }

    fun products(): List<String> = readableDatabase.rawQuery("SELECT id,name,qty,price,cost FROM products ORDER BY name", null).use { c ->
        buildList { while (c.moveToNext()) add("${c.getLong(0)}|${c.getString(1)}|الكمية ${c.getInt(2)}|بيع ${c.getLong(3)}|شراء ${c.getLong(4)}") }
    }
    fun parties(kind: String): List<String> = readableDatabase.rawQuery("SELECT id,name,phone,balance FROM parties WHERE kind=? ORDER BY name", arrayOf(kind)).use { c ->
        buildList { while (c.moveToNext()) add("${c.getLong(0)}|${c.getString(1)}|${c.getString(2)}|رصيد ${c.getLong(3)}") }
    }
    fun trialBalance(): List<String> = readableDatabase.rawQuery("SELECT a.code,a.name,COALESCE(SUM(CASE WHEN j.debit=a.code THEN j.amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN j.credit=a.code THEN j.amount ELSE 0 END),0) FROM accounts a LEFT JOIN journal j ON j.debit=a.code OR j.credit=a.code GROUP BY a.code,a.name ORDER BY a.code", null).use { c ->
        buildList { while (c.moveToNext()) add("${c.getString(0)} • ${c.getString(1)} • مدين ${c.getLong(2)} • دائن ${c.getLong(3)}") }
    }
    fun journalRows(): List<String> = readableDatabase.rawQuery("SELECT date,description,debit,credit,amount FROM journal ORDER BY id DESC LIMIT 100", null).use { c ->
        buildList { while (c.moveToNext()) add("${c.getString(0)}\n${c.getString(1)}\nمدين ${c.getString(2)} ← دائن ${c.getString(3)} : ${c.getLong(4)}") }
    }
    fun productLowStock(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM products WHERE qty<=reorder", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
