package com.example.bac1

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class SortCreateActivity : AppCompatActivity() {

    private val nameFields = mutableListOf<EditText>()
    private val itemFields = mutableListOf<EditText>()
    private lateinit var catsBox: LinearLayout

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
        return e
    }

    private fun buildCats(count: Int) {
        val oldNames = nameFields.map { it.text.toString() }
        val oldItems = itemFields.map { it.text.toString() }
        catsBox.removeAllViews()
        nameFields.clear()
        itemFields.clear()
        for (n in 1..count) {
            catsBox.addView(label("التصنيف $n", 18f))
            val name = edit("اسم التصنيف (مثال: جملة اسمية)")
            name.setText(oldNames.getOrNull(n - 1) ?: "")
            val items = edit("اكتب كل عنصر في سطر منفصل")
            items.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            items.minLines = 4
            items.gravity = Gravity.TOP
            items.setText(oldItems.getOrNull(n - 1) ?: "")
            catsBox.addView(name)
            catsBox.addView(items)
            nameFields.add(name)
            itemFields.add(items)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        container.addView(label("🗂️ صنّف المعلومات", 22f))
        container.addView(
            label(
                "أمثلة: (أنواع الجمل)، (فروع العلوم)، (حروف الجر وحروف العطف). " +
                        "اكتب أسماء التصنيفات، وتحت كل تصنيف عناصره، كل عنصر في سطر. " +
                        "ومينفعش نفس العنصر يتكرر في تصنيفين.", 13f
            )
        )

        container.addView(label("عنوان اللعبة"))
        val etTitle = edit("مثال: أنواع الجمل")
        container.addView(etTitle)

        container.addView(label("عدد التصنيفات (من 2 إلى 5)"))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        val etCount = EditText(this)
        etCount.setTextColor(0xFF000000.toInt())
        etCount.inputType = InputType.TYPE_CLASS_NUMBER
        etCount.setText("3")
        etCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        countRow.addView(etCount)
        val apply = Button(this)
        apply.text = "تطبيق"
        apply.setOnClickListener {
            val c = etCount.text.toString().trim().toIntOrNull() ?: 3
            if (c !in 2..5) Toast.makeText(this, "العدد من 2 إلى 5", Toast.LENGTH_SHORT).show()
            else buildCats(c)
        }
        countRow.addView(apply)
        container.addView(countRow)

        catsBox = LinearLayout(this)
        catsBox.orientation = LinearLayout.VERTICAL
        container.addView(catsBox)
        buildCats(3)

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
            if (title.isEmpty()) {
                Toast.makeText(this, "اكتب عنوان اللعبة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cats = mutableListOf<HashMap<String, Any>>()
            val seenCat = HashSet<String>()
            val seenItem = HashSet<String>()
            var total = 0
            for (i in nameFields.indices) {
                val name = nameFields[i].text.toString().trim()
                val lines = itemFields[i].text.toString().split("\n")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                if (name.isEmpty()) {
                    Toast.makeText(this, "اسم التصنيف ${i + 1} فاضي", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!seenCat.add(name.lowercase())) {
                    Toast.makeText(this, "اسم التصنيف \"$name\" مكرر", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (lines.isEmpty()) {
                    Toast.makeText(this, "التصنيف ${i + 1} ملوش عناصر", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                for (l in lines) {
                    if (l.length > 60) {
                        Toast.makeText(this, "العنصر \"${l.take(15)}...\" طويل (حد 60 حرف)", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (!seenItem.add(l.lowercase())) {
                        Toast.makeText(this, "العنصر \"$l\" مكرر في أكتر من مكان", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                }
                total += lines.size
                cats.add(hashMapOf<String, Any>("name" to name, "items" to lines))
            }
            if (total < 4) {
                Toast.makeText(this, "ضيف 4 عناصر على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (total > 40) {
                Toast.makeText(this, "الحد الأقصى 40 عنصر", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = UUID.randomUUID().toString()
            save.isEnabled = false
            FirebaseFirestore.getInstance().collection("sortGames").document(id)
                .set(
                    hashMapOf<String, Any>(
                        "gameId" to id,
                        "teacherCode" to (prefs.getString("teacher_code", "") ?: ""),
                        "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                        "title" to title,
                        "categories" to cats,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    TeacherActivityLog.post(this, "نزّل لعبة تصنيف جديدة 🗂️ ادخل الألعاب")
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
