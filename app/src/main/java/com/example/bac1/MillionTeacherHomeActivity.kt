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

class MillionTeacherHomeActivity : AppCompatActivity() {

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
        container.addView(tv("💰 من سيربح التحدي", 22f, true))
        val code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""

        val add = Button(this)
        add.text = "➕ لعبة جديدة"
        add.setOnClickListener { startActivity(Intent(this, MillionCreateActivity::class.java)) }
        container.addView(add)

        container.addView(tv("ألعابي (اضغط لشوف المتصدرين، واضغط مطولًا للحذف)", 14f))

        FirebaseFirestore.getInstance().collection("millionGames")
            .whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { res ->
                val list = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                if (list.isEmpty()) container.addView(tv("لسه ما عملتش لعبة.", 14f))
                list.forEach { d ->
                    val n = (d.get("questions") as? List<*>)?.size ?: 0
                    val b = Button(this)
                    b.isAllCaps = false
                    b.text = "${d.getString("title") ?: ""} · $n سؤال"
                    b.setOnClickListener {
                        startActivity(Intent(this, MillionBoardActivity::class.java).putExtra("gameId", d.id))
                    }
                    b.setOnLongClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("حذف اللعبة")
                            .setMessage("تحذف \"${d.getString("title") ?: ""}\" ونتايجها؟")
                            .setPositiveButton("احذف") { _, _ -> deleteGame(d.id) }
                            .setNegativeButton("إلغاء", null)
                            .show()
                        true
                    }
                    container.addView(b)
                }
            }
            .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }

    private fun deleteGame(id: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("millionResults").whereEqualTo("gameId", id).get()
            .addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.delete() }
                db.collection("millionGames").document(id).delete()
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
