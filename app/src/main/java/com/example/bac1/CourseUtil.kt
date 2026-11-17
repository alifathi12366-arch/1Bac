package com.example.bac1

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import java.util.UUID

object CourseUtil {

    class Q(val text: String, val options: List<String>, val correct: Int, val note: String)

    class Lesson(
        val id: String, val title: String, val videoId: String, val desc: String,
        val qs: List<Q>, val quizMinutes: Int = 0, val groups: List<String> = emptyList()
    )

    private val idInUrl = Regex("(?:v=|youtu\\.be/|embed/|shorts/|live/)([A-Za-z0-9_-]{11})")
    private val bareId = Regex("^[A-Za-z0-9_-]{11}$")

    fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    fun videoId(input: String): String? {
        val s = input.trim()
        if (bareId.matches(s)) return s
        return idInUrl.find(s)?.groupValues?.get(1)
    }

    fun fmtTime(sec: Int): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)

    fun parseQuestions(raw: Any?): List<Q> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val text = m["text"] as? String ?: return@mapNotNull null
            val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String } ?: return@mapNotNull null
            val c = (m["correctIndex"] as? Number)?.toInt() ?: return@mapNotNull null
            if (opts.size < 2 || c !in opts.indices) return@mapNotNull null
            Q(text, opts, c, (m["note"] as? String)?.trim() ?: "")
        }
    }

    fun parseLessons(raw: Any?): List<Lesson> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val vid = m["videoId"] as? String ?: return@mapNotNull null
            Lesson(
                id, m["title"] as? String ?: "", vid,
                (m["desc"] as? String)?.trim() ?: "",
                parseQuestions(m["questions"]),
                (m["quizMinutes"] as? Number)?.toInt() ?: 0,
                (m["groups"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        }
    }

    fun questionToMap(q: Q): HashMap<String, Any> {
        val m = hashMapOf<String, Any>(
            "text" to q.text, "options" to q.options, "correctIndex" to q.correct
        )
        if (q.note.isNotEmpty()) m["note"] = q.note
        return m
    }

    fun lessonToMap(l: Lesson): HashMap<String, Any> {
        return hashMapOf<String, Any>(
            "id" to l.id, "title" to l.title, "videoId" to l.videoId, "desc" to l.desc,
            "questions" to l.qs.map { questionToMap(it) },
            "quizMinutes" to l.quizMinutes.toLong(),
            "groups" to l.groups
        )
    }

    // الدروس اللي تظهر للطالب حسب مجموعاته (الدرس من غير مجموعات = للكل)
    fun visible(lessons: List<Lesson>, myGroups: Set<String>): List<Lesson> =
        lessons.filter { it.groups.isEmpty() || it.groups.any { g -> g in myGroups } }

    // الدرس من غير امتحان بيعتبر مكتمل لكل الطلاب
    fun isDone(l: Lesson, passed: Set<String>): Boolean = l.qs.isEmpty() || l.id in passed

    // الأول مفتوح، وكل درس بيفتح لما اللي قبله يكون مفتوح ومكتمل
    fun unlocked(list: List<Lesson>, passed: Set<String>): List<Boolean> {
        val out = ArrayList<Boolean>()
        for (i in list.indices) {
            if (i == 0) out.add(true)
            else out.add(out[i - 1] && isDone(list[i - 1], passed))
        }
        return out
    }

    // مجموعات الطالب عند المدرس ده
    fun myGroups(db: FirebaseFirestore, teacherCode: String, name: String, cb: (Set<String>) -> Unit) {
        db.collection("teacherGroups").whereArrayContains("studentNames", name).get()
            .addOnSuccessListener { res ->
                cb(res.documents.filter { it.getString("teacherCode") == teacherCode }.map { it.id }.toSet())
            }
            .addOnFailureListener { cb(emptySet()) }
    }

    fun student(ctx: Context): Pair<String, String> {
        val prefs = ctx.getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = prefs.all["student_id"]?.toString().orEmpty()
        var key = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }
        if (key.isEmpty()) key = "anon"
        return Pair(key + "_" + name.replace("/", "-"), name)
    }

    fun page(act: AppCompatActivity): LinearLayout {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor(LeagueUi.BG))
        val scroll = ScrollView(act)
        val body = LinearLayout(act)
        body.orientation = LinearLayout.VERTICAL
        val p = LeagueUi.dp(act, 16)
        body.setPadding(p, LeagueUi.dp(act, 12), p, LeagueUi.dp(act, 24))
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        act.setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return body
    }
}
