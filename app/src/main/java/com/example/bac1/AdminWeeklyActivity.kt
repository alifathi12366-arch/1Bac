package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class AdminWeeklyActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var etSubject: EditText
    private lateinit var etWeek: EditText
    private lateinit var etTitle: EditText
    private lateinit var etImage: EditText
    private lateinit var etPdf: EditText
    private lateinit var tvMode: TextView
    private lateinit var btnSave: Button
    private var editingId: String? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(10), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun edit(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setTextColor(0xFF000000.toInt())
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)

        container.addView(label("📢 إضافة / تعديل التقييمات الأسبوعية", 20f, true))
        tvMode = label("وضع: إضافة تقييم جديد", 14f)
        container.addView(tvMode)

        container.addView(label("المادة (مثال: اللغة العربية)"))
        etSubject = edit("اسم المادة")
        container.addView(etSubject)

        container.addView(label("رقم الأسبوع"))
        etWeek = edit("1")
        etWeek.inputType = InputType.TYPE_CLASS_NUMBER
        container.addView(etWeek)

        container.addView(label("العنوان"))
        etTitle = edit("عنوان التقييم")
        container.addView(etTitle)

        container.addView(label("رابط الصورة (Direct link من imgBB)"))
        etImage = edit("https://i.ibb.co/...")
        etImage.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        container.addView(etImage)

        container.addView(label("رابط الـ PDF (اختياري)"))
        etPdf = edit("https://...")
        etPdf.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        container.addView(etPdf)

        btnSave = Button(this)
        btnSave.text = "💾 حفظ"
        btnSave.setTextColor(0xFFFFFFFF.toInt())
        btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE65100.toInt())
        btnSave.setOnClickListener { save() }
        container.addView(btnSave)

        val clear = Button(this)
        clear.text = "➕ تقييم جديد (مسح الخانات)"
        clear.setOnClickListener { resetForm() }
        container.addView(clear)

        container.addView(label("التقييمات الموجودة", 18f, true))
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        container.addView(listBox)

        loadList()
    }

    private fun resetForm() {
        editingId = null
        etSubject.setText("")
        etWeek.setText("")
        etTitle.setText("")
        etImage.setText("")
        etPdf.setText("")
        tvMode.text = "وضع: إضافة تقييم جديد"
        btnSave.text = "💾 حفظ"
    }

    private fun save() {
        val subject = etSubject.text.toString().trim()
        val week = etWeek.text.toString().trim().toLongOrNull()
        val title = etTitle.text.toString().trim()
        val image = etImage.text.toString().trim()
        val pdf = etPdf.text.toString().trim()

        if (subject.isEmpty() || week == null || title.isEmpty()) {
            Toast.makeText(this, "املأ المادة ورقم الأسبوع والعنوان", Toast.LENGTH_SHORT).show()
            return
        }
        if (image.isNotEmpty()) {
            if (!image.startsWith("http")) {
                Toast.makeText(this, "رابط الصورة غير صحيح", Toast.LENGTH_SHORT).show()
                return
            }
            if (image.contains("ibb.co/") && !image.contains("i.ibb.co/")) {
                Toast.makeText(this, "ده Viewer link. اختر Direct link (يبدأ بـ i.ibb.co)", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (pdf.isNotEmpty() && !pdf.startsWith("http")) {
            Toast.makeText(this, "رابط الـ PDF غير صحيح", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf<String, Any>(
            "source" to subject,
            "weekNumber" to week,
            "title" to title,
            "imageUrl" to image,
            "pdfUrl" to pdf
        )
        btnSave.isEnabled = false
        val col = db.collection("weeklyUpdates")
        val task = editingId?.let { col.document(it).set(data) } ?: col.add(data)
        task.addOnSuccessListener {
            btnSave.isEnabled = true
            Toast.makeText(this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show()
            resetForm()
            loadList()
        }.addOnFailureListener {
            btnSave.isEnabled = true
            Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun weekOf(d: DocumentSnapshot): Long =
        d.getLong("weekNumber") ?: d.getString("weekNumber")?.toLongOrNull() ?: 0L

    private fun loadList() {
        listBox.removeAllViews()
        listBox.addView(label("جارٍ التحميل...", 13f))
        db.collection("weeklyUpdates").get()
            .addOnSuccessListener { res ->
                listBox.removeAllViews()
                if (res.isEmpty) {
                    listBox.addView(label("لا توجد تقييمات بعد.", 13f))
                    return@addOnSuccessListener
                }
                val docs = res.documents.sortedWith(
                    compareBy<DocumentSnapshot>({ it.getString("source") ?: "" }, { weekOf(it) })
                )
                docs.forEach { d -> addRow(d) }
            }
            .addOnFailureListener {
                listBox.removeAllViews()
                listBox.addView(label("تعذّر تحميل القائمة", 13f))
            }
    }

    private fun addRow(d: DocumentSnapshot) {
        val subject = d.getString("source") ?: ""
        val title = d.getString("title") ?: ""
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))
        row.addView(label("$subject · أسبوع ${weekOf(d)}\n$title", 15f))

        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL

        val edit = Button(this)
        edit.text = "✏️ تعديل"
        edit.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        edit.setOnClickListener {
            editingId = d.id
            etSubject.setText(subject)
            etWeek.setText(weekOf(d).toString())
            etTitle.setText(title)
            etImage.setText(d.getString("imageUrl") ?: "")
            etPdf.setText(d.getString("pdfUrl") ?: "")
            tvMode.text = "وضع: تعديل «$title»"
            btnSave.text = "💾 حفظ التعديل"
            Toast.makeText(this, "اتحمّل في الخانات فوق", Toast.LENGTH_SHORT).show()
        }

        val del = Button(this)
        del.text = "🗑️ مسح"
        del.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        del.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("مسح التقييم؟")
                .setMessage("$subject · أسبوع ${weekOf(d)}\n$title")
                .setPositiveButton("مسح") { _, _ ->
                    db.collection("weeklyUpdates").document(d.id).delete()
                        .addOnSuccessListener {
                            if (editingId == d.id) resetForm()
                            loadList()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "فشل المسح", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        btns.addView(edit)
        btns.addView(del)
        row.addView(btns)
        listBox.addView(row)
    }
}
