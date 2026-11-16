package com.example.bac1

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LessonPlayerActivity : AppCompatActivity() {

    private class QuizQ(val text: String, val options: List<String>, val correct: Int, val note: String)

    private val db = FirebaseFirestore.getInstance()

    private var courseId = ""
    private var lessonId = ""
    private var playerKey = ""
    private var studentName = ""
    private var teacherCode = ""
    private var courseTitle = ""
    private var passPercent = 60
    private var lessons: List<CourseUtil.Lesson> = emptyList()
    private var passed: Set<String> = emptySet()
    private var lessonIdx = -1
    private var lesson: CourseUtil.Lesson? = null

    private var screen = "load"
    private var quiz: List<QuizQ> = emptyList()
    private var qi = 0
    private var picked = -1
    private val answers = mutableListOf<Int>()
    private var scorePct = 0
    private var correctCount = 0
    private var didPass = false
    private var wrongList: List<Int> = emptyList()
    private var confettiShown = false

    private var video: WebView? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var fullscreen: FrameLayout

    private fun dp(v: Int) = LeagueUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.layoutDirection = View.LAYOUT_DIRECTION_RTL
        main.setBackgroundColor(Color.parseColor(LeagueUi.BG))
        scroll = ScrollView(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(12), dp(16), dp(24))
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        main.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        root.addView(main, FrameLayout.LayoutParams(-1, -1))

        fullscreen = FrameLayout(this)
        fullscreen.setBackgroundColor(Color.BLACK)
        fullscreen.visibility = View.GONE
        root.addView(fullscreen, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    screen == "quiz" -> {
                        screen = "player"
                        render()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        val st = CourseUtil.student(this)
        playerKey = st.first
        studentName = st.second
        courseId = intent.getStringExtra("courseId") ?: ""
        lessonId = intent.getStringExtra("lessonId") ?: ""

        showMessage("جارٍ التحميل...")
        load()
    }

    private fun header(): String = "🎬 $courseTitle"

    private fun showMessage(msg: String) {
        releaseVideo()
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🎬 الدرس", 24f, LeagueUi.GOLD, true))
        body.addView(LeagueUi.note(this, msg))
    }

    private fun scrollTop() {
        scroll.post { scroll.scrollTo(0, 0) }
    }

    // ---------- التحميل ----------
    private fun load() {
        db.collection("courses").document(courseId).get()
            .addOnSuccessListener { c ->
                if (!c.exists()) {
                    showMessage("الكورس ده مش موجود")
                    return@addOnSuccessListener
                }
                courseTitle = c.getString("title") ?: ""
                teacherCode = c.getString("teacherCode") ?: ""
                passPercent = (c.getLong("passPercent") ?: 60L).toInt()
                lessons = CourseUtil.parseLessons(c.get("lessons"))
                lessonIdx = lessons.indexOfFirst { it.id == lessonId }
                if (lessonIdx < 0) {
                    showMessage("الدرس ده مش موجود")
                    return@addOnSuccessListener
                }
                lesson = lessons[lessonIdx]
                db.collection("courseProgress").document("${courseId}_$playerKey").get()
                    .addOnSuccessListener { p ->
                        passed = (p.get("passed") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                        val unlocked = lessonIdx == 0 || lessons[lessonIdx - 1].id in passed ||
                                lessons[lessonIdx].id in passed
                        if (!unlocked) {
                            showMessage("الدرس ده لسه مقفول 🔒\nخلّص امتحان الدرس اللي قبله الأول.")
                        } else {
                            screen = "player"
                            render()
                        }
                    }
                    .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
            }
            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    // ---------- العرض ----------
    private fun render() {
        releaseVideo()
        body.removeAllViews()
        when (screen) {
            "player" -> renderPlayer()
            "quiz" -> renderQuiz()
            "result" -> renderResult()
            else -> body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
            if (view == null) return
            if (customView != null) {
                callback?.onCustomViewHidden()
                return
            }
            customView = view
            customCb = callback
            fullscreen.addView(view, FrameLayout.LayoutParams(-1, -1))
            fullscreen.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        override fun onHideCustomView() {
            exitFullscreen()
        }
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        fullscreen.removeView(v)
        fullscreen.visibility = View.GONE
        customCb?.onCustomViewHidden()
        customView = null
        customCb = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun releaseVideo() {
        exitFullscreen()
        val w = video ?: return
        video = null
        (w.parent as? ViewGroup)?.removeView(w)
        w.loadUrl("about:blank")
        w.destroy()
    }

    private fun renderPlayer() {
        val l = lesson ?: return
        body.addView(LeagueUi.tv(this, header(), 15f, LeagueUi.MUTED, true))
        body.addView(LeagueUi.tv(this, "${lessonIdx + 1}. ${l.title}", 22f, LeagueUi.GOLD, true))

        val w = WebView(this)
        video = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.setBackgroundColor(Color.BLACK)
        w.webChromeClient = chrome
        w.webViewClient = WebViewClient()
        // لازم نبعت Referer عشان يوتيوب يقبل التشغيل (غير كده بيطلع خطأ 153)
        val html = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style></head>" +
                "<body><iframe src=\"https://www.youtube.com/embed/${l.videoId}?rel=0&playsinline=1&modestbranding=1\" " +
                "allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen\" " +
                "allowfullscreen referrerpolicy=\"strict-origin-when-cross-origin\"></iframe></body></html>"
        w.loadDataWithBaseURL("https://$packageName", html, "text/html", "UTF-8", null)
        val h = (resources.displayMetrics.widthPixels - dp(32)) * 9 / 16
        val lp = LinearLayout.LayoutParams(-1, h)
        lp.topMargin = dp(10)
        body.addView(w, lp)

        if (l.desc.isNotEmpty()) {
            val box = LeagueUi.info(this, "📝 عن الدرس", l.desc)
            val blp = LinearLayout.LayoutParams(-1, -2)
            blp.topMargin = dp(10)
            box.layoutParams = blp
            body.addView(box)
        }

        val isPassed = l.id in passed
        if (isPassed) {
            body.addView(LeagueUi.note(this, "✅ نجحت في امتحان الدرس ده قبل كده", LeagueUi.GREEN))
        } else {
            body.addView(
                LeagueUi.note(this, "شوف الفيديو، وبعدها امتحان الدرس. لازم تنجح ($passPercent%) عشان يفتح الدرس اللي بعده.", LeagueUi.MUTED)
            )
        }

        if (l.qs.isEmpty()) {
            if (!isPassed) {
                val b = LeagueUi.button(this, "✅ أنهيت الدرس", LeagueUi.GREEN)
                b.setOnClickListener {
                    quiz = emptyList()
                    correctCount = 0
                    scorePct = 100
                    didPass = true
                    wrongList = emptyList()
                    saveProgress()
                    screen = "result"
                    render()
                    scrollTop()
                }
                body.addView(b)
            }
        } else {
            val b = LeagueUi.button(
                this, if (isPassed) "📝 أعد الامتحان (اختياري)" else "📝 ابدأ امتحان الدرس", LeagueUi.GREEN
            )
            b.setOnClickListener { startQuiz() }
            body.addView(b)
        }
    }

    // ---------- الامتحان ----------
    private fun startQuiz() {
        val l = lesson ?: return
        quiz = l.qs.shuffled().map { q ->
            val correctText = q.options[q.correct]
            val sh = q.options.shuffled()
            QuizQ(q.text, sh, sh.indexOf(correctText), q.note)
        }
        qi = 0
        picked = -1
        answers.clear()
        confettiShown = false
        screen = "quiz"
        render()
        scrollTop()
    }

    private fun renderQuiz() {
        val q = quiz.getOrNull(qi) ?: return
        body.addView(LeagueUi.tv(this, "📝 امتحان: ${lesson?.title ?: ""}", 15f, LeagueUi.MUTED, true))
        body.addView(LeagueUi.tv(this, "السؤال ${qi + 1} من ${quiz.size}", 20f, LeagueUi.GOLD, true))
        body.addView(LeagueUi.questionBox(this, q.text))
        q.options.forEachIndexed { i, opt ->
            val c = LeagueUi.optionCard(this, opt, if (picked == i) 1 else 0)
            c.setOnClickListener {
                picked = i
                render()
            }
            body.addView(c)
        }
        if (picked != -1) {
            val last = qi == quiz.size - 1
            val b = LeagueUi.button(this, if (last) "🏁 إنهاء الامتحان" else "التالي ◀", LeagueUi.GREEN)
            b.setOnClickListener {
                answers.add(picked)
                picked = -1
                if (last) {
                    finishQuiz()
                } else {
                    qi++
                    render()
                    scrollTop()
                }
            }
            body.addView(b)
        }
        val back = LeagueUi.button(this, "↩ رجوع للفيديو", "#475569")
        back.setOnClickListener {
            screen = "player"
            render()
        }
        body.addView(back)
    }

    private fun finishQuiz() {
        var correct = 0
        val wrong = mutableListOf<Int>()
        quiz.forEachIndexed { i, q ->
            if (answers.getOrNull(i) == q.correct) correct++ else wrong.add(i)
        }
        correctCount = correct
        scorePct = if (quiz.isEmpty()) 100 else correct * 100 / quiz.size
        didPass = scorePct >= passPercent
        wrongList = wrong
        if (didPass) saveProgress()
        screen = "result"
        render()
        scrollTop()
    }

    private fun saveProgress() {
        val l = lesson ?: return
        if (l.id in passed) return
        passed = passed + l.id
        db.collection("courseProgress").document("${courseId}_$playerKey").set(
            hashMapOf<String, Any>(
                "courseId" to courseId, "teacherCode" to teacherCode,
                "studentId" to playerKey, "studentName" to studentName,
                "passed" to FieldValue.arrayUnion(l.id),
                "scores" to hashMapOf<String, Any>(l.id to scorePct.toLong()),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).addOnFailureListener {
            Toast.makeText(this, "تعذّر حفظ تقدمك، تحقق من الإنترنت وأعد الامتحان", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderResult() {
        body.addView(LeagueUi.tv(this, header(), 15f, LeagueUi.MUTED, true))
        val head = LeagueUi.tv(
            this, if (didPass) "🎉 نجحت!" else "❌ لسه محتاج مراجعة", 28f,
            if (didPass) LeagueUi.GREEN else LeagueUi.RED, true
        )
        head.gravity = android.view.Gravity.CENTER
        head.setPadding(0, dp(10), 0, dp(4))
        body.addView(head)

        if (quiz.isNotEmpty()) {
            val sc = LeagueUi.tv(this, "$scorePct%  ($correctCount من ${quiz.size})", 34f, LeagueUi.GOLD, true)
            sc.gravity = android.view.Gravity.CENTER
            body.addView(sc)
            body.addView(LeagueUi.note(this, "المطلوب للنجاح: $passPercent%", LeagueUi.CYAN))
        } else {
            body.addView(LeagueUi.note(this, "✅ أكملت الدرس", LeagueUi.GREEN))
        }

        if (!didPass) {
            body.addView(LeagueUi.tv(this, "الأسئلة اللي غلطت فيها (راجع الفيديو):", 16f, LeagueUi.TEXT, true))
            wrongList.forEach {
                val t = LeagueUi.tv(this, "• ${quiz[it].text}", 15f, LeagueUi.MUTED)
                t.setPadding(0, dp(6), 0, 0)
                body.addView(t)
            }
            val retry = LeagueUi.button(this, "🔁 أعد المحاولة", LeagueUi.GREEN)
            retry.setOnClickListener { startQuiz() }
            body.addView(retry)
            val rev = LeagueUi.button(this, "📺 أراجع الفيديو", "#6366F1")
            rev.setOnClickListener {
                screen = "player"
                render()
                scrollTop()
            }
            body.addView(rev)
            return
        }

        // نجح: نعرض الإجابات الصحيحة
        quiz.forEachIndexed { i, q ->
            body.addView(LeagueUi.questionBox(this, "${i + 1}. ${q.text}"))
            q.options.forEachIndexed { k, opt ->
                val st = when {
                    k == q.correct -> 2
                    k == answers.getOrNull(i) -> 3
                    else -> 0
                }
                body.addView(LeagueUi.optionCard(this, opt, st))
            }
            if (q.note.isNotEmpty()) body.addView(LeagueUi.note(this, "💡 ${q.note}", LeagueUi.GOLD))
        }

        val next = lessons.getOrNull(lessonIdx + 1)
        if (next != null) {
            val b = LeagueUi.button(this, "الدرس التالي ▶", LeagueUi.GREEN)
            b.setOnClickListener {
                startActivity(
                    Intent(this, LessonPlayerActivity::class.java)
                        .putExtra("courseId", courseId)
                        .putExtra("lessonId", next.id)
                )
                finish()
            }
            body.addView(b)
        } else {
            body.addView(LeagueUi.note(this, "🎓 خلّصت كل دروس الكورس!", LeagueUi.GOLD))
        }
        val back = LeagueUi.button(this, "↩ رجوع للدروس", "#475569")
        back.setOnClickListener { finish() }
        body.addView(back)

        if (scorePct == 100 && !confettiShown) {
            confettiShown = true
            findViewById<ViewGroup>(android.R.id.content).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1)
            )
        }
    }

    override fun onPause() {
        video?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        video?.onResume()
    }

    override fun onDestroy() {
        releaseVideo()
        super.onDestroy()
    }
}
