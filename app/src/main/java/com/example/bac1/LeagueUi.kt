package com.example.bac1

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.firestore.DocumentSnapshot

object LeagueUi {
    const val BG = "#0B1033"
    const val CARD = "#1A2150"
    const val GOLD = "#FFC83D"
    const val CYAN = "#22D3EE"
    const val GREEN = "#22C55E"
    const val RED = "#EF4444"
    const val TEXT = "#FFFFFF"
    const val MUTED = "#A9B0D6"

    class LQ(val text: String, val options: List<String>, val correct: Int, val points: Int, val seconds: Int)

    class Entry(
        val id: String, val name: String, val score: Long, val correct: Long,
        val timeMs: Long, val rank: Int, val delta: Int
    )

    private class Row(val id: String, val name: String, val score: Long, val correct: Long, val timeMs: Long)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun bg(ctx: Context, color: String, radiusDp: Int, stroke: String? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(color))
        g.cornerRadius = dp(ctx, radiusDp).toFloat()
        if (stroke != null) g.setStroke(dp(ctx, 2), Color.parseColor(stroke))
        return g
    }

    fun tv(ctx: Context, s: String, size: Float = 16f, color: String = TEXT, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    fun note(ctx: Context, s: String, color: String = MUTED): TextView {
        val t = tv(ctx, s, 15f, color)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(ctx, 8), dp(ctx, 14), dp(ctx, 8), dp(ctx, 6))
        return t
    }

    fun info(ctx: Context, head: String, s: String): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 18))
        box.background = bg(ctx, CARD, 18)
        box.addView(tv(ctx, head, 21f, GOLD, true))
        val b = tv(ctx, s, 16f, TEXT)
        b.setPadding(0, dp(ctx, 8), 0, 0)
        box.addView(b)
        return box
    }

    fun button(ctx: Context, label: String, color: String): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.textSize = 16f
        b.setTextColor(Color.WHITE)
        b.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(ctx, 12)
        b.layoutParams = lp
        return b
    }

    fun questionBox(ctx: Context, s: String): TextView {
        val t = tv(ctx, s, 21f, TEXT, true)
        t.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
        t.background = bg(ctx, CARD, 18)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(ctx, 8)
        lp.bottomMargin = dp(ctx, 6)
        t.layoutParams = lp
        return t
    }

    // state: 0 عادي، 1 مختار، 2 صح، 3 غلط
    fun optionCard(ctx: Context, label: String, state: Int): TextView {
        val (fill, stroke, prefix) = when (state) {
            1 -> Triple("#0E5F76", CYAN, "")
            2 -> Triple("#14532D", GREEN, "✅  ")
            3 -> Triple("#7F1D1D", RED, "❌  ")
            else -> Triple("#1E2A5E", "#34428F", "")
        }
        val t = TextView(ctx)
        t.text = prefix + label
        t.textSize = 18f
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        t.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        t.background = bg(ctx, fill, 16, stroke)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(ctx, 8)
        t.layoutParams = lp
        t.isClickable = true
        return t
    }

    fun parseQuestions(raw: Any?): List<LQ> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val text = m["text"] as? String ?: return@mapNotNull null
            val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String } ?: return@mapNotNull null
            val c = (m["correctIndex"] as? Number)?.toInt() ?: return@mapNotNull null
            if (opts.size < 2 || c !in opts.indices) return@mapNotNull null
            val pts = (m["points"] as? Number)?.toInt() ?: 10
            val sec = (m["seconds"] as? Number)?.toInt() ?: 20
            LQ(text, opts, c, pts, sec)
        }
    }

    fun parse(raw: Any?): List<Entry> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            Entry(
                m["id"] as? String ?: "",
                m["name"] as? String ?: "",
                (m["score"] as? Number)?.toLong() ?: 0L,
                (m["correct"] as? Number)?.toLong() ?: 0L,
                (m["timeMs"] as? Number)?.toLong() ?: 0L,
                (m["rank"] as? Number)?.toInt() ?: 0,
                (m["delta"] as? Number)?.toInt() ?: 0
            )
        }
    }

    // يحسب الترتيب من مستندات النقاط (الأعلى نقاطًا، وعند التعادل الأسرع)
    fun build(docs: List<DocumentSnapshot>, prev: List<Entry>): List<HashMap<String, Any>> {
        val prevRank = HashMap<String, Int>()
        prev.forEach { prevRank[it.id] = it.rank }
        val rows = docs.map { d ->
            Row(
                d.getString("studentId") ?: d.id,
                d.getString("studentName") ?: "طالب",
                d.getLong("score") ?: 0L,
                d.getLong("correct") ?: 0L,
                d.getLong("timeMs") ?: 0L
            )
        }.sortedWith(compareByDescending<Row> { it.score }.thenBy { it.timeMs }.thenBy { it.name })

        val out = ArrayList<HashMap<String, Any>>()
        rows.take(100).forEachIndexed { i, r ->
            val rank = i + 1
            val old = prevRank[r.id]
            out.add(
                hashMapOf<String, Any>(
                    "id" to r.id, "name" to r.name, "score" to r.score,
                    "correct" to r.correct, "timeMs" to r.timeMs,
                    "rank" to rank, "delta" to (if (old != null) old - rank else 0)
                )
            )
        }
        return out
    }

    fun standingsView(ctx: Context, list: List<Entry>, myId: String, maxRows: Int = 10): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        list.take(maxRows).forEach { box.addView(row(ctx, it, it.id == myId)) }
        val me = list.firstOrNull { it.id == myId }
        if (me != null && me.rank > maxRows) {
            val dots = tv(ctx, "⋯", 18f, MUTED)
            dots.gravity = Gravity.CENTER
            box.addView(dots)
            box.addView(row(ctx, me, true))
        }
        return box
    }

    private fun row(ctx: Context, e: Entry, mine: Boolean): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        r.background = bg(ctx, if (mine) "#2B3A8F" else CARD, 14, if (mine) GOLD else null)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(ctx, 6)
        r.layoutParams = lp

        val medal = when (e.rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "${e.rank}"
        }
        val rankTv = tv(ctx, medal, if (e.rank <= 3) 22f else 16f, GOLD, true)
        rankTv.gravity = Gravity.CENTER
        r.addView(rankTv, LinearLayout.LayoutParams(dp(ctx, 40), -2))

        val nameTv = tv(ctx, e.name, 16f, TEXT, mine)
        nameTv.maxLines = 1
        r.addView(nameTv, LinearLayout.LayoutParams(0, -2, 1f))

        if (e.delta != 0) {
            val up = e.delta > 0
            val a = tv(ctx, (if (up) "▲" else "▼") + Math.abs(e.delta), 13f, if (up) GREEN else RED, true)
            a.setPadding(dp(ctx, 6), 0, dp(ctx, 10), 0)
            r.addView(a)
        }
        r.addView(tv(ctx, "${e.score}", 18f, GOLD, true))
        return r
    }

    fun podium(ctx: Context, list: List<Entry>): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        val specs = listOf(Triple(1, "🥈", 90), Triple(0, "🥇", 120), Triple(2, "🥉", 70))
        for ((idx, medal, h) in specs) {
            val e = list.getOrNull(idx) ?: continue
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginStart = dp(ctx, 4)
            lp.marginEnd = dp(ctx, 4)
            col.layoutParams = lp

            col.addView(tv(ctx, medal, 34f))
            val n = tv(ctx, e.name, 14f, TEXT, true)
            n.gravity = Gravity.CENTER
            n.maxLines = 2
            col.addView(n)
            col.addView(tv(ctx, "${e.score} نقطة", 13f, GOLD, true))

            val block = TextView(ctx)
            block.text = "${idx + 1}"
            block.gravity = Gravity.CENTER
            block.textSize = 26f
            block.setTypeface(null, Typeface.BOLD)
            block.setTextColor(Color.parseColor(BG))
            block.background = bg(
                ctx, if (idx == 0) GOLD else if (idx == 1) "#CBD5E1" else "#D6A06B", 12
            )
            val blp = LinearLayout.LayoutParams(-1, dp(ctx, h))
            blp.topMargin = dp(ctx, 6)
            col.addView(block, blp)
            row.addView(col)
        }
        return row
    }
}
