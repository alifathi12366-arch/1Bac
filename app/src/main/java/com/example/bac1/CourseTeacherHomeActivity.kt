package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CourseTeacherHomeActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout

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

    private fun code() = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        container.removeAllViews()
        container.addView(tv("🎬 الكورسات والفيديوهات", 22f, true))
        container.addView(
            tv(
                "ارفع الفيديو على يوتيوب بوضع \"غير مدرج\" وحط اللينك في الدرس. " +
                        "كل درس عليه امتحان، والطالب مش بيفتح الدرس اللي بعده إلا لما ينجح.", 13f
            )
        )

        val add = Button(this)
        add.text = "➕ كورس جديد"
        add.setOnClickListener { askTitle() }
        container.addView(add)

        container.addView(tv("كورساتي (اضغط للفتح، واضغط مطولًا للحذف)", 14f))

        db.collection("courses").whereEqualTo("teacherCode", code()).get()
            .addOnSuccessListener { res ->
                val list = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                if (list.isEmpty()) container.addView(tv("لسه ما عملتش كورس.", 14f))
                list.forEach { d ->
                    val n = (d.get("lessons") as? List<*>)?.size ?: 0
                    val b = Button(this)
                    b.isAllCaps = false
                    b.text = "${d.getString("title") ?: ""} · $n درس"
                    b.setOnClickListener {
                        startActivity(Intent(this, CourseManageActivity::class.java).putExtra("courseId", d.id))
                    }
                    b.setOnLongClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("حذف الكورس")
                            .setMessage("تحذف \"${d.getString("title") ?: ""}\" وكل تقدّم الطلاب فيه؟")
                            .setPositiveButton("احذف") { _, _ -> deleteCourse(d.id) }
                            .setNegativeButton("إلغاء", null)
                            .show()
                        true
                    }
                    container.addView(b)
                }
            }
            .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }

    private fun askTitle() {
        val et = EditText(this)
        et.hint = "مثال: كورس الوحدة الأولى"
        et.setPadding(dp(20), dp(12), dp(20), dp(12))
        AlertDialog.Builder(this)
            .setTitle("اسم الكورس")
            .setView(et)
            .setPositiveButton("إنشاء") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isEmpty()) {
                    Toast.makeText(this, "اكتب اسم الكورس", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
                val id = CourseUtil.newId()
                db.collection("courses").document(id).set(
                    hashMapOf<String, Any>(
                        "courseId" to id,
                        "teacherCode" to code(),
                        "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                        "title" to t,
                        "passPercent" to 60L,
                        "lessons" to emptyList<Any>(),
                        "createdAt" to System.currentTimeMillis()
                    )
                ).addOnSuccessListener {
                    startActivity(Intent(this, CourseManageActivity::class.java).putExtra("courseId", id))
                }.addOnFailureListener {
                    Toast.makeText(this, "فشل الإنشاء: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteCourse(id: String) {
        db.collection("courseProgress").whereEqualTo("courseId", id).get()
            .addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.delete() }
                db.collection("courses").document(id).delete()
                    .addOnSuccessListener { load() }
                    .addOnFailureListener {
                        Toast.makeText(this, "فشل الحذف", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل الحذف", Toast.LENGTH_SHORT).show()
            }
    }
}
