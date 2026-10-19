package com.example.bac1

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.sin
import kotlin.random.Random

// ورق ملوّن متحرك بالكود بدون مكتبات
class ConfettiView(ctx: Context) : View(ctx) {
    private class P(
        var x: Float, var y: Float, val vx: Float, val vy: Float,
        val size: Float, val color: Int, var rot: Float, val vr: Float
    )

    private val ps = mutableListOf<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(
        0xFFFFC107.toInt(), 0xFFE91E63.toInt(), 0xFF03A9F4.toInt(),
        0xFF4CAF50.toInt(), 0xFFFF5722.toInt(), 0xFF9C27B0.toInt()
    )
    private var startTime = 0L

    private fun spawn(w: Int, h: Int, anywhere: Boolean): P {
        val d = resources.displayMetrics.density
        return P(
            Random.nextFloat() * w,
            if (anywhere) -Random.nextFloat() * h else -20f * d,
            (Random.nextFloat() - 0.5f) * 2f * d,
            (3f + Random.nextFloat() * 4f) * d,
            (6f + Random.nextFloat() * 6f) * d,
            colors.random(),
            Random.nextFloat() * 360f,
            (Random.nextFloat() - 0.5f) * 12f
        )
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        ps.clear()
        repeat(110) { ps.add(spawn(w, h, true)) }
        startTime = System.currentTimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val running = System.currentTimeMillis() - startTime < 6000
        var alive = false
        for (p in ps) {
            p.y += p.vy
            p.x += p.vx + sin(p.y / 40f) * 1.5f
            p.rot += p.vr
            if (p.y > height + 40) {
                if (running) {
                    val n = spawn(width, height, false)
                    p.x = n.x; p.y = n.y
                } else continue
            }
            alive = true
            paint.color = p.color
            canvas.save()
            canvas.rotate(p.rot, p.x, p.y)
            canvas.drawRect(p.x, p.y, p.x + p.size, p.y + p.size * 0.6f, paint)
            canvas.restore()
        }
        if (alive) postInvalidateOnAnimation()
    }
}

class RoomResultActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(6), dp(8), dp(6))
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.getStringExtra("code") ?: ""
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val myId = prefs.getString("student_id", "") ?: ""
        prefs.edit().putBoolean("seen_${code}_$myId", true).apply()

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF1A237E.toInt())
        val loading = tv("جارٍ تحميل النتيجة...", 18f, Color.WHITE)
        root.addView(loading, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        setContentView(root)

        val db = FirebaseFirestore.getInstance()
        db.collection("rooms").document(code).get().addOnSuccessListener { room ->
            db.collection("roomResults").whereEqualTo("code", code).get()
                .addOnSuccessListener { res ->
                    root.removeAllViews()
                    build(root, room, res.documents, myId)
                }
                .addOnFailureListener { loading.text = "تعذّر تحميل النتيجة" }
        }.addOnFailureListener { loading.text = "تعذّر تحميل النتيجة" }
    }

    private fun build(
        root: FrameLayout,
        room: com.google.firebase.firestore.DocumentSnapshot,
        results: List<com.google.firebase.firestore.DocumentSnapshot>,
        myId: String
    ) {
        val winners = (room.get("winnerIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val tie = room.getBoolean("tie") == true
        val pts = (room.getLong("pointsAwarded") ?: 0L).toInt()
        val players = (room.get("players") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val nameOf = players.associate { (it["id"] as? String ?: "") to (it["name"] as? String ?: "") }
        val iWon = winners.contains(myId)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(40), dp(20), dp(30))

        when {
            iWon && !tie -> {
                box.addView(tv("👑", 80f, Color.WHITE))
                box.addView(tv("مبروك يا بطل!", 30f, 0xFFFFD54F.toInt(), true))
                box.addView(tv("أنت الفائز في هذه الغرفة 🏆", 18f, Color.WHITE))
                box.addView(tv("+$pts نقاط تحدي ⭐", 20f, 0xFF69F0AE.toInt(), true))
            }
            iWon && tie -> {
                box.addView(tv("🤝", 80f, Color.WHITE))
                box.addView(tv("تعادل رائع!", 30f, 0xFFFFD54F.toInt(), true))
                box.addView(tv("+$pts نقطة تحدي ⭐", 20f, 0xFF69F0AE.toInt(), true))
            }
            winners.isNotEmpty() -> {
                box.addView(tv("💪", 80f, Color.WHITE))
                box.addView(tv("حظ أوفر المرة القادمة", 26f, 0xFFFFD54F.toInt(), true))
                val names = winners.joinToString(" و ") { nameOf[it] ?: it }
                box.addView(tv(
                    if (tie) "تعادل على الصدارة: $names" else "الفائز: $names 👑",
                    18f, Color.WHITE))
            }
            else -> {
                box.addView(tv("🏁", 80f, Color.WHITE))
                box.addView(tv("انتهى التحدي", 26f, 0xFFFFD54F.toInt(), true))
                box.addView(tv("لا فائز في هذه الغرفة (لم يكتمل التنافس).", 16f, Color.WHITE))
            }
        }

        box.addView(tv("الترتيب النهائي", 20f, Color.WHITE, true))
        val byId = results.associateBy { it.getString("studentId") ?: "" }
        val sorted = players.map { (it["id"] as? String ?: "") }.sortedWith(
            compareBy<String>(
                { if (byId[it]?.getBoolean("finished") == true) 0 else 1 },
                { -(byId[it]?.getLong("score") ?: 0L) },
                { byId[it]?.getLong("timeMs") ?: Long.MAX_VALUE }
            ))
        sorted.forEachIndexed { i, id ->
            val r = byId[id]
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
            val line = if (r != null && r.getBoolean("finished") == true)
                "${r.getLong("score") ?: 0} من ${r.getLong("total") ?: 0} · ${(r.getLong("timeMs") ?: 0L) / 1000} ث"
            else "لم يكمل"
            val me = if (id == myId) " (أنت)" else ""
            box.addView(tv("$medal ${nameOf[id] ?: id}$me\n$line", 16f,
                if (id == myId) 0xFFFFD54F.toInt() else Color.WHITE))
        }

        val again = Button(this)
        again.text = "⚔️ تحدّى مرة ثانية"
        again.setTextColor(Color.WHITE)
        again.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE65100.toInt())
        again.setOnClickListener {
            startActivity(Intent(this, ChallengeHomeActivity::class.java))
            finish()
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(20)
        box.addView(again, lp)

        val back = Button(this)
        back.text = "رجوع"
        back.setOnClickListener { finish() }
        box.addView(back, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        scroll.addView(box)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        if (iWon) root.addView(ConfettiView(this), FrameLayout.LayoutParams(-1, -1))
    }
}
