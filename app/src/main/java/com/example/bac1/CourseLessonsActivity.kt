package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CourseLessonsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var body: LinearLayout
    private var courseId = ""
    private var playerKey = ""
    private var studentName = ""

    private fun dp(v: Int) = LeagueUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = CourseUtil.page(this)
        val st = CourseUtil.student(this)
        playerKey = st.first
        studentName = st.second
        courseId = intent.getStringExtra("courseId") ?: ""
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun message(msg: String) {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🎬 الكورس", 24f, LeagueUi.GOLD, true))
        body.addView(LeagueUi.note(this, msg))
    }

    private fun load() {
        message("جارٍ التحميل...")
        db.collection("courses").document(courseId).get()
            .addOnSuccessListener { c ->
                if (!c.exists()) {
                    message("الكورس ده مش موجود")
                    return@addOnSuccessListener
                }
                val title = c.getString("title") ?: ""
                val pass = (c.getLong("passPercent") ?: 60L).toInt()
                val code = c.getString("teacherCode") ?: ""
                val all = CourseUtil.parseLessons(c.get("lessons"))
                CourseUtil.myGroups(db, code, studentName) { mine ->
                    val lessons = CourseUtil.visible(all, mine)
                    db.collection("courseProgress").document("${courseId}_$playerKey").get()
                        .addOnSuccessListener { p ->
                            val passed = (p.get("passed") as? List<*>)
                                ?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                            show(title, pass, lessons, passed, p.get("scores") as? Map<*, *>)
                        }
                        .addOnFailureListener { show(title, pass, lessons, emptySet(), null) }
                }
            }
            .addOnFailureListener { message("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    private fun show(
        title: String, pass: Int, lessons: List<CourseUtil.Lesson>,
        passed: Set<String>, scores: Map<*, *>?
    ) {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🎬 $title", 24f, LeagueUi.GOLD, true))
        val flags = CourseUtil.unlocked(lessons, passed)
        val done = lessons.indices.count { flags[it] && CourseUtil.isDone(lessons[it], passed) }
        body.addView(
            LeagueUi.note(this, "📊 تقدمك: $done من ${lessons.size} · النجاح في كل امتحان من $pass%", LeagueUi.CYAN)
        )
        if (lessons.isEmpty()) body.addView(LeagueUi.note(this, "مفيش دروس متاحة ليك لسه."))

        lessons.forEachIndexed { i, l ->
            val unlocked = flags[i]
            val hasQuiz = l.qs.isNotEmpty()
            val isPassed = hasQuiz && l.id in passed
            val icon = if (isPassed) "✅" else if (unlocked) "▶️" else "🔒"
            val sc = (scores?.get(l.id) as? Number)?.toInt()
            val sub = when {
                isPassed -> "نجحت" + (if (sc != null) " ($sc%)" else "") + " · اضغط لمراجعة الدرس"
                unlocked && hasQuiz -> "${l.qs.size} سؤال في الامتحان" +
                        (if (l.quizMinutes > 0) " · ⏱ ${l.quizMinutes} د" else "") + " · ابدأ الدرس"
                unlocked -> "من غير امتحان · ابدأ الدرس"
                else -> "خلّص امتحان الدرس السابق عشان يفتح"
            }
            val border = if (isPassed) LeagueUi.GREEN else if (unlocked) LeagueUi.GOLD else "#34428F"

            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(16), dp(14), dp(16), dp(14))
            card.background = LeagueUi.bg(this, LeagueUi.CARD, 16, border)
            card.alpha = if (unlocked) 1f else 0.6f
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(10)
            card.layoutParams = lp
            card.addView(LeagueUi.tv(this, "$icon  ${i + 1}. ${l.title}", 18f, LeagueUi.TEXT, true))
            card.addView(LeagueUi.tv(this, sub, 13f, LeagueUi.MUTED))
            card.setOnClickListener {
                if (!unlocked) {
                    Toast.makeText(this, "🔒 خلّص امتحان الدرس السابق الأول", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(
                        Intent(this, LessonPlayerActivity::class.java)
                            .putExtra("courseId", courseId)
                            .putExtra("lessonId", l.id)
                    )
                }
            }
            body.addView(card)
        }
    }
}
