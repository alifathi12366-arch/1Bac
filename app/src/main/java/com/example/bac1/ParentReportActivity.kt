package com.example.bac1

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentReportActivity : AppCompatActivity() {

    private class Student(val id: String, val name: String, val full: String, val phone: String, val parent: String)
    private class ExamRow(val title: String, val score: Int, val total: Int, val pending: Boolean, val ts: Long)
    private class ChalRow(val title: String, val score: Int, val total: Int, val ts: Long)
    private class Data {
        val exams = mutableListOf<ExamRow>()
        val chals = mutableListOf<ChalRow>()
        var hasGroups = false
        var attTotal = 0
        var attPresent = 0
        var lessonsDone = 0
        var lessonsTotal = 0
    }

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private var code = ""
    private var teacherName = ""
    private var subject = ""
    private var startId = ""
    private var students: List<Student> = emptyList()
    private var index = 0
    private var days = 30
    private var titles: Map<String, String>? = null
    private var courses: List<DocumentSnapshot>? = null
    private val groupAtt = HashMap<String, List<DocumentSnapshot>>()
    private var lastBitmap: Bitmap? = null

    private lateinit var btnPick: Button
    private lateinit var tvParent: TextView
    private lateinit var etNote: EditText
    private lateinit var btnGen: Button
    private lateinit var btnSend: Button
    private lateinit var tvStatus: TextView
    private lateinit var ivPreview: ImageView
    private val periodBtns = mutableListOf<Pair<Int, Button>>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

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
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        code = prefs.getString("teacher_code", "") ?: ""
        teacherName = prefs.getString("teacher_name", "") ?: ""
        subject = prefs.getString("teacher_subject", "") ?: ""
        startId = intent.getStringExtra("userId") ?: ""

        // نمسح صور التقارير القديمة
        filesDir.listFiles()?.filter { it.name.startsWith("report_") }?.forEach { it.delete() }

        container.addView(tv("📊 تقرير ولي الأمر", 22f, true))
        container.addView(tv("اختر الطالب والفترة، وهيطلع تقرير بصورة جاهزة تبعتها لولي الأمر على واتساب.", 13f))

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        val prev = Button(this)
        prev.text = "▶"
        prev.setOnClickListener { step(-1) }
        btnPick = Button(this)
        btnPick.isAllCaps = false
        btnPick.text = "جارٍ تحميل الطلاب..."
        btnPick.setOnClickListener { pickStudent() }
        val next = Button(this)
        next.text = "◀"
        next.setOnClickListener { step(1) }
        nav.addView(prev, LinearLayout.LayoutParams(dp(56), -2))
        nav.addView(btnPick, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(next, LinearLayout.LayoutParams(dp(56), -2))
        container.addView(nav)

        tvParent = tv("", 13f)
        container.addView(tvParent)

        container.addView(tv("الفترة", 14f, true))
        val prow = LinearLayout(this)
        prow.orientation = LinearLayout.HORIZONTAL
        listOf(Pair(7, "آخر أسبوع"), Pair(30, "آخر 30 يوم"), Pair(0, "كل الفترة")).forEach { (n, label) ->
            val b = Button(this)
            b.isAllCaps = false
            b.text = label
            b.textSize = 13f
            b.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            b.setOnClickListener {
                days = n
                refreshPeriod()
            }
            prow.addView(b)
            periodBtns.add(Pair(n, b))
        }
        container.addView(prow)
        refreshPeriod()

        container.addView(tv("ملاحظة منك لولي الأمر (اختياري)", 14f, true))
        etNote = EditText(this)
        etNote.hint = "مثال: الطالب متميز ويحتاج مراجعة بسيطة في الوحدة الثانية"
        etNote.setTextColor(0xFF000000.toInt())
        etNote.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        etNote.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(300))
        etNote.minLines = 2
        container.addView(etNote)

        btnGen = Button(this)
        btnGen.text = "📄 أنشئ التقرير"
        btnGen.setTextColor(0xFFFFFFFF.toInt())
        btnGen.backgroundTintList = ColorStateList.valueOf(0xFF6A3CD6.toInt())
        btnGen.setOnClickListener { generate() }
        container.addView(btnGen)

        tvStatus = tv("", 14f)
        container.addView(tvStatus)

        ivPreview = ImageView(this)
        ivPreview.adjustViewBounds = true
        ivPreview.visibility = View.GONE
        container.addView(ivPreview, LinearLayout.LayoutParams(-1, -2))

        btnSend = Button(this)
        btnSend.text = "📲 أرسل لولي الأمر على واتساب"
        btnSend.setTextColor(0xFFFFFFFF.toInt())
        btnSend.backgroundTintList = ColorStateList.valueOf(0xFF25D366.toInt())
        btnSend.isEnabled = false
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(40)
        btnSend.layoutParams = lp
        btnSend.setOnClickListener { shareToWhatsApp() }
        container.addView(btnSend)

        loadStudents()
    }

    private fun refreshPeriod() {
        periodBtns.forEach { (n, b) ->
            val on = n == days
            b.backgroundTintList = ColorStateList.valueOf(if (on) 0xFF6A3CD6.toInt() else 0xFFBDBDBD.toInt())
            b.setTextColor(if (on) Color.WHITE else Color.BLACK)
        }
    }

    // ---------- الطلاب ----------
    private fun loadStudents() {
        if (code.isEmpty()) {
            btnPick.text = "لا يوجد كود مدرس"
            return
        }
        db.collection("users").whereEqualTo("joined_teacher_code", code).get()
            .addOnSuccessListener { res ->
                students = res.documents.map { d ->
                    val name = d.getString("name") ?: ""
                    val full = d.getString("fullName") ?: ""
                    Student(
                        d.id, name, if (full.isNotBlank()) full else name,
                        d.getString("phone") ?: "", d.getString("parentPhone") ?: ""
                    )
                }.filter { it.name.isNotBlank() }.sortedBy { it.full }
                index = students.indexOfFirst { it.id == startId }.coerceAtLeast(0)
                showStudent()
            }
            .addOnFailureListener { btnPick.text = "تعذّر تحميل الطلاب" }
    }

    private fun showStudent() {
        val s = students.getOrNull(index)
        if (s == null) {
            btnPick.text = "لسه مفيش طلاب"
            tvParent.text = ""
            return
        }
        btnPick.text = "${s.full}  (${index + 1} من ${students.size})"
        tvParent.text = if (s.parent.isEmpty()) "⚠️ مفيش رقم ولي أمر متسجّل للطالب ده"
        else "👨‍👩‍👦 ولي الأمر: ${JoinUtil.localPhone(s.parent)}"
        lastBitmap = null
        ivPreview.visibility = View.GONE
        btnSend.isEnabled = false
        tvStatus.text = ""
    }

    private fun step(by: Int) {
        if (students.isEmpty()) return
        index = (index + by + students.size) % students.size
        showStudent()
    }

    private fun pickStudent() {
        if (students.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("اختر الطالب")
            .setItems(students.map { it.full }.toTypedArray()) { _, which ->
                index = which
                showStudent()
            }
            .show()
    }

    // ---------- تجميع البيانات ----------
    private fun generate() {
        val s = students.getOrNull(index) ?: return
        btnGen.isEnabled = false
        btnSend.isEnabled = false
        tvStatus.text = "⏳ جارٍ تجميع بيانات الطالب..."
        val since = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else 0L
        val d = Data()
        var pending = 3
        val finish: () -> Unit = {
            pending -= 1
            if (pending == 0) buildReport(s, d)
        }
        loadExams(s, since, d, finish)
        loadChallenges(s, since, d, finish)
        loadGroupBased(s, since, d, finish)
    }

    private fun loadExams(s: Student, since: Long, d: Data, finish: () -> Unit) {
        db.collection("results").whereEqualTo("teacherCode", code).whereEqualTo("studentName", s.name).get()
            .addOnSuccessListener { rs ->
                for (doc in rs.documents) {
                    val ts = doc.getLong("timestamp") ?: 0L
                    if (since > 0 && ts < since) continue
                    val total = (doc.getLong("totalScore") ?: 0L).toInt()
                    val auto = (doc.getLong("autoScore") ?: 0L).toInt()
                    val es = (doc.get("essayAnswers") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
                    val awarded = es.map { (it["awardedScore"] as? Number)?.toInt() ?: -1 }
                    val pendingEssay = awarded.any { it < 0 }
                    val essaySum = awarded.sumOf { maxOf(0, it) }
                    d.exams.add(ExamRow(doc.getString("examTitle") ?: "امتحان", auto + essaySum, total, pendingEssay, ts))
                }
                finish()
            }
            .addOnFailureListener { finish() }
    }

    private fun ensureTitles(cb: (Map<String, String>) -> Unit) {
        val t = titles
        if (t != null) {
            cb(t)
            return
        }
        db.collection("teacherChallenges").whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { r ->
                val m = HashMap<String, String>()
                r.documents.forEach { m[it.id] = it.getString("title") ?: "تحدي" }
                titles = m
                cb(m)
            }
            .addOnFailureListener { cb(HashMap()) }
    }

    private fun loadChallenges(s: Student, since: Long, d: Data, finish: () -> Unit) {
        db.collection("teacherChallengeResults").whereEqualTo("teacherCode", code)
            .whereEqualTo("studentName", s.name).get()
            .addOnSuccessListener { rs ->
                ensureTitles { t ->
                    for (doc in rs.documents) {
                        if (doc.getBoolean("finished") != true) continue
                        val ts = doc.getLong("finishedAt") ?: doc.getLong("startedAt") ?: 0L
                        if (since > 0 && ts < since) continue
                        val cid = doc.getString("challengeId") ?: ""
                        d.chals.add(
                            ChalRow(
                                t[cid] ?: "تحدي",
                                (doc.getLong("score") ?: 0L).toInt(),
                                (doc.getLong("total") ?: 0L).toInt(), ts
                            )
                        )
                    }
                    finish()
                }
            }
            .addOnFailureListener { finish() }
    }

    private fun loadGroupBased(s: Student, since: Long, d: Data, finish: () -> Unit) {
        db.collection("teacherGroups").whereArrayContains("studentNames", s.name).get()
            .addOnSuccessListener { gs ->
                val groups = gs.documents.filter { it.getString("teacherCode") == code }
                d.hasGroups = groups.isNotEmpty()
                val ids = groups.map { it.id }.toSet()
                attendanceFor(groups, s.name, since, d) {
                    coursesFor(s, ids, d) { finish() }
                }
            }
            .addOnFailureListener { finish() }
    }

    private fun ensureGroupAtt(gid: String, cb: (List<DocumentSnapshot>) -> Unit) {
        val c = groupAtt[gid]
        if (c != null) {
            cb(c)
            return
        }
        db.collection("attendanceSessions").whereEqualTo("groupId", gid).get()
            .addOnSuccessListener { r ->
                groupAtt[gid] = r.documents
                cb(r.documents)
            }
            .addOnFailureListener { cb(emptyList()) }
    }

    private fun attendanceFor(
        groups: List<DocumentSnapshot>, name: String, since: Long, d: Data, next: () -> Unit
    ) {
        if (groups.isEmpty()) {
            next()
            return
        }
        var left = groups.size
        for (g in groups) {
            ensureGroupAtt(g.id) { docs ->
                val sessions = HashMap<String, Long>()
                val attended = HashSet<String>()
                for (x in docs) {
                    val key = "${x.getString("date") ?: ""}|${x.getString("slot") ?: ""}"
                    val ts = x.getLong("createdAt") ?: 0L
                    val old = sessions[key]
                    if (old == null || ts < old) sessions[key] = ts
                    if ((x.getString("studentName") ?: "") == name) attended.add(key)
                }
                for ((key, ts) in sessions) {
                    if (since > 0 && ts < since) continue
                    d.attTotal += 1
                    if (key in attended) d.attPresent += 1
                }
                left -= 1
                if (left == 0) next()
            }
        }
    }

    private fun ensureCourses(cb: (List<DocumentSnapshot>) -> Unit) {
        val c = courses
        if (c != null) {
            cb(c)
            return
        }
        db.collection("courses").whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { r ->
                courses = r.documents
                cb(r.documents)
            }
            .addOnFailureListener { cb(emptyList()) }
    }

    private fun coursesFor(s: Student, myGroups: Set<String>, d: Data, next: () -> Unit) {
        ensureCourses { cs ->
            db.collection("courseProgress").whereEqualTo("studentName", s.name).get()
                .addOnSuccessListener { pr ->
                    val passedBy = HashMap<String, Set<String>>()
                    for (p in pr.documents) {
                        if (p.getString("teacherCode") != code) continue
                        val cid = p.getString("courseId") ?: continue
                        passedBy[cid] = (p.get("passed") as? List<*>)
                            ?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                    }
                    for (c in cs) {
                        val lessons = CourseUtil.visible(CourseUtil.parseLessons(c.get("lessons")), myGroups)
                        if (lessons.isEmpty()) continue
                        val passed = passedBy[c.id] ?: emptySet()
                        val flags = CourseUtil.unlocked(lessons, passed)
                        d.lessonsTotal += lessons.size
                        d.lessonsDone += lessons.indices.count { flags[it] && CourseUtil.isDone(lessons[it], passed) }
                    }
                    next()
                }
                .addOnFailureListener { next() }
        }
    }

    // ---------- رسم التقرير ----------
    private fun pctColor(p: Int): String = if (p >= 85) "#2E7D32" else if (p >= 65) "#EF8F00" else "#C62828"

    private fun rtv(text: String, size: Float, color: String, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        t.gravity = Gravity.START
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun shape(fill: String, radius: Int, stroke: String? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(fill))
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != null) g.setStroke(dp(1), Color.parseColor(stroke))
        return g
    }

    private fun section(title: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        c.background = shape("#FFFFFF", 16, "#E3DEFA")
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(dp(12), dp(6), dp(12), dp(6))
        c.layoutParams = lp
        c.addView(rtv(title, 16f, "#4B3FD1", true))
        return c
    }

    private fun line(left: String, chip: String, pct: Int?): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(6), 0, dp(2))
        val t = rtv(left, 14f, "#241B4E")
        r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        val c = TextView(this)
        c.text = chip
        c.textSize = 13f
        c.setTypeface(null, Typeface.BOLD)
        c.setTextColor(Color.WHITE)
        c.setPadding(dp(10), dp(3), dp(10), dp(3))
        c.background = shape(if (pct == null) "#9E9E9E" else pctColor(pct), 12)
        val clp = LinearLayout.LayoutParams(-2, -2)
        clp.marginStart = dp(8)
        r.addView(c, clp)
        return r
    }

    private fun statBox(value: String, label: String, color: String): LinearLayout {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER
        b.setPadding(dp(6), dp(12), dp(6), dp(12))
        b.background = shape("#FFFFFF", 14, "#E3DEFA")
        val v = rtv(value, 22f, color, true)
        v.gravity = Gravity.CENTER
        val l = rtv(label, 11f, "#5E5A7E")
        l.gravity = Gravity.CENTER
        b.addView(v)
        b.addView(l)
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.setMargins(dp(4), 0, dp(4), 0)
        b.layoutParams = lp
        return b
    }

    private fun bar(done: Int, total: Int): LinearLayout {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.HORIZONTAL
        b.background = shape("#E3DEFA", 5)
        val fill = View(this)
        fill.background = shape("#7A33E0", 5)
        b.addView(fill, LinearLayout.LayoutParams(0, -1, done.toFloat()))
        b.addView(View(this), LinearLayout.LayoutParams(0, -1, (total - done).coerceAtLeast(0).toFloat()))
        val lp = LinearLayout.LayoutParams(-1, dp(10))
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    private fun buildReport(s: Student, d: Data) {
        val graded = d.exams.filter { !it.pending && it.total > 0 }
        val examAvg: Int? = if (graded.isEmpty()) null else graded.sumOf { it.score * 100 / it.total } / graded.size
        val chalOk = d.chals.filter { it.total > 0 }
        val chalAvg: Int? = if (chalOk.isEmpty()) null else chalOk.sumOf { it.score * 100 / it.total } / chalOk.size
        val attPct: Int? = if (d.attTotal > 0) d.attPresent * 100 / d.attTotal else null
        val period = when (days) {
            7 -> "آخر 7 أيام"
            30 -> "آخر 30 يوم"
            else -> "كل الفترة"
        }
        val today = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor("#F4F2FF"))
        root.layoutParams = ViewGroup.LayoutParams(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT)

        // الهيدر
        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(18), dp(18), dp(18), dp(18))
        head.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#7A33E0"), Color.parseColor("#4F1AA0"))
        )
        head.addView(rtv("1Bac 🎓", 14f, "#E1D5FF", true))
        head.addView(rtv("تقرير ولي الأمر", 26f, "#FFFFFF", true))
        head.addView(rtv("أستاذ $teacherName" + (if (subject.isNotEmpty()) " · $subject" else ""), 14f, "#E1D5FF"))
        head.addView(rtv("📅 $period · حتى $today", 12f, "#CDBBFF"))
        root.addView(head)

        // الطالب
        val who = section("👤 الطالب")
        who.addView(rtv(s.full, 20f, "#241B4E", true))
        root.addView(who)

        // الملخص
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        val slp = LinearLayout.LayoutParams(-1, -2)
        slp.setMargins(dp(8), dp(6), dp(8), dp(6))
        stats.layoutParams = slp
        stats.addView(statBox(if (examAvg != null) "$examAvg%" else "—", "متوسط الامتحانات", if (examAvg != null) pctColor(examAvg) else "#9E9E9E"))
        stats.addView(statBox(if (attPct != null) "$attPct%" else "—", "الحضور", if (attPct != null) pctColor(attPct) else "#9E9E9E"))
        stats.addView(
            statBox(
                if (d.lessonsTotal > 0) "${d.lessonsDone}/${d.lessonsTotal}" else "—", "الدروس",
                "#4B3FD1"
            )
        )
        root.addView(stats)

        // الامتحانات
        val ex = section("📝 الامتحانات")
        if (d.exams.isEmpty()) {
            ex.addView(rtv("لا توجد امتحانات في الفترة دي", 13f, "#7A7A8C"))
        } else {
            val sorted = d.exams.sortedByDescending { it.ts }
            sorted.take(8).forEach { e ->
                if (e.pending) ex.addView(line(e.title, "⏳ قيد التصحيح", null))
                else {
                    val p = if (e.total > 0) e.score * 100 / e.total else 0
                    ex.addView(line(e.title, "${e.score}/${e.total} · $p%", p))
                }
            }
            if (sorted.size > 8) ex.addView(rtv("+ ${sorted.size - 8} امتحانات أخرى", 12f, "#7A7A8C"))
        }
        root.addView(ex)

        // التحديات
        val ch = section("🎯 التحديات")
        if (d.chals.isEmpty()) {
            ch.addView(rtv("لم يشارك في تحديات في الفترة دي", 13f, "#7A7A8C"))
        } else {
            val sorted = d.chals.sortedByDescending { it.ts }
            sorted.take(6).forEach { c ->
                val p = if (c.total > 0) c.score * 100 / c.total else 0
                ch.addView(line(c.title, "${c.score}/${c.total} · $p%", p))
            }
            if (sorted.size > 6) ch.addView(rtv("+ ${sorted.size - 6} تحديات أخرى", 12f, "#7A7A8C"))
        }
        root.addView(ch)

        // الحضور
        val at = section("📅 الحضور")
        if (d.hasGroups && d.attTotal > 0) {
            at.addView(rtv("حضر ${d.attPresent} من ${d.attTotal} حصة ($attPct%)", 14f, pctColor(attPct ?: 0), true))
            val absent = d.attTotal - d.attPresent
            at.addView(rtv(if (absent == 0) "لم يتغيب عن أي حصة 👏" else "غاب $absent حصة", 13f, "#5E5A7E"))
        } else {
            at.addView(rtv("لا يوجد سجل حضور في الفترة دي", 13f, "#7A7A8C"))
        }
        root.addView(at)

        // الكورسات
        if (d.lessonsTotal > 0) {
            val co = section("🎬 الكورسات")
            co.addView(rtv("أنهى ${d.lessonsDone} من ${d.lessonsTotal} درس", 14f, "#241B4E", true))
            co.addView(bar(d.lessonsDone, d.lessonsTotal))
            root.addView(co)
        }

        // رأي الأستاذ
        val rem = section("💬 رأي الأستاذ")
        if (examAvg != null) {
            rem.addView(
                rtv(
                    when {
                        examAvg >= 90 -> "🌟 مستوى ممتاز في الامتحانات"
                        examAvg >= 75 -> "👍 مستوى جيد جدًا في الامتحانات"
                        examAvg >= 60 -> "🙂 مستوى جيد ويحتاج شوية اجتهاد"
                        else -> "⚠️ يحتاج متابعة ومراجعة أكتر"
                    }, 14f, "#241B4E"
                )
            )
        }
        if (attPct != null) {
            rem.addView(
                rtv(
                    when {
                        attPct >= 90 -> "👏 حضور منتظم"
                        attPct >= 70 -> "🙂 حضور جيد"
                        else -> "⚠️ غياب متكرر، برجاء المتابعة"
                    }, 14f, "#241B4E"
                )
            )
        }
        val note = etNote.text.toString().trim()
        if (note.isNotEmpty()) {
            val n = rtv("📝 $note", 14f, "#4E342E")
            n.setPadding(dp(10), dp(8), dp(10), dp(8))
            n.background = shape("#FFF8E1", 10)
            val nlp = LinearLayout.LayoutParams(-1, -2)
            nlp.topMargin = dp(8)
            n.layoutParams = nlp
            rem.addView(n)
        }
        if (examAvg == null && attPct == null && note.isEmpty()) {
            rem.addView(rtv("لا توجد بيانات كافية في الفترة دي", 13f, "#7A7A8C"))
        }
        root.addView(rem)

        val foot = rtv("تم إنشاء التقرير من تطبيق 1Bac", 11f, "#7A7A8C")
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, dp(10), 0, dp(14))
        root.addView(foot)

        val bmp = toBitmap(root)
        lastBitmap = bmp
        ivPreview.setImageBitmap(bmp)
        ivPreview.visibility = View.VISIBLE
        btnGen.isEnabled = true
        btnSend.isEnabled = true
        tvStatus.text = "✅ التقرير جاهز، راجعه وابعته"
    }

    private fun toBitmap(v: View): Bitmap {
        val w = dp(360)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val h = v.measuredHeight
        v.layout(0, 0, w, h)
        val scale = maxOf(1f, 1080f / w)
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        v.draw(c)
        return bmp
    }

    // ---------- الإرسال ----------
    private fun shareToWhatsApp() {
        val bmp = lastBitmap ?: return
        val s = students.getOrNull(index) ?: return
        val file = File(filesDir, "report_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            toast("تعذّر حفظ الصورة")
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val digits = s.parent.filter { it.isDigit() }
        val caption = "📋 تقرير الطالب ${s.full}\nأستاذ $teacherName" +
                (if (subject.isNotEmpty()) " · $subject" else "") + "\nمن تطبيق 1Bac"

        fun build(pkg: String?): Intent {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "image/png"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.putExtra(Intent.EXTRA_TEXT, caption)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) {
                i.setPackage(pkg)
                if (digits.isNotEmpty()) i.putExtra("jid", "$digits@s.whatsapp.net")
            }
            return i
        }

        var launched = false
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                startActivity(build(pkg))
                launched = true
                break
            } catch (e: Exception) {
            }
        }
        if (!launched) startActivity(Intent.createChooser(build(null), "أرسل التقرير"))
    }
}
