package com.example.bac1

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ReportActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false, color: Int = 0xFF222222.toInt()): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun gradeOf(p: Int) = when {
        p >= 85 -> "ممتاز 🌟"
        p >= 75 -> "جيد جدًا 👏"
        p >= 65 -> "جيد 👍"
        p >= 50 -> "مقبول 🙂"
        else -> "يحتاج إلى مجهود 💪"
    }

    private fun colorOf(p: Int) = when {
        p >= 75 -> 0xFF2E7D32.toInt()
        p >= 50 -> 0xFFEF6C00.toInt()
        else -> 0xFFC62828.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        container.addView(tv("📊 مستواي الدراسي", 24f, true))
        container.addView(tv("جارٍ تحميل التقرير...", 14f))

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val id = prefs.all["student_id"]?.toString().orEmpty()
        val key = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }

        FirebaseFirestore.getInstance().collection("dailyAttempts")
            .whereEqualTo("studentKey", key).get()
            .addOnSuccessListener { res ->
                val rows = res.documents.filter { it.getBoolean("finished") == true }
                build(rows.map {
                    Row(
                        it.getString("subject")?.ifBlank { "عام" } ?: "عام",
                        (it.getLong("score") ?: 0L).toInt(),
                        (it.getLong("total") ?: 0L).toInt(),
                        it.getString("date") ?: ""
                    )
                })
            }
            .addOnFailureListener {
                container.removeAllViews()
                container.addView(tv("تعذّر تحميل التقرير، تحقق من الإنترنت.", 16f, true))
            }
    }

    private data class Row(val subject: String, val score: Int, val total: Int, val date: String)

    private fun streak(dates: Set<String>): Int {
        val tz = TimeZone.getTimeZone("Africa/Cairo")
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = tz
        val cal = Calendar.getInstance(tz)
        if (!dates.contains(fmt.format(cal.time))) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (dates.contains(fmt.format(cal.time))) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    private fun build(rows: List<Row>) {
        container.removeAllViews()
        container.addView(tv("📊 مستواي الدراسي", 24f, true))

        val valid = rows.filter { it.total > 0 }
        if (valid.isEmpty()) {
            container.addView(tv("لا توجد بيانات بعد. حل تحدي اليوم لتظهر نتائجك هنا 🔥", 16f))
            return
        }

        val totalScore = valid.sumOf { it.score }
        val totalQ = valid.sumOf { it.total }
        val overall = Math.round(totalScore * 100.0 / totalQ).toInt()

        container.addView(tv("المستوى العام", 16f))
        container.addView(tv("${gradeOf(overall)}", 30f, true, colorOf(overall)))
        container.addView(tv("المتوسط في كل المواد: $overall%", 18f, true))

        // المواد
        container.addView(tv("متوسطك في كل مادة", 20f, true))
        val bySubject = LinkedHashMap<String, IntArray>()
        valid.forEach {
            val a = bySubject.getOrPut(it.subject) { IntArray(2) }
            a[0] += it.score
            a[1] += it.total
        }
        val pcts = bySubject.map { (s, a) -> Triple(s, Math.round(a[0] * 100.0 / a[1]).toInt(), a) }
            .sortedByDescending { it.second }
        pcts.forEach { (s, p, a) ->
            container.addView(tv("$s: $p% (${a[0]} من ${a[1]})", 16f, false, colorOf(p)))
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = 100
            bar.progress = p
            bar.progressTintList = android.content.res.ColorStateList.valueOf(colorOf(p))
            container.addView(bar, LinearLayout.LayoutParams(-1, dp(10)))
        }
        if (pcts.size >= 2) {
            container.addView(tv("💪 أقوى مادة: ${pcts.first().first} (${pcts.first().second}%)", 16f, true))
            container.addView(tv("⚠️ تحتاج تركيز: ${pcts.last().first} (${pcts.last().second}%)", 16f, true))
        }

        // إحصائيات
        container.addView(tv("إحصائياتك", 20f, true))
        container.addView(tv("🎯 عدد التحديات التي حللتها: ${valid.size}", 16f))
        container.addView(tv("❓ الأسئلة: أجبتَ $totalScore صحيحة من $totalQ", 16f))
        val best = valid.maxWithOrNull(
            compareBy<Row>({ it.score * 1000 / it.total }, { it.total })
        )!!
        container.addView(tv(
            "🏅 أفضل نتيجة: ${best.score} من ${best.total} في ${best.subject} (${best.date})", 16f))
        val st = streak(valid.map { it.date }.toSet())
        container.addView(tv("🔥 المواظبة: $st ${if (st == 1) "يوم" else "أيام"} متتالية", 16f))
        container.addView(tv("يتحدّث التقرير مع كل تحدٍّ جديد تحله.", 12f))
    }
}
