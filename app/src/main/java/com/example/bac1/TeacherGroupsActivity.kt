package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class TeacherGroupsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val dayNames = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val weekOrder = listOf(7, 1, 2, 3, 4, 5, 6)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        container.removeAllViews()
        container.addView(tv("👥 مجموعاتي وجدول حصصي", 22f, true))
        val code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""

        val add = Button(this)
        add.text = "➕ مجموعة جديدة"
        add.setOnClickListener { startActivity(Intent(this, GroupEditActivity::class.java)) }
        container.addView(add)

        FirebaseFirestore.getInstance().collection("teacherGroups")
            .whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { res ->
                val groups = res.documents.sortedBy { it.getString("name") ?: "" }

                // الجدول الأسبوعي
                container.addView(tv("📅 الجدول الأسبوعي", 19f, true))
                var any = false
                for (day in weekOrder) {
                    val rows = mutableListOf<Pair<String, String>>()
                    for (g in groups) {
                        val slots = g.get("slots") as? List<*> ?: continue
                        for (s in slots) {
                            val m = s as? Map<*, *> ?: continue
                            if ((m["day"] as? Number)?.toInt() == day) {
                                val n = (g.get("studentNames") as? List<*>)?.size ?: 0
                                rows.add((m["time"] as? String ?: "") to
                                        "${g.getString("name") ?: ""} ($n طالب)")
                            }
                        }
                    }
                    if (rows.isEmpty()) continue
                    any = true
                    container.addView(tv(dayNames[day - 1], 16f, true))
                    rows.sortedBy { it.first }.forEach {
                        container.addView(tv("   🕐 ${it.first} · ${it.second}", 15f))
                    }
                }
                if (!any) container.addView(tv("لا توجد حصص بعد. أنشئ مجموعة وأضف مواعيدها.", 14f))

                // قايمة المجموعات
                container.addView(tv("المجموعات", 19f, true))
                if (groups.isEmpty()) container.addView(tv("لا توجد مجموعات.", 14f))
                groups.forEach { g ->
                    val n = (g.get("studentNames") as? List<*>)?.size ?: 0
                    val b = Button(this)
                    b.isAllCaps = false
                    b.text = "${g.getString("name") ?: ""} · $n طالب"
                    b.setOnClickListener {
                        startActivity(Intent(this, GroupEditActivity::class.java)
                            .putExtra("groupId", g.id))
                    }
                    container.addView(b)
                }
            }
            .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }
}
