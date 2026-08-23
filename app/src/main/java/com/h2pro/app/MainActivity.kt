package com.h2pro.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var db: AccountingDb
    private lateinit var content: LinearLayout
    private val fmt = NumberFormat.getIntegerInstance(Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AccountingDb(this)
        buildShell()
        showDashboard()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(247,249,252)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val header = TextView(this).apply { text = "H2pro • المحاسبة الذكية"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(21,101,192)); gravity = Gravity.CENTER_VERTICAL; setPadding(24,28,24,28) }
        root.addView(header)
        val nav = HorizontalScrollView(this)
        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8,8,8,8) }
        listOf("الرئيسية","المبيعات","المشتريات","المصروفات","العملاء","الموردون","المخزون","القيود","التقارير").forEach { label ->
            val b = Button(this).apply { text = label; isAllCaps = false }
            b.setOnClickListener { when(label){
                "الرئيسية" -> showDashboard(); "المبيعات" -> showSales(); "المشتريات" -> showPurchases(); "المصروفات" -> showExpenses();
                "العملاء" -> showParties("CUSTOMER"); "الموردون" -> showParties("SUPPLIER"); "المخزون" -> showProducts(); "القيود" -> showJournal(); "التقارير" -> showReports()
            } }
            navRow.addView(b)
        }
        nav.addView(navRow); root.addView(nav)
        val scroll = ScrollView(this); content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,32); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }

    private fun title(text: String) = TextView(this).apply { this.text=text; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30,40,55)); setPadding(4,10,4,16) }
    private fun card(label: String, value: String): TextView = TextView(this).apply { text="$label\n$value ريال"; textSize=17f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30,55,80)); setBackgroundColor(Color.WHITE); setPadding(20,18,20,18); layoutParams=LinearLayout.LayoutParams(-1, -2).apply{setMargins(0,6,0,6)} }
    private fun button(text:String, action:()->Unit): Button = Button(this).apply { this.text=text; isAllCaps=false; setOnClickListener{action()} }
    private fun field(hint:String, number:Boolean=false): EditText = EditText(this).apply { this.hint=hint; textSize=16f; setPadding(18,10,18,10); if(number) inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
    private fun money(s:String)=s.replace(",","").trim().toLongOrNull() ?: 0L
    private fun int(s:String)=s.trim().toIntOrNull() ?: 0
    private fun set(items:List<String>, empty:String="لا توجد بيانات"){
        content.removeAllViews()
        if(items.isEmpty()) content.addView(TextView(this).apply{text=empty;textSize=18f;setPadding(20,20,20,20)})
        else items.forEach{content.addView(TextView(this).apply{text=it;textSize=16f;setPadding(12,16,12,16);setBackgroundColor(Color.WHITE);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,4,0,4)}})}
    }

    private fun showDashboard(){
        content.removeAllViews(); content.addView(title("لوحة التحكم"))
        val d=db.dashboard(); val profit=d.getValue("sales")-d.getValue("cogs")-d.getValue("expenses")
        listOf("المبيعات" to d.getValue("sales"),"المصروفات" to d.getValue("expenses"),"الأرباح التقديرية" to profit,"الصندوق" to d.getValue("cash"),"البنك" to d.getValue("bank"),"العملاء" to d.getValue("receivables"),"الموردون" to d.getValue("payables"),"المخزون" to d.getValue("inventory")).forEach{content.addView(card(it.first,fmt.format(it.second)))}
        content.addView(TextView(this).apply{text="تنبيه المخزون: ${db.productLowStock()} منتج عند/تحت حد إعادة الطلب";textSize=16f;setPadding(8,18,8,18)})
        content.addView(button("إضافة منتج"){productDialog()}); content.addView(button("إضافة عميل"){partyDialog("CUSTOMER")}); content.addView(button("إضافة مورد"){partyDialog("SUPPLIER")})
    }

    private fun showProducts(){ set(db.products()); content.addView(title("المخزون والمنتجات"),0); content.addView(button("+ منتج جديد"){productDialog()},1) }
    private fun productDialog(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)}; val name=field("اسم المنتج"); val sku=field("الباركود / SKU"); val qty=field("الكمية الابتدائية",true); val cost=field("سعر الشراء",true); val price=field("سعر البيع",true); val reorder=field("حد إعادة الطلب",true); listOf(name,sku,qty,cost,price,reorder).forEach{box.addView(it)}
        AlertDialog.Builder(this).setTitle("منتج جديد").setView(box).setPositiveButton("حفظ"){_,_-> try{db.addProduct(name.text.toString(),sku.text.toString(),int(qty.text.toString()),money(cost.text.toString()),money(price.text.toString()),int(reorder.text.toString()));showProducts()}catch(e:Exception){toast(e.message?:"خطأ")}}.setNegativeButton("إلغاء",null).show()
    }

    private fun showParties(kind:String){ set(db.parties(kind)); content.addView(title(if(kind=="CUSTOMER")"العملاء" else "الموردون"),0); content.addView(button("+ إضافة"){partyDialog(kind)},1) }
    private fun partyDialog(kind:String){ val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)}; val n=field("الاسم"); val p=field("الهاتف"); box.addView(n);box.addView(p); AlertDialog.Builder(this).setTitle(if(kind=="CUSTOMER")"عميل جديد" else "مورد جديد").setView(box).setPositiveButton("حفظ"){_,_->db.addParty(n.text.toString(),p.text.toString(),kind);showParties(kind)}.setNegativeButton("إلغاء",null).show() }

    private fun chooseProduct(onChosen:(Long)->Unit){ val rows=db.products(); if(rows.isEmpty()){toast("أضف منتجاً أولاً");return}; val labels=rows.map{it.split("|")[1]}; AlertDialog.Builder(this).setTitle("اختر المنتج").setItems(labels.toTypedArray()){_,which->onChosen(rows[which].substringBefore("|").toLong())}.show() }
    private fun chooseParty(kind:String,onChosen:(Long?)->Unit){ val rows=db.parties(kind); val labels=rows.map{it.split("|")[1]}; val arr=(listOf("نقدي")+labels).toTypedArray(); AlertDialog.Builder(this).setTitle("الحساب").setItems(arr){_,which->onChosen(if(which==0)null else rows[which-1].substringBefore("|").toLong())}.show() }

    private fun showSales(){ content.removeAllViews();content.addView(title("المبيعات والفواتير")); content.addView(button("+ فاتورة بيع"){saleDialog()}); content.addView(TextView(this).apply{text="الفواتير تحفظ محاسبياً وتحدّث المخزون والعملاء تلقائياً.";textSize=16f;setPadding(4,10,4,20)}) }
    private fun saleDialog(){ chooseProduct{pid-> chooseParty("CUSTOMER"){party-> val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)}; val q=field("الكمية",true);val price=field("سعر الوحدة",true);val paid=field("المدفوع",true);val note=field("ملاحظة");listOf(q,price,paid,note).forEach{box.addView(it)};AlertDialog.Builder(this).setTitle("فاتورة بيع").setView(box).setPositiveButton("ترحيل الفاتورة"){_,_->try{db.addSale(pid,party,int(q.text.toString()),money(price.text.toString()),money(paid.text.toString()),note.text.toString());toast("تم حفظ الفاتورة والقيد");showDashboard()}catch(e:Exception){toast(e.message?:"تعذر الحفظ")}}.setNegativeButton("إلغاء",null).show() } } }
    private fun showPurchases(){content.removeAllViews();content.addView(title("المشتريات"));content.addView(button("+ فاتورة شراء"){purchaseDialog()});content.addView(TextView(this).apply{text="المشتريات تحدّث المخزون وتثبت الموردين والالتزام المالي.";textSize=16f;setPadding(8,8,8,8)})}
    private fun purchaseDialog(){chooseProduct{pid->chooseParty("SUPPLIER"){party->val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)};val q=field("الكمية",true);val cost=field("تكلفة الوحدة",true);val paid=field("المدفوع",true);val note=field("ملاحظة");listOf(q,cost,paid,note).forEach{box.addView(it)};AlertDialog.Builder(this).setTitle("فاتورة شراء").setView(box).setPositiveButton("ترحيل"){_,_->try{db.addPurchase(pid,party,int(q.text.toString()),money(cost.text.toString()),money(paid.text.toString()),note.text.toString());toast("تم حفظ الشراء");showDashboard()}catch(e:Exception){toast(e.message?:"تعذر الحفظ")}}.setNegativeButton("إلغاء",null).show()}}}
    private fun showExpenses(){content.removeAllViews();content.addView(title("المصروفات"));content.addView(button("+ تسجيل مصروف"){expenseDialog()});content.addView(TextView(this).apply{text="كل مصروف يُرحّل مديناً للمصروف ودائناً للصندوق.";textSize=16f;setPadding(8,8,8,8)})}
    private fun expenseDialog(){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)};val amount=field("المبلغ",true);val note=field("بيان المصروف");box.addView(amount);box.addView(note);AlertDialog.Builder(this).setTitle("مصروف جديد").setView(box).setPositiveButton("حفظ"){_,_->try{db.addExpense(money(amount.text.toString()),note.text.toString());toast("تم تسجيل المصروف");showDashboard()}catch(e:Exception){toast(e.message?:"خطأ")}}.setNegativeButton("إلغاء",null).show()}

    private fun showJournal(){set(db.journalRows());content.addView(title("القيود اليومية"),0);content.addView(button("+ قيد يدوي"){manualJournalDialog()},1)}
    private fun manualJournalDialog(){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)};val desc=field("البيان");val debit=field("كود المدين (مثال 1000)");val credit=field("كود الدائن (مثال 4000)");val amount=field("المبلغ",true);listOf(desc,debit,credit,amount).forEach{box.addView(it)};AlertDialog.Builder(this).setTitle("قيد يومية متوازن").setView(box).setPositiveButton("ترحيل"){_,_->try{db.journal(desc.text.toString(),debit.text.toString(),credit.text.toString(),money(amount.text.toString()));showJournal()}catch(e:Exception){toast(e.message?:"القيد غير صالح")}}.setNegativeButton("إلغاء",null).show()}

    private fun showReports(){content.removeAllViews();content.addView(title("التقارير المالية"));content.addView(button("ميزان المراجعة"){set(db.trialBalance());content.addView(title("ميزان المراجعة"),0)});content.addView(button("دفتر الأستاذ / القيود"){showJournal()});val d=db.dashboard();val profit=d.getValue("sales")-d.getValue("cogs")-d.getValue("expenses");content.addView(card("صافي الربح التقديري",fmt.format(profit)));content.addView(card("إجمالي المبيعات",fmt.format(d.getValue("sales"))));content.addView(card("تكلفة المبيعات",fmt.format(d.getValue("cogs"))));content.addView(card("المصروفات",fmt.format(d.getValue("expenses"))))}

    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
}
