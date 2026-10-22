package com.example.bac1

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddTeacherChallengeActivity : AppCompatActivity() {

    private val fields = mutableListOf<List<EditText>>()
    private val groups = mutableListOf<RadioGroup>()
    private lateinit var questionsBox: LinearLayout
    private var deadlineMs = 0L

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

        container.addView(label("🎯 إنشاء تحدي للطلاب", 22f))

        container.addView(label("عنوان التحدي"))
        val etTitle = edit("مثال: تحدي الوحدة الأولى")
        container.addView(etTitle)

        container.addView(label("الجائزة (نص، والمدرس هو اللي يسلّمها)"))
                val etPrize = edit("مثال: شهادة تقدير + كتاب هدية 🎁")
        container.addView(etPrize)

        container.addView(label("مدة الحل بالدقائق"))
        val etDuration = edit("30")
        etDuration.inputType = InputType.TYPE_CLASS_NUMBER
        etDuration.setText("30")
        container.addView(etDuration)

        val tvDeadline = label("📅 آخر موعد للحل: اضغط للتحديد", 16f)
        tvDeadline.setBackgroundColor(0xFFE0E0E0.toInt())
        tvDeadline.setPadding(dp(12), dp(12), dp(12), dp(12))
        tvDeadline.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                TimePickerDialog(this, { _, h, min ->
                    val c = Calendar.getInstance()
                    c.set(y, m, d, h, min, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    deadlineMs = c.timeInMillis
                    tvDeadline.text = "📅 آخر موعد: " +
                            SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(Date(deadlineMs))
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        container.addView(tvDeadline)

        container.addView(label("عدد الأسئلة (من 1 إلى 30)"))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        val etCount = edit("5")
        etCount.inputType = InputType.TYPE_CLASS_NUMBER
        etCount.setText("5")
        etCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        countRow.addView(etCount)
        val apply = Button(this)
        apply.text = "تطبيق"
        apply.setOnClickListener {
            val c = etCount.text.toString().trim().toIntOrNull() ?: 5
            if (c !in 1..30) Toast.makeText(this, "العدد من 1 إلى 30", Toast.LENGTH_SHORT).show()
            else buildQuestions(c)
        }
        countRow.addView(apply)
        container.addView(countRow)

        questionsBox = LinearLayout(this)
        questionsBox.orientation = LinearLayout.VERTICAL
        container.addView(questionsBox)
        buildQuestions(5)

        val save = Button(this)
        save.text = "🚀 حفظ ونشر التحدي"
        save.setTextColor(0xFFFFFFFF.toInt())
        save.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        save.layoutParams = lp
        container.addView(save)

        save.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val prize = etPrize.text.toString().trim()
            val duration = etDuration.text.toString().trim().toLongOrNull() ?: 0L
            if (title.isEmpty() || prize.isEmpty() || duration <= 0L) {
                Toast.makeText(this, "املأ العنوان والجائزة والمدة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (deadlineMs <= System.currentTimeMillis()) {
                Toast.makeText(this, "حدد آخر موعد في المستقبل", Toast.LENGTH_SHORT).show()
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

            val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            val id = UUID.randomUUID().toString()
            save.isEnabled = false
            FirebaseFirestore.getInstance().collection("teacherChallenges").document(id)
                .set(hashMapOf(
                    "challengeId" to id,
                    "teacherCode" to (prefs.getString("teacher_code", "") ?: ""),
                    "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                    "title" to title,
                    "prize" to prize,
                    "durationMinutes" to duration,
                    "deadline" to deadlineMs,
                    "questions" to questions,
                    "createdAt" to System.currentTimeMillis()
                ))
                .addOnSuccessListener {
                                        TeacherActivityLog.post(this, "أنشأ تحديًا جديدًا 🎯")
                    Toast.makeText(this, "تم نشر التحدي ✅", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
