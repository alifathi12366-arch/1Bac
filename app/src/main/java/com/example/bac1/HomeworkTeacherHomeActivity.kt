package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class HomeworkTeacherHomeActivity : AppCompatActivity() {

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
        container.addView(tv("📚 الواجبات", 22f, true))
        val code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""

        val add = Button(this)
        add.text = "➕ واجب جديد"
        add.setOnClickListener { startActivity(Intent(this, HomeworkCreateActivity::class.java)) }
        container.addView(add)
        container.addView(tv("واجباتي (اضغط للمتابعة، واضغط مطولًا للحذف)", 14f))

        db.collection("homeworks").whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { res ->
                val list = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                if (list.isEmpty()) container.addView(tv("لسه ما عملتش واجب.", 14f))
                val now = System.currentTimeMillis()
                list.forEach { d ->
                    val n = (d.get("questions") as? List<*>)?.size ?: 0
                    val dl = d.getLong("deadline") ?: 0L
                    val b = Button(this)
                    b.isAllCaps = false
                    b.text = "${d.getString("title") ?: ""} · $n سؤال\n" +
                            (if (dl < now) "🔒 انتهى " else "⏰ ") + HomeworkUtil.fmtDate(dl)
                    b.setOnClickListener {
                        startActivity(Intent(this, HomeworkManageActivity::class.java).putExtra("hwId", d.id))
                    }
                    b.setOnLongClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("حذف الواجب")
                            .setMessage("تحذف \"${d.getString("title") ?: ""}\" وكل تسليمات الطلاب؟")
                            .setPositiveButton("احذف") { _, _ -> delete(d.id) }
                            .setNegativeButton("إلغاء", null)
                            .show()
                        true
                    }
                    container.addView(b)
                }
            }
            .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }

    private fun delete(id: String) {
        db.collection("homeworkSubmissions").whereEqualTo("homeworkId", id).get()
            .addOnSuccessListener { s ->
                s.documents.forEach { it.reference.delete() }
                db.collection("notices").whereEqualTo("homeworkId", id).get()
                    .addOnSuccessListener { n ->
                        n.documents.forEach { it.reference.delete() }
                        db.collection("homeworks").document(id).delete().addOnSuccessListener { load() }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "فشل الحذف", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { Toast.makeText(this, "فشل الحذف", Toast.LENGTH_SHORT).show() }
    }
}
