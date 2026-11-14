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
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class LeagueCreateActivity : AppCompatActivity() {

    private val fields = mutableListOf<List<EditText>>()
    private val groups = mutableListOf<RadioGroup>()
    private val pts = mutableListOf<EditText>()
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

    private fun buildQuestions(count: Int) {
        questionsBox.removeAllViews()
        fields.clear()
        groups.clear()
        pts.clear()
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
            questionsBox.addView(label("🏅 نقاط السؤال ده:", 14f))
            val p = edit("10")
            p.inputType = InputType.TYPE_CLASS_NUMBER
            p.setText("10")
            questionsBox.addView(p)
            fields.add(listOf(q) + o)
            groups.add(rg)
            pts.add(p)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)

        container.addView(label("🏆 دوري أبطال جديد", 22f))

        container.addView(label("عنوان الدوري"))
        val etTitle = edit("مثال: دوري الوحدة الأولى")
        container.addView(etTitle)

        container.addView(label("وقت كل سؤال بالثواني (من 5 إلى 120)"))
        val etSeconds = edit("20")
        etSeconds.inputType = InputType.TYPE_CLASS_NUMBER
        etSeconds.setText("20")
        container.addView(etSeconds)

        container.addView(label("عدد الأسئلة (من 1 إلى 20)"))
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
            if (c !in 1..20) Toast.makeText(this, "العدد من 1 إلى 20", Toast.LENGTH_SHORT).show()
            else buildQuestions(c)
        }
        countRow.addView(apply)
        container.addView(countRow)

        questionsBox = LinearLayout(this)
        questionsBox.orientation = LinearLayout.VERTICAL
        container.addView(questionsBox)
        buildQuestions(5)

        val save = Button(this)
        save.text = "🚀 حفظ وفتح الدوري"
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
                Toast.makeText(this, "اكتب عنوان الدوري", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (seconds !in 5..120) {
                Toast.makeText(this, "الوقت من 5 إلى 120 ثانية", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val questions = mutableListOf<HashMap<String, Any>>()
            for (i in 0 until fields.size) {
                val f = fields[i]
                val text = f[0].text.toString().trim()
                val opts = f.drop(1).map { it.text.toString().trim() }
                val g = groups[i]
                val points = pts[i].text.toString().trim().toIntOrNull() ?: 0
                if (text.isEmpty() || opts.any { it.isEmpty() } || g.checkedRadioButtonId == -1) {
                    Toast.makeText(this, "السؤال ${i + 1} غير مكتمل", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (points !in 1..1000) {
                    Toast.makeText(this, "نقاط السؤال ${i + 1} من 1 إلى 1000", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val correct = g.indexOfChild(g.findViewById(g.checkedRadioButtonId))
                questions.add(
                    hashMapOf<String, Any>(
                        "text" to text, "options" to opts, "correctIndex" to correct,
                        "points" to points, "seconds" to seconds
                    )
                )
            }

            val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            val id = UUID.randomUUID().toString()
            save.isEnabled = false
            FirebaseFirestore.getInstance().collection("leagues").document(id)
                .set(
                    hashMapOf<String, Any>(
                        "leagueId" to id,
                        "teacherCode" to (prefs.getString("teacher_code", "") ?: ""),
                        "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                        "title" to title,
                        "questions" to questions,
                        "status" to "waiting",
                        "currentIndex" to -1,
                        "standings" to emptyList<Any>(),
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    TeacherActivityLog.post(this, "فتح دوري أبطال جديد 🏆 ادخل الألعاب وانضم")
                    Toast.makeText(this, "تم فتح الدوري ✅", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LeagueControlActivity::class.java).putExtra("leagueId", id))
                    finish()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
