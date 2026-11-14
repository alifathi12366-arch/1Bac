package com.example.bac1

import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class MatchCreateActivity : AppCompatActivity() {

    private val aFields = mutableListOf<EditText>()
    private val bFields = mutableListOf<EditText>()
    private lateinit var pairsBox: LinearLayout
    private lateinit var etLabelA: EditText
    private lateinit var etLabelB: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    private fun edit(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setTextColor(0xFF000000.toInt())
        e.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(80))
        return e
    }

    private fun buildPairs(count: Int) {
        val oldA = aFields.map { it.text.toString() }
        val oldB = bFields.map { it.text.toString() }
        pairsBox.removeAllViews()
        aFields.clear()
        bFields.clear()
        for (n in 1..count) {
            pairsBox.addView(label("الزوج $n", 16f))
            val a = edit("العنصر الأول (مثال: الحدث)")
            val b = edit("العنصر المقابل (مثال: تاريخه)")
            a.setText(oldA.getOrNull(n - 1) ?: "")
            b.setText(oldB.getOrNull(n - 1) ?: "")
            pairsBox.addView(a)
            pairsBox.addView(b)
            aFields.add(a)
            bFields.add(b)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        container.addView(label("🔗 وصّل الكلمة بمعناها", 22f))
        container.addView(
            label(
                "أمثلة: (حدث ← تاريخه)، (كلمة ← معناها)، (دولة ← عاصمتها). " +
                        "كل عنصر له مقابل واحد بس، ومينفعش تكرر نفس العنصر في نفس العمود.", 13f
            )
        )

        container.addView(label("عنوان اللعبة"))
        val etTitle = edit("مثال: أحداث الحرب العالمية الأولى")
        container.addView(etTitle)

        container.addView(label("اسم العمود الأول"))
        etLabelA = edit("مثال: الحدث")
        container.addView(etLabelA)

        container.addView(label("اسم العمود الثاني"))
        etLabelB = edit("مثال: التاريخ")
        container.addView(etLabelB)

        container.addView(label("عدد الأزواج (من 3 إلى 12)"))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        val etCount = EditText(this)
        etCount.setTextColor(0xFF000000.toInt())
        etCount.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        etCount.setText("6")
        etCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        countRow.addView(etCount)
        val apply = Button(this)
        apply.text = "تطبيق"
        apply.setOnClickListener {
            val c = etCount.text.toString().trim().toIntOrNull() ?: 6
            if (c !in 3..12) Toast.makeText(this, "العدد من 3 إلى 12", Toast.LENGTH_SHORT).show()
            else buildPairs(c)
        }
        countRow.addView(apply)
        container.addView(countRow)

        pairsBox = LinearLayout(this)
        pairsBox.orientation = LinearLayout.VERTICAL
        container.addView(pairsBox)
        buildPairs(6)

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
            val labelA = etLabelA.text.toString().trim().ifEmpty { "العمود الأول" }
            val labelB = etLabelB.text.toString().trim().ifEmpty { "العمود الثاني" }
            if (title.isEmpty()) {
                Toast.makeText(this, "اكتب عنوان اللعبة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pairs = mutableListOf<HashMap<String, Any>>()
            val seenA = HashSet<String>()
            val seenB = HashSet<String>()
            for (i in aFields.indices) {
                val a = aFields[i].text.toString().trim()
                val b = bFields[i].text.toString().trim()
                if (a.isEmpty() || b.isEmpty()) {
                    Toast.makeText(this, "الزوج ${i + 1} غير مكتمل", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!seenA.add(a.lowercase())) {
                    Toast.makeText(this, "العنصر الأول في الزوج ${i + 1} مكرر", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!seenB.add(b.lowercase())) {
                    Toast.makeText(this, "العنصر المقابل في الزوج ${i + 1} مكرر", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pairs.add(hashMapOf<String, Any>("a" to a, "b" to b))
            }

            val id = UUID.randomUUID().toString()
            save.isEnabled = false
            FirebaseFirestore.getInstance().collection("matchGames").document(id)
                .set(
                    hashMapOf<String, Any>(
                        "gameId" to id,
                        "teacherCode" to (prefs.getString("teacher_code", "") ?: ""),
                        "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                        "title" to title,
                        "labelA" to labelA,
                        "labelB" to labelB,
                        "pairs" to pairs,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    TeacherActivityLog.post(this, "نزّل لعبة توصيل جديدة 🔗 ادخل الألعاب")
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
