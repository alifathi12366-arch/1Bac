package com.example.bac1

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LessonPlayerActivity : AppCompatActivity() {

    private class QuizQ(val text: String, val options: List<String>, val correct: Int, val note: String)

    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private var courseId = ""
    private var lessonId = ""
    private var playerKey = ""
    private var studentName = ""
    private var teacherCode = ""
    private var courseTitle = ""
    private var passPercent = 60
    private var visible: List<CourseUtil.Lesson> = emptyList()
    private var passed: Set<String> = emptySet()
    private var lessonIdx = -1
    private var lesson: CourseUtil.Lesson? = null

    private var resumePos = 0
    @Volatile private var curTime = 0
    @Volatile private var maxReached = 0
    @Volatile private var duration = 0
    private var lastSavedTime = -1
    private var lastSavedMax = -1
    private var notesText = ""
    private var rate = 1f
    private var etNotes: EditText? = null
    private val speedButtons = mutableListOf<Pair<Float, Button>>()

    private var screen = "load"
    private var quiz: List<QuizQ> = emptyList()
    private var qi = 0
    private var picked = -1
    private val answers = mutableListOf<Int>()
    private var scorePct = 0
    private var correctCount = 0
    private var didPass = false
    private var timedOut = false
    private var confettiShown = false
    private var quizTimer: CountDownTimer? = null
    private var quizEndMs = 0L
    private var tvQuizTimer: TextView? = null

    private var video: WebView? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var fullscreen: FrameLayout

    private fun dp(v: Int) = LeagueUi.dp(this, v)

    private val saver = object : Runnable {
        override fun run() {
            if (screen == "player") {
                saveWatch()
                handler.postDelayed(this, 20000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // منع التصوير وتسجيل الشاشة أثناء الفيديو والامتحان
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

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
                        quizTimer?.cancel()
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
        rate = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getFloat("video_speed", 1f)

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
                val all = CourseUtil.parseLessons(c.get("lessons"))
                CourseUtil.myGroups(db, teacherCode, studentName) { mine ->
                    visible = CourseUtil.visible(all, mine)
                    lessonIdx = visible.indexOfFirst { it.id == lessonId }
                    if (lessonIdx < 0) {
                        showMessage("الدرس ده مش متاح لمجموعتك")
                    } else {
                        lesson = visible[lessonIdx]
                        db.collection("courseProgress").document("${courseId}_$playerKey").get()
                            .addOnSuccessListener { p -> onProgress(p) }
                            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
                    }
                }
            }
            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    private fun onProgress(p: DocumentSnapshot) {
        passed = (p.get("passed") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        val w = (p.get("watch") as? Map<*, *>)?.get(lessonId) as? Map<*, *>
        resumePos = (w?.get("pos") as? Number)?.toInt() ?: 0
        maxReached = (w?.get("max") as? Number)?.toInt() ?: 0
        duration = (w?.get("dur") as? Number)?.toInt() ?: 0
        curTime = resumePos
        lastSavedTime = curTime
        lastSavedMax = maxReached
        notesText = (p.get("notes") as? Map<*, *>)?.get(lessonId) as? String ?: ""

        val flags = CourseUtil.unlocked(visible, passed)
        if (!flags[lessonIdx]) {
            showMessage("الدرس ده لسه مقفول 🔒\nخلّص امتحان الدرس اللي قبله الأول.")
        } else {
            screen = "player"
            render()
        }
    }

    // ---------- العرض ----------
    private fun render() {
        if (screen != "player") leavePlayer()
        releaseVideo()
        body.removeAllViews()
        tvQuizTimer = null
        when (screen) {
            "player" -> renderPlayer()
            "quiz" -> renderQuiz()
            "result" -> renderResult()
            else -> body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        }
    }

    private fun leavePlayer() {
        handler.removeCallbacks(saver)
        saveWatch()
        saveNotes()
        etNotes = null
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

    // الجسر بين صفحة الفيديو والتطبيق (الوقت الحالي وطول الفيديو وحالة التشغيل)
    inner class Bridge {
        @JavascriptInterface
        fun onTime(t: Double) {
            val s = t.toInt()
            curTime = s
            if (s > maxReached) maxReached = s
        }

        @JavascriptInterface
        fun onDuration(d: Double) {
            if (d > 0) duration = d.toInt()
        }

        @JavascriptInterface
        fun onState(s: Double) {
            val st = s.toInt()
            runOnUiThread {
                if (st == 1) applyRate()
                if (st == 2 || st == 0) saveWatch()
            }
        }
    }

    private fun playerHtml(videoId: String, start: Int): String {
        val src = "https://www.youtube.com/embed/$videoId?rel=0&playsinline=1&modestbranding=1&enablejsapi=1&start=$start"
        return "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style></head><body>" +
                "<iframe id=\"yt\" src=\"$src\" " +
                "allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen\" " +
                "allowfullscreen referrerpolicy=\"strict-origin-when-cross-origin\"></iframe>" +
                "<script>" +
                "var f=document.getElementById('yt');var got=false;" +
                "function post(o){try{f.contentWindow.postMessage(JSON.stringify(o),'*');}catch(e){}}" +
                "function hello(){if(!got){post({event:'listening',id:1,channel:'widget'});}}" +
                "f.addEventListener('load',hello);setInterval(hello,1000);" +
                "window.addEventListener('message',function(e){" +
                "if(typeof e.data!=='string')return;var d;try{d=JSON.parse(e.data);}catch(x){return;}" +
                "if(d.event==='infoDelivery'&&d.info){got=true;" +
                "if(typeof d.info.currentTime==='number'&&window.Android)Android.onTime(d.info.currentTime);" +
                "if(typeof d.info.duration==='number'&&window.Android)Android.onDuration(d.info.duration);" +
                "if(typeof d.info.playerState==='number'&&window.Android)Android.onState(d.info.playerState);}" +
                "});" +
                "function setRate(r){post({event:'command',func:'setPlaybackRate',args:[r]});}" +
                "</script></body></html>"
    }

    private fun applyRate() {
        video?.evaluateJavascript("setRate($rate)", null)
    }

    private fun refreshSpeed() {
        speedButtons.forEach { (r, b) ->
            val on = r == rate
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (on) LeagueUi.GOLD else "#34428F"))
            b.setTextColor(if (on) Color.BLACK else Color.WHITE)
        }
    }

    private fun baseMap(): HashMap<String, Any> = hashMapOf<String, Any>(
        "courseId" to courseId, "teacherCode" to teacherCode,
        "studentId" to playerKey, "studentName" to studentName,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun saveWatch() {
        val l = lesson ?: return
        if (curTime <= 0 && maxReached <= 0) return
        if (curTime == lastSavedTime && maxReached == lastSavedMax) return
        lastSavedTime = curTime
        lastSavedMax = maxReached
        val pos = if (duration > 0 && curTime >= duration - 5) 0 else curTime
        val m = baseMap()
        m["watch"] = hashMapOf<String, Any>(
            l.id to hashMapOf<String, Any>(
                "pos" to pos.toLong(), "max" to maxReached.toLong(),
                "dur" to duration.toLong(), "at" to System.currentTimeMillis()
            )
        )
        db.collection("courseProgress").document("${courseId}_$playerKey").set(m, SetOptions.merge())
    }

    private fun saveNotes() {
        val l = lesson ?: return
        val txt = etNotes?.text?.toString()?.trim() ?: return
        if (txt == notesText) return
        notesText = txt
        val m = baseMap()
        m["notes"] = hashMapOf<String, Any>(l.id to txt)
        db.collection("courseProgress").document("${courseId}_$playerKey").set(m, SetOptions.merge())
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
        w.addJavascriptInterface(Bridge(), "Android")
        val start = if (resumePos > 5) resumePos - 2 else 0
        w.loadDataWithBaseURL("https://$packageName", playerHtml(l.videoId, start), "text/html", "UTF-8", null)
        val h = (resources.displayMetrics.widthPixels - dp(32)) * 9 / 16
        val vlp = LinearLayout.LayoutParams(-1, h)
        vlp.topMargin = dp(10)
        body.addView(w, vlp)

        handler.removeCallbacks(saver)
        handler.postDelayed(saver, 20000L)

        if (resumePos > 5) {
            body.addView(
                LeagueUi.note(this, "▶ هتكمل من ${CourseUtil.fmtTime(resumePos)} (آخر مكان وقفت عنده)", LeagueUi.CYAN)
            )
        }

        // سرعة الفيديو
        val sl = LeagueUi.tv(this, "⚡ سرعة الفيديو", 14f, LeagueUi.MUTED, true)
        sl.setPadding(0, dp(8), 0, dp(2))
        body.addView(sl)
        val srow = LinearLayout(this)
        srow.orientation = LinearLayout.HORIZONTAL
        speedButtons.clear()
        listOf(1f to "1×", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×").forEach { (r, label) ->
            val b = Button(this)
            b.isAllCaps = false
            b.text = label
            b.textSize = 14f
            val blp = LinearLayout.LayoutParams(0, -2, 1f)
            blp.marginStart = dp(3)
            blp.marginEnd = dp(3)
            b.layoutParams = blp
            b.setOnClickListener {
                rate = r
                getSharedPreferences("bac1_prefs", MODE_PRIVATE).edit().putFloat("video_speed", r).apply()
                applyRate()
                refreshSpeed()
            }
            srow.addView(b)
            speedButtons.add(Pair(r, b))
        }
        body.addView(srow, LinearLayout.LayoutParams(-1, -2))
        refreshSpeed()

        if (l.desc.isNotEmpty()) {
            val box = LeagueUi.info(this, "📝 عن الدرس", l.desc)
            val blp = LinearLayout.LayoutParams(-1, -2)
            blp.topMargin = dp(10)
            box.layoutParams = blp
            body.addView(box)
        }

        // ملاحظات الطالب
        val nbox = LinearLayout(this)
        nbox.orientation = LinearLayout.VERTICAL
        nbox.setPadding(dp(14), dp(12), dp(14), dp(12))
        nbox.background = LeagueUi.bg(this, LeagueUi.CARD, 16)
        val nlp = LinearLayout.LayoutParams(-1, -2)
        nlp.topMargin = dp(12)
        nbox.layoutParams = nlp
        nbox.addView(LeagueUi.tv(this, "🗒️ ملاحظاتي على الدرس", 16f, LeagueUi.GOLD, true))
        val et = EditText(this)
        et.setText(notesText)
        et.hint = "اكتب ملاحظاتك وإنت بتتفرج..."
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Color.parseColor(LeagueUi.MUTED))
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(3000))
        et.minLines = 3
        et.gravity = Gravity.TOP or Gravity.START
        et.background = LeagueUi.bg(this, "#0F1740", 12)
        et.setPadding(dp(10), dp(8), dp(10), dp(8))
        val elp = LinearLayout.LayoutParams(-1, -2)
        elp.topMargin = dp(8)
        nbox.addView(et, elp)
        etNotes = et
        val nrow = LinearLayout(this)
        nrow.orientation = LinearLayout.HORIZONTAL
        val ins = LeagueUi.button(this, "⏱ أدرج الوقت", "#6366F1")
        ins.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        ins.setOnClickListener {
            val pos = et.selectionStart.coerceAtLeast(0)
            et.text.insert(pos, "[${CourseUtil.fmtTime(curTime)}] ")
        }
        val sv = LeagueUi.button(this, "💾 احفظ", LeagueUi.GREEN)
        val slp = LinearLayout.LayoutParams(0, -2, 1f)
        slp.marginStart = dp(8)
        slp.topMargin = dp(12)
        sv.layoutParams = slp
        sv.setOnClickListener {
            saveNotes()
            Toast.makeText(this, "تم حفظ ملاحظاتك ✅", Toast.LENGTH_SHORT).show()
        }
        nrow.addView(ins)
        nrow.addView(sv)
        nbox.addView(nrow)
        body.addView(nbox)

        // الامتحان (اختياري)
        val isPassed = l.id in passed
        val next = visible.getOrNull(lessonIdx + 1)
        if (l.qs.isEmpty()) {
            body.addView(LeagueUi.note(this, "ده درس من غير امتحان ✅", LeagueUi.GREEN))
            if (next != null) {
                val b = LeagueUi.button(this, "الدرس التالي ▶", LeagueUi.GREEN)
                b.setOnClickListener { openNext(next) }
                body.addView(b)
            }
        } else {
            if (isPassed) body.addView(LeagueUi.note(this, "✅ نجحت في امتحان الدرس ده قبل كده", LeagueUi.GREEN))
            val info = "📝 امتحان الدرس: ${l.qs.size} سؤال" +
                    (if (l.quizMinutes > 0) " · ⏱ ${l.quizMinutes} دقيقة" else "") +
                    " · النجاح من $passPercent%"
            body.addView(LeagueUi.note(this, info, LeagueUi.MUTED))
            val b = LeagueUi.button(
                this, if (isPassed) "📝 أعد الامتحان (اختياري)" else "📝 ابدأ امتحان الدرس", LeagueUi.GREEN
            )
            b.setOnClickListener { startQuiz() }
            body.addView(b)
        }
        val back = LeagueUi.button(this, "↩ رجوع للدروس", "#475569")
        back.setOnClickListener { finish() }
        body.addView(back)
    }

    private fun openNext(next: CourseUtil.Lesson) {
        leavePlayer()
        startActivity(
            Intent(this, LessonPlayerActivity::class.java)
                .putExtra("courseId", courseId)
                .putExtra("lessonId", next.id)
        )
        finish()
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
        timedOut = false
        confettiShown = false

        quizTimer?.cancel()
        if (l.quizMinutes > 0) {
            val ms = l.quizMinutes * 60_000L
            quizEndMs = System.currentTimeMillis() + ms
            quizTimer = object : CountDownTimer(ms, 1000L) {
                override fun onTick(left: Long) {
                    tvQuizTimer?.text = "⏱ " + CourseUtil.fmtTime((left / 1000).toInt())
                }

                override fun onFinish() {
                    if (screen == "quiz") finishQuiz(true)
                }
            }.start()
        } else {
            quizEndMs = 0L
        }
        screen = "quiz"
        render()
        scrollTop()
    }

    private fun renderQuiz() {
        val q = quiz.getOrNull(qi) ?: return
        body.addView(LeagueUi.tv(this, "📝 امتحان: ${lesson?.title ?: ""}", 15f, LeagueUi.MUTED, true))
        if (quizEndMs > 0) {
            val left = ((quizEndMs - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            val t = LeagueUi.tv(this, "⏱ " + CourseUtil.fmtTime(left), 30f, LeagueUi.GOLD, true)
            t.gravity = Gravity.CENTER
            tvQuizTimer = t
            body.addView(t)
        }
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
                    finishQuiz(false)
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
            quizTimer?.cancel()
            screen = "player"
            render()
        }
        body.addView(back)
    }

    private fun finishQuiz(timeUp: Boolean) {
        if (screen != "quiz") return
        quizTimer?.cancel()
        if (picked != -1 && answers.size == qi) answers.add(picked)
        picked = -1
        timedOut = timeUp
        var correct = 0
        quiz.forEachIndexed { i, q -> if (answers.getOrNull(i) == q.correct) correct++ }
        correctCount = correct
        scorePct = if (quiz.isEmpty()) 100 else correct * 100 / quiz.size
        didPass = scorePct >= passPercent
        if (didPass) saveProgress()
        screen = "result"
        render()
        scrollTop()
    }

    private fun saveProgress() {
        val l = lesson ?: return
        if (l.id in passed) return
        passed = passed + l.id
        val m = baseMap()
        m["passed"] = FieldValue.arrayUnion(l.id)
        m["scores"] = hashMapOf<String, Any>(l.id to scorePct.toLong())
        db.collection("courseProgress").document("${courseId}_$playerKey").set(m, SetOptions.merge())
            .addOnFailureListener {
                Toast.makeText(this, "تعذّر حفظ تقدمك، تحقق من الإنترنت وأعد الامتحان", Toast.LENGTH_LONG).show()
            }
    }

    private fun renderResult() {
        body.addView(LeagueUi.tv(this, header(), 15f, LeagueUi.MUTED, true))
        val head = LeagueUi.tv(
            this,
            (if (didPass) "🎉 نجحت!" else "❌ لسه محتاج مراجعة") + (if (timedOut) "  ⏱ انتهى الوقت" else ""),
            26f, if (didPass) LeagueUi.GREEN else LeagueUi.RED, true
        )
        head.gravity = Gravity.CENTER
        head.setPadding(0, dp(10), 0, dp(4))
        body.addView(head)

        val sc = LeagueUi.tv(this, "$scorePct%", 40f, LeagueUi.GOLD, true)
        sc.gravity = Gravity.CENTER
        body.addView(sc)
        body.addView(LeagueUi.note(this, "$correctCount إجابة صحيحة من ${quiz.size} · المطلوب $passPercent%", LeagueUi.CYAN))

        if (didPass) {
            val next = visible.getOrNull(lessonIdx + 1)
            if (next != null) {
                val b = LeagueUi.button(this, "الدرس التالي ▶", LeagueUi.GREEN)
                b.setOnClickListener { openNext(next) }
                body.addView(b)
            } else {
                body.addView(LeagueUi.note(this, "🎓 خلّصت كل دروس الكورس!", LeagueUi.GOLD))
            }
            val back = LeagueUi.button(this, "↩ رجوع للدروس", "#475569")
            back.setOnClickListener { finish() }
            body.addView(back)
        } else {
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
        }

        // مراجعة كل سؤال: إجابتك صح ولا غلط + الإجابة الصحيحة
        val rh = LeagueUi.tv(this, "📋 مراجعة إجاباتك", 20f, LeagueUi.GOLD, true)
        rh.setPadding(0, dp(18), 0, dp(4))
        body.addView(rh)
        quiz.forEachIndexed { i, q ->
            val a = answers.getOrNull(i)
            val (status, color) = when {
                a == null || a < 0 -> Pair("⏱ لم تجب", LeagueUi.MUTED)
                a == q.correct -> Pair("✅ إجابتك صحيحة", LeagueUi.GREEN)
                else -> Pair("❌ إجابتك غلط", LeagueUi.RED)
            }
            body.addView(LeagueUi.tv(this, "السؤال ${i + 1}  ·  $status", 14f, color, true))
            body.addView(LeagueUi.questionBox(this, q.text))
            q.options.forEachIndexed { k, opt ->
                val st = when {
                    k == q.correct -> 2
                    k == a -> 3
                    else -> 0
                }
                body.addView(LeagueUi.optionCard(this, opt, st))
            }
            if (q.note.isNotEmpty()) body.addView(LeagueUi.note(this, "💡 ${q.note}", LeagueUi.GOLD))
        }

        if (scorePct == 100 && !confettiShown) {
            confettiShown = true
            findViewById<ViewGroup>(android.R.id.content).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1)
            )
        }
    }

    override fun onPause() {
        saveWatch()
        saveNotes()
        video?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        video?.onResume()
        if (screen == "quiz" && quizEndMs > 0 && System.currentTimeMillis() >= quizEndMs) finishQuiz(true)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        quizTimer?.cancel()
        saveWatch()
        saveNotes()
        releaseVideo()
        super.onDestroy()
    }
}
