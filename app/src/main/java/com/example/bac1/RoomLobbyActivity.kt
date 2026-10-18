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
import com.google.firebase.firestore.FirebaseFirestore

class RoomLobbyActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var container: LinearLayout
    private var code = ""

    private val refresher = object : Runnable {
        override fun run() {
            load()
            handler.postDelayed(this, 8000)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        code = intent.getStringExtra("code") ?: ""
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
        FirebaseFirestore.getInstance().collection("rooms").document(code).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    Toast.makeText(this, "الغرفة غير موجودة", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                render(d)
            }
    }

    private fun render(d: com.google.firebase.firestore.DocumentSnapshot) {
        container.removeAllViews()
        val owner = d.getString("ownerName") ?: ""
        val expires = d.getLong("expiresAt") ?: 0L
        val hoursLeft = ((expires - System.currentTimeMillis()) / 3600000L).coerceAtLeast(0)
        val max = (d.getLong("maxPlayers") ?: 10L).toInt()
        val players = (d.get("players") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

        container.addView(tv("⚔️ غرفة التحدي", 22f, true))
        container.addView(tv("الكود: $code", 28f, true))
        container.addView(tv("أنشأها: $owner · تنتهي بعد $hoursLeft ساعة", 13f))

        val share = Button(this)
        share.text = "📤 شارك الكود مع أصحابك"
        share.setTextColor(0xFFFFFFFF.toInt())
        share.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF25D366.toInt())
        share.setOnClickListener {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, "تحداك $owner في تطبيق أولى بكالوريا ⚔️\nادخل بكود الغرفة: $code\nمن: اتحدي أصحابك")
            startActivity(Intent.createChooser(i, "مشاركة الكود"))
        }
        container.addView(share)

        container.addView(tv("اللاعبون (${players.size} من $max)", 18f, true))
        players.forEachIndexed { idx, p ->
            val n = p["name"] as? String ?: ""
            val id = p["id"] as? String ?: ""
            container.addView(tv("${idx + 1}. $n #$id", 16f))
        }

        container.addView(tv("🚧 بدء الحل والنتائج في التحديث القادم. ارجع للغرفة بعدها.", 14f))
    }
}
