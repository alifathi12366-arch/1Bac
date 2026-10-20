package com.example.bac1

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HonorCertificateActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(10), dp(8), dp(10), dp(8))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val student = intent.getStringExtra("student") ?: ""
        val teacher = intent.getStringExtra("teacher") ?: ""
        val message = intent.getStringExtra("message") ?: ""

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF1A237E.toInt())

        // الشهادة نفسها (هي اللي تتحفظ كصورة)
        val cert = LinearLayout(this)
        cert.orientation = LinearLayout.VERTICAL
        cert.gravity = Gravity.CENTER_HORIZONTAL
        val bg = GradientDrawable()
        bg.setColor(0xFFFFF8E1.toInt())
        bg.setStroke(dp(6), 0xFFB8860B.toInt())
        bg.cornerRadius = dp(12).toFloat()
        cert.background = bg
        cert.setPadding(dp(16), dp(28), dp(16), dp(16))

        cert.addView(tv("🏅", 60f, Color.BLACK))
        cert.addView(tv("شهادة تقدير", 32f, 0xFFB8860B.toInt(), true))
        cert.addView(tv("تُمنح هذه الشهادة إلى", 16f, 0xFF555555.toInt()))
        cert.addView(tv(student, 30f, 0xFF1A237E.toInt(), true))
        if (message.isNotBlank()) cert.addView(tv("«$message»", 18f, 0xFF333333.toInt()))
        cert.addView(tv("تقديرًا لتميزه واجتهاده", 16f, 0xFF555555.toInt()))
        cert.addView(tv("الأستاذ / $teacher", 20f, 0xFF222222.toInt(), true))
        val tag = tv("1Bac", 10f, 0xFF888888.toInt())
        tag.gravity = Gravity.END
        cert.addView(tag, LinearLayout.LayoutParams(-1, -2))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(40), dp(20), dp(30))
        box.addView(cert, LinearLayout.LayoutParams(-1, -2))

        val save = Button(this)
        save.text = "💾 حفظ كصورة"
        save.setOnClickListener { saveImage(cert) }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(20)
        box.addView(save, lp)

        val back = Button(this)
        back.text = "رجوع"
        back.setOnClickListener { finish() }
        box.addView(back, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        scroll.addView(box)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        root.addView(ConfettiView(this), FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun saveImage(v: View) {
        try {
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "1Bac_honor_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/1Bac")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) throw Exception("no uri")
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "تم الحفظ في معرض الصور ✅", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر الحفظ", Toast.LENGTH_SHORT).show()
        }
    }
}
