package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class CourseStudentHomeActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var body: LinearLayout
    private var teacherCode = ""
    private var playerKey = ""
    private var studentName = ""

    private fun dp(v: Int) = LeagueUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = CourseUtil.page(this)
        val st = CourseUtil.student(this)
        playerKey = st.first
        studentName = st.second
        teacherCode = intent.getStringExtra("teacherCode") ?: ""
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun header() {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🎬 الكورسات", 24f, LeagueUi.GOLD, true))
    }

    private fun load() {
        header()
        if (teacherCode.isEmpty()) {
            body.addView(LeagueUi.note(this, "تعذّر تحديد المدرس"))
            return
        }
        body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        CourseUtil.myGroups(db, teacherCode, studentName) { mine ->
            db.collection("courses").whereEqualTo("teacherCode", teacherCode).get()
                .addOnSuccessListener { snap ->
                    val courses = snap.documents.sortedBy { it.getLong("createdAt") ?: 0L }
                    db.collection("courseProgress").whereEqualTo("studentId", playerKey).get()
                        .addOnSuccessListener { pr ->
                            val passedBy = HashMap<String, Set<String>>()
                            for (p in pr.documents) {
                                val cid = p.getString("courseId") ?: continue
                                passedBy[cid] = (p.get("passed") as? List<*>)
                                    ?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                            }
                            show(courses, passedBy, mine)
                        }
                        .addOnFailureListener { show(courses, HashMap(), mine) }
                }
                .addOnFailureListener {
                    header()
                    body.addView(LeagueUi.note(this, "تعذّر الاتصال، تحقق من الإنترنت وحاول تاني."))
                }
        }
    }

    private fun show(courses: List<DocumentSnapshot>, passedBy: Map<String, Set<String>>, mine: Set<String>) {
        header()
        var shown = 0
        for (c in courses) {
            val lessons = CourseUtil.visible(CourseUtil.parseLessons(c.get("lessons")), mine)
            if (lessons.isEmpty()) continue
            shown++
            val passed = passedBy[c.id] ?: emptySet()
            val flags = CourseUtil.unlocked(lessons, passed)
            val done = lessons.indices.count { flags[it] && CourseUtil.isDone(lessons[it], passed) }
            val finished = done == lessons.size

            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(16), dp(14), dp(16), dp(14))
            card.background = LeagueUi.bg(this, LeagueUi.CARD, 18, if (finished) LeagueUi.GREEN else "#34428F")
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(10)
            card.layoutParams = lp

            card.addView(
                LeagueUi.tv(this, (c.getString("title") ?: "") + if (finished) "  🎓" else "", 19f, LeagueUi.TEXT, true)
            )
            card.addView(LeagueUi.tv(this, "📊 $done من ${lessons.size} دروس", 13f, LeagueUi.MUTED))
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.background = LeagueUi.bg(this, "#34428F", 4)
            val fill = View(this)
            fill.background = LeagueUi.bg(this, LeagueUi.GOLD, 4)
            bar.addView(fill, LinearLayout.LayoutParams(0, -1, done.toFloat()))
            bar.addView(View(this), LinearLayout.LayoutParams(0, -1, (lessons.size - done).toFloat()))
            val blp = LinearLayout.LayoutParams(-1, dp(8))
            blp.topMargin = dp(8)
            card.addView(bar, blp)

            card.setOnClickListener {
                startActivity(Intent(this, CourseLessonsActivity::class.java).putExtra("courseId", c.id))
            }
            body.addView(card)
        }
        if (shown == 0) {
            body.addView(LeagueUi.note(this, "لا توجد كورسات متاحة ليك دلوقتي 🙂\nأول ما المدرس ينشر درس لمجموعتك هتلاقيه هنا."))
        }
    }
}
