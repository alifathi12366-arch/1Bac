package com.example.bac1

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ReviewViewActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false, g: Int = Gravity.START): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.gravity = g
        t.setPadding(dp(8), dp(6), dp(8), dp(6))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFF263238.toInt())
        setContentView(scroll)

        val id = intent.getStringExtra("reviewId") ?: ""
        FirebaseFirestore.getInstance().collection("teacherReviews").document(id).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) { finish(); return@addOnSuccessListener }
                build(scroll, d.getString("title") ?: "", d.getString("body") ?: "",
                    d.getString("teacherName") ?: "",
                    if (d.getBoolean("showPhone") == true) d.getString("phone") ?: "" else "")
            }
    }

    private fun build(scroll: ScrollView, title: String, body: String, teacher: String, phone: String) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFFFFDE7.toInt(), 0xFFFFF3E0.toInt())
        )
        bg.setStroke(dp(5), 0xFF00796B.toInt())
        bg.cornerRadius = dp(16).toFloat()
        card.background = bg
        card.setPadding(dp(16), dp(20), dp(16), dp(12))

        card.addView(tv("📘", 44f, Color.BLACK, false, Gravity.CENTER))
        card.addView(tv(title, 26f, 0xFF00695C.toInt(), true, Gravity.CENTER))

        val line = View(this)
        line.setBackgroundColor(0xFF00796B.toInt())
        card.addView(line, LinearLayout.LayoutParams(-1, dp(2)).also { it.setMargins(dp(20), dp(6), dp(20), dp(10)) })

        body.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, s ->
            card.addView(tv("${i + 1}. $s", 17f, 0xFF212121.toInt()))
        }

        card.addView(tv("الأستاذ / $teacher", 18f, 0xFF004D40.toInt(), true, Gravity.CENTER)
            .also { it.setPadding(dp(8), dp(18), dp(8), dp(2)) })
        if (phone.isNotEmpty()) card.addView(tv("📞 $phone", 15f, 0xFF424242.toInt(), false, Gravity.CENTER))
        card.addView(tv("1Bac", 10f, 0xFF888888.toInt(), false, Gravity.END))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(30), dp(16), dp(24))
        box.addView(card, LinearLayout.LayoutParams(-1, -2))

        val save = Button(this)
        save.text = "💾 حفظ كصورة"
        save.setOnClickListener { saveImage(card) }
        box.addView(save, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(16) })
        val back = Button(this)
        back.text = "رجوع"
        back.setOnClickListener { finish() }
        box.addView(back, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(box)
    }

        private fun saveImage(v: View, share: Boolean = false) {
        try {
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "1Bac_review_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/1Bac")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("no uri")
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (share) {
                val i = android.content.Intent(android.content.Intent.ACTION_SEND)
                i.type = "image/png"
                i.putExtra(android.content.Intent.EXTRA_STREAM, uri)
                i.putExtra(android.content.Intent.EXTRA_TEXT, "مراجعة من تطبيق أولى بكالوريا 📘")
                i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(android.content.Intent.createChooser(i, "مشاركة المراجعة"))
            } else {
                Toast.makeText(this, "تم الحفظ في معرض الصور ✅", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر الحفظ", Toast.LENGTH_SHORT).show()
        }
    }
