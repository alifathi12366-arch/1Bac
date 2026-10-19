package com.example.bac1

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class DailyBoardActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private var date = ""
    private var myKey = ""
    private var myName = ""
    private var confettiShown = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        date = intent.getStringExtra("date") ?: ""
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        myName = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = prefs.all["student_id"]?.toString().orEmpty()
        myKey = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }
        load()
    }

    private fun load() {
        FirebaseFirestore.getInstance().collection("dailyAttempts")
            .whereEqualTo("date", date).get()
            .addOnSuccessListener { res ->
                RoomChat.latest("DAILY_$date") { msgs -> render(res.documents, msgs) }
            }
            .addOnFailureListener {
                Toast.makeText(this, "تعذّر تحميل الترتيب", Toast.LENGTH_SHORT).show()
            }
    }

    private fun render(docs: List<DocumentSnapshot>, msgs: List<DocumentSnapshot>) {
        container.removeAllViews()
        val done = docs.filter { it.getBoolean("finished") == true }
            .sortedWith(compareBy<DocumentSnapshot>(
                { -(it.getLong("score") ?: 0L) },
                { it.getLong("timeMs") ?: Long.MAX_VALUE }
            ))

        container.addView(tv("🏆 ترتيب تحدي اليوم", 22f, true))
        container.addView(tv("$date · شارك ${done.size} طالب", 13f))

        val myIdx = done.indexOfFirst { it.getString("studentKey") == myKey }
        if (myIdx >= 0) {
            container.addView(tv("ترتيبك اليوم: #${myIdx + 1}", 18f, true))
        }
        if (myIdx == 0 && !confettiShown) {
            confettiShown = true
            findViewById<ViewGroup>(android.R.id.content).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1))
            container.addView(tv("👑 أنت بطل اليوم! مبروك 🎉", 20f, true))
        }

        if (done.isEmpty()) container.addView(tv("لا أحد أنهى التحدي بعد.", 14f))
        done.take(10).forEachIndexed { i, d ->
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
            val name = d.getString("studentName") ?: ""
            val s = d.getLong("score") ?: 0L
            val t = d.getLong("total") ?: 0L
            val sec = (d.getLong("timeMs") ?: 0L) / 1000
            val me = if (d.getString("studentKey") == myKey) " (أنت)" else ""
            container.addView(tv("$medal $name$me · $s من $t · $sec ث", 15f))
        }

        val refresh = Button(this)
        refresh.text = "🔄 تحديث"
        refresh.setOnClickListener { load() }
        container.addView(refresh)

        container.addView(tv("💬 الرسائل", 18f, true))
        RoomChat.PRESETS.chunked(2).forEach { pair ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            pair.forEach { text ->
                val b = Button(this)
                b.text = text
                b.textSize = 13f
                b.isAllCaps = false
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.marginEnd = dp(4)
                b.layoutParams = p
                b.setOnClickListener {
                    RoomChat.send("DAILY_$date", myKey, myName, text) { ok, msg ->
                        if (ok) load()
                        else Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                row.addView(b)
            }
            container.addView(row)
        }
        if (msgs.isEmpty()) {
            container.addView(tv("لا توجد رسائل بعد. ابدأ المشاكسة! 😄", 13f))
        } else {
            msgs.forEach { m ->
                val me = if (m.getString("senderId") == myKey) " (أنت)" else ""
                container.addView(tv("${m.getString("senderName") ?: ""}$me: ${m.getString("text") ?: ""}", 15f))
            }
        }
    }
}
