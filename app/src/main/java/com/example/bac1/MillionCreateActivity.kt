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
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

class MillionCreateActivity : AppCompatActivity() {

    private val fields = mutableListOf<List<EditText>>()
    private val rgs = mutableListOf<RadioGroup>()
    private lateinit var questionsBox: LinearLayout

    private val full = listOf(
        100L, 200L, 300L, 500L, 1000L, 2000L, 4000L, 8000L,
        16000L, 32000L, 64000L, 125000L, 250000L, 500000L, 1000000L
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Long): String = NumberFormat.getInstance(Locale.US).format(v)

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
        rgs.clear()
        val prizes = full.takeLast(count)
        for (n in 1..count) {
            val safe = n % 5 == 0 && n < count
            questionsBox.addView(
                label("السؤال $n  ·  💰 ${fmt(prizes[n - 1])} نقطة" + if (safe) "  ·  🛡️ نقطة أمان" else "", 17f)
            )
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
            rgs.add(rg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        container.addView(label("💰 من سيربح التحدي", 22f))
        container.addView(label("رتّب الأسئلة من الأسهل للأصعب، وسلم الجوائز بيتحسب لوحده (آخر سؤال = 1,000,000 نقطة).", 13f))

        container.addView(label("عنوان اللعبة"))
        val etTitle = edit("مثال: تحدي الوحدة الأولى")
        container.addView(etTitle)

        container.addView(label("وقت كل سؤال بالثواني (من 10 إلى 120)"))
        val etSeconds = edit("30")
        etSeconds.inputType = InputType.TYPE_CLASS_NUMBER
        etSeconds.setText("30")
        container.addView(etSeconds)

        container.addView(label("عدد الأسئلة (من 3 إلى 15)"))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        val etCount = edit("10")
        etCount.inputType = InputType.TYPE_CLASS_NUMBER
        etCount.setText("10")
        etCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        countRow.addView(etCount)
        val apply = Button(this)
        apply.text = "تطبيق"
        apply.setOnClickListener {
            val c = etCount.text.toString().trim().toIntOrNull() ?: 10
            if (c !in 3..15) Toast.makeText(this, "العدد من 3 إلى 15", Toast.LENGTH_SHORT).show()
            else buildQuestions(c)
        }
        countRow.addView(apply)
        container.addView(countRow)

        questionsBox = LinearLayout(this)
        questionsBox.orientation = LinearLayout.VERTICAL
        container.addView(questionsBox)
        buildQuestions(10)

        val save = Button(this)
        save.text = "🚀 حفظ ونشر اللعبة"
        save.setTextColor(0xFFFFFFFF.toInt())
        save.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        save.layoutParams = lp
        container.addView(save)

        save.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val seconds = etSeconds.text.toString().trim().toIntOrNull() ?: 0
            if (title.isEmpty()) {
                Toast.makeText(this, "اكتب عنوان اللعبة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (seconds !in 10..120) {
                Toast.makeText(this, "الوقت من 10 إلى 120 ثانية", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val questions = mutableListOf<HashMap<String, Any>>()
            for (i in 0 until fields.size) {
                val f = fields[i]
                val text = f[0].text.toString().trim()
                val opts = f.drop(1).map { it.text.toString().trim() }
                val g = rgs[i]
                if (text.isEmpty() || opts.any { it.isEmpty() } || g.checkedRadioButtonId == -1) {
                    Toast.makeText(this, "السؤال ${i + 1} غير مكتمل", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val correct = g.indexOfChild(g.findViewById(g.checkedRadioButtonId))
                questions.add(
                    hashMapOf<String, Any>("text" to text, "options" to opts, "correctIndex" to correct)
                )
            }

            val n = questions.size
            val prizes = full.takeLast(n)
            val checkpoints = (1 until n).filter { it % 5 == 0 }.map { it - 1 }

            val id = UUID.randomUUID().toString()
            save.isEnabled = false
            FirebaseFirestore.getInstance().collection("millionGames").document(id)
                .set(
                    hashMapOf<String, Any>(
                        "gameId" to id,
                        "teacherCode" to (prefs.getString("teacher_code", "") ?: ""),
                        "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                        "title" to title,
                        "seconds" to seconds,
                        "questions" to questions,
                        "prizes" to prizes,
                        "checkpoints" to checkpoints,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    TeacherActivityLog.post(this, "فتح لعبة من سيربح التحدي 💰 ادخل الألعاب")
                    Toast.makeText(this, "تم نشر اللعبة ✅", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
