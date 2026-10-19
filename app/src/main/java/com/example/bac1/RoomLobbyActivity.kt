package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class RoomLobbyActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var container: LinearLayout
    private var code = ""
    private var studentId = ""
    private var studentName = ""
    private var settling = false

    private val refresher = object : Runnable {
        override fun run() {
            load()
            handler.postDelayed(this, 15000)
        }
    }

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

    private fun btn(text: String, color: Long, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setTextColor(0xFFFFFFFF.toInt())
        b.backgroundTintList = android.content.res.ColorStateList.valueOf(color.toInt())
        b.setOnClickListener { onClick() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        code = intent.getStringExtra("code") ?: ""
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        studentId = prefs.getString("student_id", "") ?: ""
        studentName = prefs.getString("student_name", "طالب") ?: "طالب"
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun load() {
        val db = FirebaseFirestore.getInstance()
        db.collection("rooms").document(code).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    Toast.makeText(this, "الغرفة غير موجودة", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                db.collection("roomResults").whereEqualTo("code", code).get()
                    .addOnSuccessListener { res ->
                        RoomChat.latest(code) { msgs -> render(d, res.documents, msgs) }
                    }
                    .addOnFailureListener { render(d, emptyList(), emptyList()) }
            }
    }

    private fun render(d: DocumentSnapshot, results: List<DocumentSnapshot>, msgs: List<DocumentSnapshot>) {
        container.removeAllViews()
        val owner = d.getString("ownerName") ?: ""
        val expires = d.getLong("expiresAt") ?: 0L
        val expired = System.currentTimeMillis() > expires
        val settled = d.getBoolean("settled") == true
        val hoursLeft = ((expires - System.currentTimeMillis()) / 3600000L).coerceAtLeast(0)
        val max = (d.getLong("maxPlayers") ?: 10L).toInt()
        val qCount = (d.get("questions") as? List<*>)?.size ?: 0
        val players = (d.get("players") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val byId = results.associateBy { it.getString("studentId") ?: "" }
        val mine = byId[studentId]
        val rows = players.map { p -> (p["id"] as? String ?: "") to (p["name"] as? String ?: "") }

        val finishedCount = rows.count { byId[it.first]?.getBoolean("finished") == true }
        val allDone = rows.isNotEmpty() && finishedCount == rows.size
        val ready = !settled && qCount > 0 && ((allDone && rows.size >= 2) || expired)
        if (ready && !settling) {
            settling = true
            ChallengeSettler.settle(code) { ok ->
                settling = false
                if (ok) load()
            }
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val seenKey = "seen_${code}_$studentId"
        if (settled && mine?.getBoolean("finished") == true && !prefs.getBoolean(seenKey, false)) {
            val i = Intent(this, RoomResultActivity::class.java)
            i.putExtra("code", code)
            startActivity(i)
        }

        container.addView(tv("⚔️ غرفة التحدي", 22f, true))
        container.addView(tv("الكود: $code", 28f, true))
        container.addView(tv(
            "أنشأها: $owner · $qCount سؤال · " + when {
                settled -> "انتهى التحدي"
                expired -> "انتهت صلاحيتها"
                else -> "تنتهي بعد $hoursLeft ساعة"
            }, 13f))

        if (!settled && !expired) {
            container.addView(btn("📤 شارك الكود مع أصحابك", 0xFF25D366) {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT,
                    "تحداك $owner في تطبيق أولى بكالوريا ⚔️\nادخل بكود الغرفة: $code\nمن: اتحدي أصحابك")
                startActivity(Intent.createChooser(i, "مشاركة الكود"))
            })
        }

        when {
            qCount == 0 -> container.addView(
                tv("هذه غرفة قديمة بدون أسئلة. أنشئ غرفة جديدة.", 14f))
            mine != null -> {
                val s = (mine.getLong("score") ?: 0L)
                val t = (mine.getLong("total") ?: qCount.toLong())
                container.addView(tv(
                    if (mine.getBoolean("finished") == true) "✅ حللتَ التحدي: $s من $t"
                    else "⌛ بدأتَ الحل ولم تُكمل (انتهت محاولتك)", 16f, true))
            }
            settled || expired -> container.addView(tv("انتهى هذا التحدي، لا يمكن الحل الآن.", 14f))
            else -> container.addView(btn("▶️ ابدأ الحل", 0xFFE65100) {
                val i = Intent(this, InterstitialActivity::class.java)
                i.putExtra("TARGET_CLASS", RoomPlayActivity::class.java.name)
                i.putExtra("code", code)
                startActivity(i)
            })
        }

        if (settled) {
            container.addView(btn("🏆 عرض النتيجة النهائية", 0xFF6A1B9A) {
                val i = Intent(this, RoomResultActivity::class.java)
                i.putExtra("code", code)
                startActivity(i)
            })
        }

        container.addView(btn("🔄 تحديث", 0xFF546E7A) { load() })

        container.addView(tv("اللاعبون (${players.size} من $max)", 18f, true))
        val sorted = rows.sortedWith(compareBy<Pair<String, String>>(
            { if (byId[it.first]?.getBoolean("finished") == true) 0 else 1 },
            { -(byId[it.first]?.getLong("score") ?: 0L) },
            { byId[it.first]?.getLong("timeMs") ?: Long.MAX_VALUE }
        ))
        sorted.forEachIndexed { idx, (id, name) ->
            val r = byId[id]
            val line = when {
                r == null -> "⏳ لم يحل بعد"
                r.getBoolean("finished") != true -> "⌛ لم يُكمل"
                else -> "✅ ${r.getLong("score") ?: 0} من ${r.getLong("total") ?: qCount}" +
                        " · ${(r.getLong("timeMs") ?: 0L) / 1000} ث"
            }
            container.addView(tv("${idx + 1}. $name #$id\n     $line", 15f))
        }

        // ---------- الرسائل الجاهزة ----------
        container.addView(tv("💬 الرسائل", 18f, true))
        if (!settled && !expired) {
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
                        RoomChat.send(code, studentId, studentName, text) { ok, msg ->
                            if (ok) load()
                            else Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(b)
                }
                container.addView(row)
            }
        }
        if (msgs.isEmpty()) {
            container.addView(tv("لا توجد رسائل بعد. ابدأ المشاكسة! 😄", 13f))
        } else {
            msgs.forEach { m ->
                val who = m.getString("senderName") ?: ""
                val text = m.getString("text") ?: ""
                val me = if (m.getString("senderId") == studentId) " (أنت)" else ""
                container.addView(tv("$who$me: $text", 15f))
            }
        }

        container.addView(tv(
            "تُحسم النتيجة عندما يخلص الجميع أو بعد انتهاء الـ 24 ساعة. الصفحة تتحدّث كل 15 ثانية.", 12f))
    }
}
