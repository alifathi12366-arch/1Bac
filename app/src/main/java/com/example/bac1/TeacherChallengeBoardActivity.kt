package com.example.bac1

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class TeacherChallengeBoardActivity : AppCompatActivity() {

    private lateinit var box: LinearLayout
    private var myId = ""
    private var isTeacher = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false, color: Int = 0xFF222222.toInt()): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.setPadding(dp(8), dp(6), dp(8), dp(6))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        myId = prefs.getString("student_id", "") ?: ""
        isTeacher = prefs.getBoolean("is_teacher", false)

        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(24), dp(16), dp(24))
        scroll.addView(box)
        setContentView(scroll)

        val id = intent.getStringExtra("challengeId") ?: ""
        val db = FirebaseFirestore.getInstance()
        box.addView(tv("جارٍ التحميل...", 16f))
        db.collection("teacherChallenges").document(id).get().addOnSuccessListener { c ->
            db.collection("teacherChallengeResults").whereEqualTo("challengeId", id).get()
                .addOnSuccessListener { res -> build(c, res.documents) }
                .addOnFailureListener { box.removeAllViews(); box.addView(tv("تعذّر التحميل", 16f)) }
        }.addOnFailureListener { box.removeAllViews(); box.addView(tv("تعذّر التحميل", 16f)) }
    }

    private fun build(c: DocumentSnapshot, all: List<DocumentSnapshot>) {
        box.removeAllViews()
        val title = c.getString("title") ?: ""
        val prize = c.getString("prize") ?: ""
        val ended = System.currentTimeMillis() >= (c.getLong("deadline") ?: 0L)
        val done = all.filter { it.getBoolean("finished") == true }.sortedWith(
            compareBy<DocumentSnapshot>({ -(it.getLong("score") ?: 0L) }, { it.getLong("timeMs") ?: Long.MAX_VALUE }))
        val winner = if (ended && done.isNotEmpty() && (done[0].getLong("score") ?: 0L) > 0L) done[0] else null
        val iWon = winner != null && winner.getString("studentId") == myId && !isTeacher

        box.addView(tv("🎯 $title", 22f, true))
        box.addView(tv("🎁 الجائزة: $prize", 17f, true, 0xFFE65100.toInt()))
        box.addView(tv("عدد المشاركين: ${done.size}", 14f))

        if (iWon) {
            box.addView(tv("👑", 70f).also { it.gravity = Gravity.CENTER })
            box.addView(tv("مبروك يا بطل! أنت الفائز 🏆", 24f, true, 0xFF2E7D32.toInt()).also { it.gravity = Gravity.CENTER })
            box.addView(tv("جائزتك: $prize\nتواصل مع مدرسك لاستلامها.", 17f).also { it.gravity = Gravity.CENTER })
            (findViewById<ViewGroup>(android.R.id.content)).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1))
        } else if (winner != null) {
            box.addView(tv("👑 الفائز: ${winner.getString("studentName") ?: ""}", 20f, true, 0xFF6A1B9A.toInt()))
        } else if (!ended) {
            box.addView(tv("⏳ التحدي ما زال مفتوحًا. الفائز يُعلن بعد انتهاء الموعد.", 14f))
        }

        val mine = done.firstOrNull { it.getString("studentId") == myId }
        if (!isTeacher && mine != null) {
            box.addView(tv("نتيجتك: ${mine.getLong("score") ?: 0} من ${mine.getLong("total") ?: 0} · " +
                    "${(mine.getLong("timeMs") ?: 0L) / 1000} ث", 16f, true))
        }

        if (isTeacher || ended) {
            box.addView(tv("الترتيب", 20f, true))
            if (done.isEmpty()) box.addView(tv("لا أحد شارك بعد.", 14f))
            done.forEachIndexed { i, d ->
                val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
                val me = if (d.getString("studentId") == myId && !isTeacher) " (أنت)" else ""
                box.addView(tv("$medal ${d.getString("studentName") ?: ""}$me · " +
                        "${d.getLong("score") ?: 0} من ${d.getLong("total") ?: 0} · " +
                        "${(d.getLong("timeMs") ?: 0L) / 1000} ث", 15f, false, Color.parseColor("#222222")))
            }
        }

        val back = Button(this)
        back.text = "رجوع"
        back.setOnClickListener { finish() }
        box.addView(back)
    }
}
