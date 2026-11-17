package com.example.bac1

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MyTeachersActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEACHER_CODE = "EXTRA_TEACHER_CODE"
        const val EXTRA_TEACHER_NAME = "EXTRA_TEACHER_NAME"
    }

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private lateinit var noTeachersText: TextView
    private var token = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_teachers)
        container = findViewById(R.id.myTeachersContainer)
        noTeachersText = findViewById(R.id.noTeachersText)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        token += 1
        val my = token
        container.removeAllViews()
        noTeachersText.visibility = View.GONE

        val id = JoinUtil.identifier(this)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val codes = HashSet(prefs.getStringSet("joined_teacher_codes", emptySet()) ?: emptySet())

        db.collection("joinRequests").whereEqualTo("identifier", id).get()
            .addOnSuccessListener { rs ->
                val status = HashMap<String, String>()
                val names = HashMap<String, String>()
                for (d in rs.documents) {
                    val c = d.getString("teacherCode") ?: continue
                    status[c] = d.getString("status") ?: ""
                    names[c] = d.getString("teacherName") ?: "المدرس"
                }
                for ((c, s) in status) {
                    if (s == "approved") codes.add(c)
                    if (s == "blocked") codes.remove(c)
                }
                verify(my, id, codes, status, names)
            }
            .addOnFailureListener { verify(my, id, codes, HashMap(), HashMap()) }
    }

    // نتأكد إن الطالب لسه مسجّل عند كل مدرس (لو اتخرّج بيتشال)
    private fun verify(
        my: Int, id: String, codes: Set<String>,
        status: Map<String, String>, names: Map<String, String>
    ) {
        val valid = ArrayList<String>()
        if (codes.isEmpty()) {
            showAll(my, valid, status, names)
            return
        }
        var left = codes.size
        for (c in codes) {
            db.collection("users").document(JoinUtil.docId(c, id)).get()
                .addOnSuccessListener { u ->
                    if (u.exists()) valid.add(c)
                    left -= 1
                    if (left == 0) showAll(my, valid, status, names)
                }
                .addOnFailureListener {
                    valid.add(c)
                    left -= 1
                    if (left == 0) showAll(my, valid, status, names)
                }
        }
    }

    private fun showAll(my: Int, valid: List<String>, status: Map<String, String>, names: Map<String, String>) {
        if (my != token) return
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        prefs.edit().putStringSet("joined_teacher_codes", HashSet(valid)).apply()

        val others = status.filter { it.key !in valid && it.value in listOf("pending", "rejected", "blocked") }
        if (valid.isEmpty() && others.isEmpty()) {
            noTeachersText.visibility = View.VISIBLE
            return
        }
        others.forEach { (c, s) -> container.addView(statusBlock(names[c] ?: "المدرس", s)) }
        valid.forEach { addTeacher(my, it) }
    }

    private fun statusBlock(name: String, s: String): TextView {
        val (msg, fill) = when (s) {
            "pending" -> Pair("⏳ طلبك عند أستاذ $name لسه قيد المراجعة", "#FFF3E0")
            "rejected" -> Pair("❌ أستاذ $name رفض طلبك. تقدر تبعت طلب جديد من زرار الانضمام.", "#FFEBEE")
            else -> Pair("🚫 مش مسموح لك تنضم لأستاذ $name. كلّمه مباشرة.", "#FFEBEE")
        }
        val t = TextView(this)
        t.text = msg
        t.textSize = 15f
        t.setTextColor(Color.parseColor("#4E342E"))
        t.setPadding(dp(14), dp(12), dp(14), dp(12))
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor(fill))
        bg.cornerRadius = dp(14).toFloat()
        t.background = bg
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(dp(8), dp(6), dp(8), dp(6))
        t.layoutParams = lp
        return t
    }

    private fun addTeacher(my: Int, code: String) {
        db.collection("teachers").document(code).get()
            .addOnSuccessListener { doc ->
                if (my != token || !doc.exists()) return@addOnSuccessListener

                val name = doc.getString("name") ?: "مدرس"
                val subject = doc.getString("subject") ?: ""

                val block = LayoutInflater.from(this).inflate(R.layout.item_my_teacher, container, false)
                block.findViewById<TextView>(R.id.tvMyTeacherName).text = name
                block.findViewById<TextView>(R.id.tvMyTeacherSubject).text = "مادة: $subject"

                block.findViewById<Button>(R.id.btnOpenTeacherContent).setOnClickListener {
                    val intent = Intent(this, TeacherContentActivity::class.java)
                    intent.putExtra(EXTRA_TEACHER_CODE, code)
                    intent.putExtra(EXTRA_TEACHER_NAME, name)
                    startActivity(intent)
                }
                container.addView(block)
            }
    }
}
