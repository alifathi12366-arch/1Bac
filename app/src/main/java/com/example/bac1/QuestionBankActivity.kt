package com.example.bac1

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class QuestionBankActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private lateinit var listBox: LinearLayout
    private var code = ""
    private var pickMode = false
    private var tagFilter = ""
    private var docs: List<DocumentSnapshot> = emptyList()
    private val checked = mutableSetOf<String>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""
        pickMode = intent.getBooleanExtra("pick", false)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        container.removeAllViews()
        container.addView(tv(if (pickMode) "🏦 اختر أسئلة للامتحان" else "🏦 بنك أسئلتي", 22f, true))

        if (!pickMode) {
            val add = Button(this)
            add.text = "➕ سؤال جديد"
            add.setOnClickListener { editDialog(null) }
            container.addView(add)
        }

        container.addView(tv("فلتر بالوسم (اتركه فاضي لعرض الكل)", 13f))
        val etTag = EditText(this)
        etTag.hint = "مثال: وحدة 1"
        etTag.setText(tagFilter)
        etTag.setTextColor(0xFF000000.toInt())
        container.addView(etTag)
        val f = Button(this)
        f.text = "🔍 تطبيق الفلتر"
        f.setOnClickListener {
            tagFilter = etTag.text.toString().trim()
            renderList()
        }
        container.addView(f)

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        container.addView(listBox)

        if (pickMode) {
            val done = Button(this)
            done.text = "✅ إضافة المحدد للامتحان"
            done.setTextColor(0xFFFFFFFF.toInt())
            done.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
            done.setOnClickListener { finishPick() }
            container.addView(done)
        }

        db.collection("teacherBank").whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { res ->
                docs = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                renderList()
            }
            .addOnFailureListener { listBox.addView(tv("تعذّر التحميل", 14f)) }
    }

    private fun renderList() {
        listBox.removeAllViews()
        val shown = docs.filter { tagFilter.isEmpty() || (it.getString("tag") ?: "") == tagFilter }
        if (shown.isEmpty()) listBox.addView(tv("لا توجد أسئلة.", 14f))
        container.findViewWithTag<TextView>("count")?.text = ""
        shown.forEach { d ->
            val text = d.getString("text") ?: ""
            val tag = d.getString("tag") ?: ""
            val label = (if (tag.isNotEmpty()) "[$tag] " else "") + text
            if (pickMode) {
                val cb = CheckBox(this)
                cb.text = label
                cb.textSize = 15f
                cb.setTextColor(0xFF000000.toInt())
                cb.isChecked = checked.contains(d.id)
                cb.setOnCheckedChangeListener { _, on -> if (on) checked.add(d.id) else checked.remove(d.id) }
                listBox.addView(cb)
            } else {
                val b = Button(this)
                b.isAllCaps = false
                b.text = label
                b.setOnClickListener { editDialog(d) }
                listBox.addView(b)
            }
        }
    }

    private fun finishPick() {
        val chosen = docs.filter { checked.contains(it.id) }
        if (chosen.isEmpty()) {
            Toast.makeText(this, "اختر سؤالًا على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        val arr = ArrayList<String>()
        chosen.forEach { d ->
            val opts = (d.get("options") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            // text||op1||op2||op3||op4||correct(1-4)
            arr.add(listOf(d.getString("text") ?: "") .plus(opts.take(4)).plus(
                ((d.getLong("correctIndex") ?: 0L) + 1).toString()).joinToString("\u0001"))
        }
        setResult(RESULT_OK, android.content.Intent().putStringArrayListExtra("picked", arr))
        finish()
    }

    private fun editDialog(d: DocumentSnapshot?) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), 0)
        fun ed(hint: String, v: String): EditText {
            val e = EditText(this)
            e.hint = hint
            e.setText(v)
            e.setTextColor(0xFF000000.toInt())
            box.addView(e)
            return e
        }
        val oldOpts = (d?.get("options") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val q = ed("نص السؤال", d?.getString("text") ?: "")
        val o = (0..3).map { ed("الاختيار ${it + 1}", oldOpts.getOrNull(it) ?: "") }
        val tag = ed("الوسم (اختياري)", d?.getString("tag") ?: "")
        box.addView(tv("الإجابة الصحيحة:", 14f))
        val rg = RadioGroup(this)
        rg.orientation = RadioGroup.HORIZONTAL
        val cur = (d?.getLong("correctIndex") ?: -1L).toInt()
        (1..4).forEach { n ->
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = "$n"
            rb.setTextColor(0xFF000000.toInt())
            rb.isChecked = (n - 1 == cur)
            rg.addView(rb)
        }
        box.addView(rg)

        val b = AlertDialog.Builder(this)
            .setTitle(if (d == null) "سؤال جديد" else "تعديل السؤال")
            .setView(android.widget.ScrollView(this).also { it.addView(box) })
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
        if (d != null) b.setNeutralButton("مسح") { _, _ ->
            db.collection("teacherBank").document(d.id).delete().addOnSuccessListener { build() }
        }
        val dlg = b.create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = q.text.toString().trim()
            val opts = o.map { it.text.toString().trim() }
            val idx = rg.indexOfChild(rg.findViewById(rg.checkedRadioButtonId))
            if (text.isEmpty() || opts.any { it.isEmpty() } || rg.checkedRadioButtonId == -1) {
                Toast.makeText(this, "أكمل السؤال والخيارات والإجابة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val data = hashMapOf<String, Any>(
                "teacherCode" to code, "text" to text, "options" to opts,
                "correctIndex" to idx, "tag" to tag.text.toString().trim(),
                "createdAt" to (d?.getLong("createdAt") ?: System.currentTimeMillis())
            )
            val col = db.collection("teacherBank")
            val task = if (d == null) col.add(data) else col.document(d.id).set(data)
            task.addOnSuccessListener { dlg.dismiss(); build() }
                .addOnFailureListener { Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show() }
        }
    }
}
