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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)

        // تاريخ الغد بتوقيت القاهرة كقيمة افتراضية
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"))
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Africa/Cairo")

        container.addView(label("➕ إضافة أسئلة تحدي اليوم", 20f))
        container.addView(label("التاريخ (yyyy-MM-dd)"))
        val etDate = edit("2026-10-17")
        etDate.setText(fmt.format(cal.time))
        container.addView(etDate)

        container.addView(label("المادة"))
        val etSubject = edit("الرياضيات")
        container.addView(etSubject)

        container.addView(label("وقت كل سؤال بالثواني"))
        val etTimer = edit("25")
        etTimer.inputType = InputType.TYPE_CLASS_NUMBER
        etTimer.setText("25")
        container.addView(etTimer)

        for (n in 1..5) {
            container.addView(label("السؤال $n", 18f))
            val q = edit("نص السؤال", true)
            val o = listOf(edit("الاختيار أ"), edit("الاختيار ب"), edit("الاختيار ج"), edit("الاختيار د"))
            container.addView(q)
            o.forEach { container.addView(it) }
            container.addView(label("الإجابة الصحيحة:", 14f))
            val rg = RadioGroup(this)
            rg.orientation = RadioGroup.HORIZONTAL
            listOf("أ", "ب", "ج", "د").forEach { name ->
                val rb = RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = name
                rb.setTextColor(0xFF000000.toInt())
                rg.addView(rb)
            }
            container.addView(rg)
            fields.add(listOf(q) + o)
            groups.add(rg)
        }

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
            for (i in 0 until 5) {
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
                    Toast.makeText(this, "تم الحفظ ✅ تحدي $date", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    btn.isEnabled = true
                    Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
