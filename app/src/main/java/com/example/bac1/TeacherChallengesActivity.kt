package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeacherChallengesActivity : AppCompatActivity() {

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
        container.addView(tv("🎯 تحديات المدرس", 24f, true))

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val isTeacher = prefs.getBoolean("is_teacher", false)
        val code = intent.getStringExtra("teacherCode")
            ?: prefs.getString("teacher_code", "") ?: ""
        if (code.isEmpty()) {
            container.addView(tv("لا يوجد مدرس مرتبط بحسابك.", 14f))
            return
        }
        container.addView(tv("جارٍ التحميل...", 13f))

        FirebaseFirestore.getInstance().collection("teacherChallenges")
            .whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { res ->
                container.removeAllViews()
                container.addView(tv("🎯 تحديات المدرس", 24f, true))
                val docs = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                if (docs.isEmpty()) container.addView(tv("لا توجد تحديات حاليًا.", 14f))
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val myKey = prefs.getString("student_id", "") ?: ""
                docs.forEach { d ->
                    val deadline = d.getLong("deadline") ?: 0L
                    val open = System.currentTimeMillis() < deadline
                    val b = Button(this)
                    b.isAllCaps = false
                    b.text = "${d.getString("title") ?: ""}\n🎁 ${d.getString("prize") ?: ""}\n" +
                            (if (open) "⏳ مفتوح حتى ${fmt.format(Date(deadline))}"
                            else "🔒 انتهى")
                    b.setOnClickListener {
                        val id = d.id
                        if (isTeacher || !open) {
                            startActivity(Intent(this, TeacherChallengeBoardActivity::class.java)
                                .putExtra("challengeId", id))
                        } else {
                            FirebaseFirestore.getInstance().collection("teacherChallengeResults")
                                .document("${id}_$myKey").get()
                                .addOnSuccessListener { r ->
                                    val target = if (r.exists()) TeacherChallengeBoardActivity::class.java
                                    else TeacherChallengePlayActivity::class.java
                                    startActivity(Intent(this, target).putExtra("challengeId", id))
                                }
                        }
                    }
                    container.addView(b)
                }
            }
            .addOnFailureListener {
                container.removeAllViews()
                container.addView(tv("تعذّر تحميل التحديات.", 14f))
            }
    }
}
