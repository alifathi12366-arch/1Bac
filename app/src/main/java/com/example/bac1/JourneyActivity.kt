package com.example.bac1

import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore

object Journey {
    val stages = listOf(
        Pair("📘", "مجتهد"), Pair("🧭", "مستكشف"), Pair("⚡", "متحمّس"), Pair("🎯", "قناص الإجابات"),
        Pair("🔥", "محترف"), Pair("🧠", "عبقري"), Pair("🏹", "بطل"), Pair("👑", "أسطورة"),
        Pair("💎", "ماسي"), Pair("🏆", "بطل أولى ثانوي")
    )

    fun reached(total: Long): Int = minOf(stages.size.toLong(), total / 100).toInt()

    fun summary(total: Long): String {
        val r = reached(total)
        val title = if (r == 0) "🌱 في بداية الرحلة" else "${stages[r - 1].first} ${stages[r - 1].second}"
        return "لقبك: $title\n✅ $total إجابة صحيحة"
    }
}

class JourneyActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var body: LinearLayout
    private var key = ""
    private var name = ""
    private var claimed = false

    private fun dp(v: Int) = LeagueUi.dp(this, v)

    private fun oval(fill: String, stroke: String?): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Color.parseColor(fill))
        if (stroke != null) g.setStroke(dp(3), Color.parseColor(stroke))
        return g
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = CourseUtil.page(this)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        name = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = prefs.all["student_id"]?.toString().orEmpty()
        key = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }
        load()
    }

    private fun header() {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🚀 رحلة التفوق", 26f, LeagueUi.GOLD, true))
    }

    private fun load() {
        header()
        body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        if (key.isEmpty()) {
            header()
            body.addView(LeagueUi.note(this, "تعذّر تحديد حسابك"))
            return
        }
        db.collection("dailyAttempts").whereEqualTo("studentKey", key)
            .aggregate(AggregateField.sum("score")).get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                val total = (snap.get(AggregateField.sum("score")) as? Number)?.toLong() ?: 0L
                db.collection("journeyClaims").document(key).get()
                    .addOnSuccessListener { c ->
                        claimed = c.exists()
                        render(total)
                    }
                    .addOnFailureListener { render(total) }
            }
            .addOnFailureListener {
                header()
                body.addView(LeagueUi.note(this, "تعذّر الاتصال، تحقق من الإنترنت وحاول تاني."))
            }
    }

    private fun render(total: Long) {
        header()
        val r = Journey.reached(total)

        // اللقب الحالي
        val cur = LinearLayout(this)
        cur.orientation = LinearLayout.VERTICAL
        cur.gravity = Gravity.CENTER_HORIZONTAL
        cur.setPadding(dp(16), dp(18), dp(16), dp(18))
        cur.background = LeagueUi.bg(this, "#2B3A8F", 22, LeagueUi.GOLD)
        val clp = LinearLayout.LayoutParams(-1, -2)
        clp.topMargin = dp(10)
        cur.layoutParams = clp
        val emoji = if (r == 0) "🌱" else Journey.stages[r - 1].first
        val title = if (r == 0) "في بداية الرحلة" else Journey.stages[r - 1].second
        cur.addView(LeagueUi.tv(this, emoji, 52f))
        cur.addView(LeagueUi.tv(this, title, 24f, LeagueUi.GOLD, true))
        cur.addView(LeagueUi.tv(this, "✅ $total إجابة صحيحة في تحدي اليوم", 15f, LeagueUi.TEXT))
        if (r < Journey.stages.size) {
            val nextAt = (r + 1) * 100L
            val inStage = (total - r * 100L).toInt().coerceIn(0, 100)
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.background = LeagueUi.bg(this, "#34428F", 5)
            val fill = View(this)
            fill.background = LeagueUi.bg(this, LeagueUi.GOLD, 5)
            bar.addView(fill, LinearLayout.LayoutParams(0, -1, inStage.toFloat()))
            bar.addView(View(this), LinearLayout.LayoutParams(0, -1, (100 - inStage).toFloat()))
            val blp = LinearLayout.LayoutParams(-1, dp(10))
            blp.topMargin = dp(10)
            cur.addView(bar, blp)
            val next = Journey.stages[r]
            cur.addView(
                LeagueUi.tv(this, "باقي ${nextAt - total} للقب ${next.first} ${next.second}", 13f, LeagueUi.MUTED)
            )
        } else {
            cur.addView(LeagueUi.tv(this, "🎉 وصلت لآخر مرحلة!", 15f, LeagueUi.GREEN, true))
        }
        body.addView(cur)

        // طريق المراحل
        Journey.stages.forEachIndexed { idx, st ->
            val i = idx + 1
            val got = r >= i
            val isNext = r + 1 == i

            if (idx > 0) {
                val line = View(this)
                line.setBackgroundColor(Color.parseColor(if (got) LeagueUi.GOLD else "#34428F"))
                val llp = LinearLayout.LayoutParams(dp(3), dp(16))
                llp.marginStart = dp(26)
                body.addView(line, llp)
            }

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val rlp = LinearLayout.LayoutParams(-1, -2)
            rlp.topMargin = if (idx == 0) dp(14) else 0
            row.layoutParams = rlp

            val badge = TextView(this)
            badge.text = if (got) st.first else "🔒"
            badge.textSize = 24f
            badge.gravity = Gravity.CENTER
            badge.background = when {
                got -> oval(LeagueUi.GOLD, null)
                isNext -> oval(LeagueUi.CARD, LeagueUi.CYAN)
                else -> oval("#1E2A5E", null)
            }
            row.addView(badge, LinearLayout.LayoutParams(dp(56), dp(56)))

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val colLp = LinearLayout.LayoutParams(0, -2, 1f)
            colLp.marginStart = dp(12)
            col.addView(
                LeagueUi.tv(
                    this, if (got || isNext) st.second else "؟؟؟", 17f,
                    if (got) LeagueUi.GOLD else LeagueUi.TEXT, true
                )
            )
            col.addView(
                LeagueUi.tv(this, "${i * 100} إجابة صحيحة" + if (got) "  ✅" else "", 13f, LeagueUi.MUTED)
            )
            row.addView(col, colLp)
            body.addView(row)
        }

        // الجائزة
        val prizeText = when {
            total >= 1000 && claimed -> "تم تسجيل طلبك ✅ هنتواصل معاك لتسليم الجائزة."
            total >= 1000 -> "مبروك! وصلت 1000 إجابة صحيحة 🎉 اضغط لتسجيل طلب الجائزة."
            else -> "هتكسب جائزة من تطبيقنا لما توصل 1000 إجابة صحيحة (باقي ${1000 - total})."
        }
        val box = LeagueUi.info(this, "🎁 جائزة الرحلة", prizeText)
        val plp = LinearLayout.LayoutParams(-1, -2)
        plp.topMargin = dp(16)
        box.layoutParams = plp
        body.addView(box)

        if (total >= 1000 && !claimed) {
            val b = LeagueUi.button(this, "🎁 سجّل طلب الجائزة", LeagueUi.GREEN)
            b.setOnClickListener {
                b.isEnabled = false
                val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
                db.collection("journeyClaims").document(key).set(
                    hashMapOf<String, Any>(
                        "studentKey" to key, "studentName" to name,
                        "phone" to (prefs.getString("student_phone", "") ?: ""),
                        "total" to total, "createdAt" to System.currentTimeMillis()
                    )
                ).addOnSuccessListener {
                    claimed = true
                    Toast.makeText(this, "تم تسجيل طلبك 🎁", Toast.LENGTH_LONG).show()
                    render(total)
                }.addOnFailureListener {
                    b.isEnabled = true
                    Toast.makeText(this, "تعذّر التسجيل، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
                }
            }
            body.addView(b)
        }
    }
}
