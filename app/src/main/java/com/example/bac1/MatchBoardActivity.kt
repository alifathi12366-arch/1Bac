package com.example.bac1

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class MatchBoardActivity : AppCompatActivity() {

    private class Row(val id: String, val name: String, val score: Long, val matched: Long, val timeMs: Long)

    private val db = FirebaseFirestore.getInstance()
    private lateinit var body: LinearLayout
    private var gameId = ""
    private var myKey = ""
    private var title = ""

    private fun dp(v: Int) = LeagueUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor(LeagueUi.BG))
        val scroll = ScrollView(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(12), dp(16), dp(24))
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val name = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = prefs.all["student_id"]?.toString().orEmpty()
        var key = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }
        if (key.isEmpty()) key = "anon"
        myKey = key + "_" + name.replace("/", "-")

        gameId = intent.getStringExtra("gameId") ?: ""
        load()
    }

    private fun header() {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🏆 لوحة المتصدرين", 24f, LeagueUi.GOLD, true))
        if (title.isNotEmpty()) {
            val t = LeagueUi.tv(this, title, 15f, LeagueUi.MUTED)
            t.setPadding(0, 0, 0, dp(8))
            body.addView(t)
        }
    }

    private fun load() {
        header()
        body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        if (gameId.isEmpty()) {
            header()
            body.addView(LeagueUi.note(this, "تعذّر فتح اللعبة"))
            return
        }
        db.collection("matchGames").document(gameId).get()
            .addOnSuccessListener { g ->
                title = g.getString("title") ?: ""
                db.collection("matchResults").whereEqualTo("gameId", gameId).get()
                    .addOnSuccessListener { snap -> show(snap.documents) }
                    .addOnFailureListener {
                        header()
                        body.addView(LeagueUi.note(this, "تعذّر التحميل، تحقق من الإنترنت"))
                    }
            }
            .addOnFailureListener {
                header()
                body.addView(LeagueUi.note(this, "تعذّر التحميل، تحقق من الإنترنت"))
            }
    }

    private fun show(docs: List<DocumentSnapshot>) {
        header()
        val rows = docs.map { d ->
            Row(
                d.getString("studentId") ?: d.id,
                d.getString("studentName") ?: "طالب",
                d.getLong("score") ?: 0L,
                d.getLong("matched") ?: 0L,
                d.getLong("timeMs") ?: 0L
            )
        }.sortedWith(
            compareByDescending<Row> { it.score }.thenBy { it.timeMs }.thenBy { it.name }
        )

        body.addView(LeagueUi.note(this, "👥 عدد اللي لعبوا: ${rows.size}", LeagueUi.CYAN))
        if (rows.isEmpty()) {
            body.addView(LeagueUi.note(this, "لسه محدش لعب اللعبة دي."))
        } else {
            val entries = rows.mapIndexed { i, r ->
                LeagueUi.Entry(r.id, r.name, r.score, r.matched, r.timeMs, i + 1, 0)
            }
            body.addView(LeagueUi.podium(this, entries))
            val h = LeagueUi.tv(this, "الترتيب الكامل", 18f, LeagueUi.GOLD, true)
            h.setPadding(0, dp(16), 0, dp(4))
            body.addView(h)
            body.addView(LeagueUi.standingsView(this, entries, myKey, 30))
        }

        val b = LeagueUi.button(this, "🔄 تحديث", "#6366F1")
        b.setOnClickListener { load() }
        body.addView(b)
    }
}
