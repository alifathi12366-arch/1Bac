package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ReviewEditActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private var code = ""
    private var reviewId: String? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(10), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        code = prefs.getString("teacher_code", "") ?: ""
        val teacherName = prefs.getString("teacher_name", "") ?: ""
        reviewId = intent.getStringExtra("reviewId")

        container.addView(tv(if (reviewId == null) "📘 مراجعة جديدة" else "✏️ تعديل المراجعة", 22f, true))

        container.addView(tv("عنوان المراجعة"))
        val etTitle = EditText(this)
        etTitle.hint = "مثال: مراجعة الوحدة الأولى"
        etTitle.setTextColor(0xFF000000.toInt())
        container.addView(etTitle)

        container.addView(tv("النقاط (كل نقطة في سطر)"))
        val etBody = EditText(this)
        etBody.hint = "اكتب كل نقطة في سطر جديد"
        etBody.setTextColor(0xFF000000.toInt())
        etBody.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        etBody.minLines = 6
        container.addView(etBody)

        container.addView(tv("رقمك للتواصل (اختياري)"))
        val etPhone = EditText(this)
        etPhone.hint = "01012345678"
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        etPhone.setTextColor(0xFF000000.toInt())
        container.addView(etPhone)

        val cb = CheckBox(this)
        cb.text = "إظهار رقمي على المراجعة"
        cb.setTextColor(0xFF000000.toInt())
        container.addView(cb)

        val save = Button(this)
        save.text = "💾 حفظ ونشر"
        save.setTextColor(0xFFFFFFFF.toInt())
        save.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        container.addView(save)

        if (reviewId != null) {
            db.collection("teacherReviews").document(reviewId!!).get().addOnSuccessListener { d ->
                etTitle.setText(d.getString("title") ?: "")
                etBody.setText(d.getString("body") ?: "")
                etPhone.setText(d.getString("phone") ?: "")
                cb.isChecked = d.getBoolean("showPhone") == true
            }
            val del = Button(this)
            del.text = "🗑️ مسح المراجعة"
            del.setOnClickListener {
                AlertDialog.Builder(this).setTitle("مسح المراجعة؟")
                    .setPositiveButton("مسح") { _, _ ->
                        db.collection("teacherReviews").document(reviewId!!).delete()
                            .addOnSuccessListener { finish() }
                    }.setNegativeButton("إلغاء", null).show()
            }
            container.addView(del)
        }

        save.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val body = etBody.text.toString().trim()
            if (title.isEmpty() || body.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "اكتب العنوان والنقاط", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val data = hashMapOf<String, Any>(
                "teacherCode" to code, "teacherName" to teacherName,
                "title" to title, "body" to body,
                "phone" to etPhone.text.toString().filter { it.isDigit() || it == '+' },
                "showPhone" to cb.isChecked,
                "createdAt" to System.currentTimeMillis()
            )
            save.isEnabled = false
            val col = db.collection("teacherReviews")
            val task = if (reviewId == null) col.add(data) else col.document(reviewId!!).update(data)
            task.addOnSuccessListener {
                if (reviewId == null) TeacherActivityLog.post(this, "نزّل مراجعة جديدة 📘")
                Toast.makeText(this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener {
                save.isEnabled = true
                Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
