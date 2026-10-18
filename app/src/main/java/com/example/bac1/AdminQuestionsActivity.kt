package com.example.bac1

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AdminQuestionsActivity : AppCompatActivity() {

    private val fields = mutableListOf<List<EditText>>()
    private val groups = mutableListOf<RadioGroup>()
    private lateinit var etDate: EditText
    private lateinit var etSubject: EditText
    private lateinit var etTimer: EditText
    private lateinit var etCount: EditText
    private lateinit var questionsBox: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    private fun edit(hint: String, multiline: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setTextColor(0xFF000000.toInt())
        if (multiline) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        return e
    }

    private fun dateString(plusDays: Int): String {
        val tz = TimeZone.getTimeZone("Africa/Cairo")
        val cal = Calendar.getInstance(tz)
        cal.add(Calendar.DAY_OF_YEAR, plusDays)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = tz
        return fmt.format(cal.time)
    }

    // يبني خانات الأسئلة بالعدد المطلوب (1 إلى 20)
    private fun buildQuestions(count: Int) {
        questionsBox.removeAllViews()
        fields.clear()
        groups.clear()
        for (n in 1..count) {
            questionsBox.addView(label("السؤال $n", 18f))
            val q = edit("نص السؤال", true)
            val o = listOf(edit("الاختيار أ"), edit("الاختيار ب"), edit("الاختيار ج"), edit("الاختيار د"))
            questionsBox.addView(q)
            o.forEach { questionsBox.addView(it) }
            questionsBox.addView(label("الإجابة الصحيحة:", 14f))
            val rg = RadioGroup(this)
            rg.orientation = RadioGroup.HORIZONTAL
            listOf("أ", "ب", "ج", "د").forEach { name ->
                val rb = RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = name
                rb.setTextColor(0xFF000000.toInt())
                rg.addView(rb)
            }
            questionsBox.addView(rg)
            fields.add(listOf(q) + o)
            groups.add(rg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)

        container.addView(label("➕ إضافة / تعديل أسئلة تحدي اليوم", 20f))
        container.addView(label("التاريخ (yyyy-MM-dd)"))
        etDate = edit("2026-10-17")
        etDate.setText(dateString(0))
        container.addView(etDate)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        fun smallBtn(text: String, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            p.marginEnd = dp(4)
            b.layoutParams = p
            b.setOnClickListener { onClick() }
            return b
        }
        row.addView(smallBtn("اليوم") { etDate.setText(dateString(0)) })
        row.addView(smallBtn("بكرة") { etDate.setText(dateString(1)) })
        row.addView(smallBtn("📥 تحميل الأسئلة") { loadExisting() })
        container.addView(row)

        container.addView(label("المادة"))
        etSubject = edit("الرياضيات")
        container.addView(etSubject)

        container.addView(label("وقت كل سؤال بالثواني"))
        etTimer = edit("25")
        etTimer.inputType = InputType.TYPE_CLASS_NUMBER
        etTimer.setText("25")
        container.addView(etTimer)

        container.addView(label("عدد الأسئلة (من 1 إلى 20)"))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        etCount = edit("5")
        etCount.inputType = InputType.TYPE_CLASS_NUMBER
        etCount.setText("5")
        etCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        countRow.addView(etCount)
        val applyBtn = Button(this)
        applyBtn.text = "تطبيق"
        applyBtn.setOnClickListener {
            val c = etCount.text.toString().trim().toIntOrNull() ?: 5
            if (c !in 1..20) {
                Toast.makeText(this, "العدد من 1 إلى 20", Toast.LENGTH_SHORT).show()
            } else {
                buildQuestions(c)
            }
        }
        countRow.addView(applyBtn)
        container.addView(countRow)

        questionsBox = LinearLayout(this)
        questionsBox.orientation = LinearLayout.VERTICAL
        container.addView(questionsBox)
        buildQuestions(5)

        val btn = Button(this)
        btn.text = "حفظ الأسئلة"
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE65100.toInt())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        btn.layoutParams = lp
        container.addView(btn)

        btn.setOnClickListener {
            val date = etDate.text.toString().trim()
            val subject = etSubject.text.toString().trim()
            val timer = etTimer.text.toString().trim().toLongOrNull() ?: 25L
            if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) || subject.isEmpty()) {
                Toast.makeText(this, "راجع التاريخ والمادة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val questions = mutableListOf<HashMap<String, Any>>()
            for (i in 0 until fields.size) {
                val f = fields[i]
                val text = f[0].text.toString().trim()
                val opts = f.drop(1).map { it.text.toString().trim() }
                val g = groups[i]
                if (text.isEmpty() || opts.any { it.isEmpty() } || g.checkedRadioButtonId == -1) {
                    Toast.makeText(this, "السؤال ${i + 1} غير مكتمل", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val correct = g.indexOfChild(g.findViewById(g.checkedRadioButtonId))
                questions.add(hashMapOf("text" to text, "options" to opts, "correctIndex" to correct))
            }

            btn.isEnabled = false
            FirebaseFirestore.getInstance().collection("dailyChallenges").document(date)
                .set(hashMapOf("subject" to subject, "timerSeconds" to timer, "questions" to questions))
                .addOnSuccessListener {
                    Toast.makeText(this, "تم الحفظ ✅ تحدي $date (${questions.size} أسئلة)", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    btn.isEnabled = true
                    Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun loadExisting() {
        val date = etDate.text.toString().trim()
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) {
            Toast.makeText(this, "اكتب التاريخ بشكل صحيح", Toast.LENGTH_SHORT).show()
            return
        }
        FirebaseFirestore.getInstance().collection("dailyChallenges").document(date).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    Toast.makeText(this, "لا توجد أسئلة محفوظة بهذا التاريخ", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                etSubject.setText(d.getString("subject") ?: "")
                etTimer.setText((d.getLong("timerSeconds") ?: 25L).toString())
                val raw = d.get("questions") as? List<*> ?: emptyList<Any>()
                val count = raw.size.coerceIn(1, 20)
                etCount.setText(count.toString())
                buildQuestions(count)
                raw.forEachIndexed { i, item ->
                    if (i >= fields.size) return@forEachIndexed
                    val m = item as? Map<*, *> ?: return@forEachIndexed
                    fields[i][0].setText(m["text"] as? String ?: "")
                    val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    for (k in 0 until 4) fields[i][k + 1].setText(opts.getOrNull(k) ?: "")
                    val c = (m["correctIndex"] as? Number)?.toInt() ?: -1
                    groups[i].clearCheck()
                    if (c in 0 until groups[i].childCount) {
                        (groups[i].getChildAt(c) as RadioButton).isChecked = true
                    }
                }
                Toast.makeText(this, "تم التحميل ✏️ عدّل ثم اضغط حفظ", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل التحميل: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
