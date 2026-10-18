package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ChallengeHomeActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private var studentId = ""
    private var studentName = ""
    private var selectedCount = 5
    private var creating = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(10), 0, dp(4))
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

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        studentId = prefs.getString("student_id", "") ?: ""
        studentName = prefs.getString("student_name", "طالب") ?: "طالب"
    }

    override fun onResume() {
        super.onResume()
        render(-2)
        ChallengeManager.getBalance(studentId) { bal -> render(bal) }
    }

    private fun render(balance: Int) {
        container.removeAllViews()
        container.addView(tv("⚔️ اتحدي أصحابك", 24f, true))

        val balText = when (balance) {
            -2 -> "جارٍ تحميل رصيدك..."
            -1 -> "تعذّر تحميل الرصيد"
            else -> "رصيد نقاط التحدي: $balance ⭐"
        }
        container.addView(tv(balText, 18f, true))
        container.addView(tv("إنشاء غرفة يكلّف نقطة واحدة. الدخول بكود صديقك مجاني. الفائز +2 والتعادل +1.", 13f))

        container.addView(tv("عدد الأسئلة", 16f, true))
        val rg = RadioGroup(this)
        rg.orientation = RadioGroup.HORIZONTAL
        listOf(5, 10, 15).forEach { n ->
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = "$n"
            rb.setTextColor(0xFF000000.toInt())
            rb.isChecked = (n == selectedCount)
            rb.setOnClickListener { selectedCount = n }
            rg.addView(rb)
        }
        container.addView(rg)

        container.addView(btn("➕ إنشاء غرفة جديدة (بنقطة)", 0xFFE65100) {
            if (balance == 0) {
                Toast.makeText(this, "رصيدك انتهى، اربح نقاطًا من الغرف", Toast.LENGTH_SHORT).show()
                return@btn
            }
            if (creating) return@btn
            creating = true
            ChallengeManager.createRoom(studentId, studentName, selectedCount) { code, err ->
                creating = false
                if (code != null) openRoom(code)
                else Toast.makeText(this, err ?: "حدث خطأ", Toast.LENGTH_LONG).show()
            }
        })

        container.addView(tv("الدخول بكود غرفة", 16f, true))
        val etCode = EditText(this)
        etCode.hint = "مثال: K7M2Q"
        etCode.setTextColor(0xFF000000.toInt())
        etCode.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        container.addView(etCode)
        container.addView(btn("دخول", 0xFF283593) {
            val code = etCode.text.toString().trim().uppercase()
            if (code.length != 5) {
                Toast.makeText(this, "الكود مكوّن من 5 خانات", Toast.LENGTH_SHORT).show()
                return@btn
            }
            ChallengeManager.joinRoom(code, studentId, studentName) { ok, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (ok) openRoom(code)
            }
        })

        container.addView(tv("غرفي (آخر 24 ساعة)", 16f, true))
        if (balance != -2) {
            ChallengeManager.myRooms(studentId) { rooms ->
                if (rooms.isEmpty()) {
                    container.addView(tv("لا توجد غرف حاليًا.", 14f))
                } else {
                    rooms.forEach { r ->
                        val code = r.getString("code") ?: return@forEach
                        val owner = r.getString("ownerName") ?: ""
                        val count = (r.get("playerIds") as? List<*>)?.size ?: 0
                        container.addView(btn("غرفة $code · أنشأها $owner · $count لاعب", 0xFF00796B) {
                            openRoom(code)
                        })
                    }
                }
            }
        }
    }

    private fun openRoom(code: String) {
        val i = Intent(this, RoomLobbyActivity::class.java)
        i.putExtra("code", code)
        startActivity(i)
    }
}
