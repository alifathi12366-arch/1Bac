package com.example.bac1

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private var uiReady = false
    private var challengeDoneToday = false

    private lateinit var sheet: Bitmap
    private lateinit var gridContainer: LinearLayout
    private lateinit var tvDailySub: TextView
    private lateinit var tvMyRank: TextView
        private lateinit var tvJourneySub: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun spr(x: Int, y: Int, w: Int, h: Int): Bitmap = Bitmap.createBitmap(sheet, x, y, w, h)

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
    }

    private fun openWithAd(target: Class<*>) {
        val i = Intent(this, InterstitialActivity::class.java)
        i.putExtra("TARGET_CLASS", target.name)
        startActivity(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("is_registered", false)) {
            startActivity(Intent(this, RegisterChoiceActivity::class.java))
            finish()
            return
        }
        if (prefs.getBoolean("is_teacher", false)) {
            startActivity(Intent(this, TeacherDashboardActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        uiReady = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if ((prefs.getString("student_id", "") ?: "").isEmpty()) {
            prefs.edit().putString("student_id", (1000..9999).random().toString()).apply()
        }

        checkForUpdate()
        loadAd()
        checkDailyLogin()
        checkTeacherStatus()

        val opts = BitmapFactory.Options()
        opts.inScaled = false
        sheet = BitmapFactory.decodeResource(resources, R.drawable.home_sprites, opts)

        setupHeader()
        gridContainer = findViewById(R.id.gridContainer)
        buildGrid()
        setupNav()
    }

    // ---------- الهيدر ----------
    private fun setupHeader() {
        findViewById<ImageView>(R.id.ivLogo).setImageBitmap(spr(299, 325, 270, 193))
        findViewById<ImageView>(R.id.ivBanner).setImageBitmap(spr(0, 0, 810, 321))

        val avatar = findViewById<ImageView>(R.id.ivAvatar)
        avatar.setImageBitmap(spr(583, 551, 139, 139))
        avatar.setOnClickListener { open(AccountActivity::class.java) }
    }

    // ---------- الكروت ----------
    private fun newRow(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.isBaselineAligned = false
        gridContainer.addView(r, LinearLayout.LayoutParams(-1, -2))
        return r
    }

    private fun buildGrid() {
        // الصف 1
        var row = newRow()
        tvMyRank = addCard(
            row, "المسابقة الشهرية", "جاري تحميل النقاط...",
            spr(573, 325, 156, 165), null, "#FCE9C8", "#FDDA96", "#F08A24"
        ) { openWithAd(LeaderboardActivity::class.java) }
        addCard(
            row, "كل الكتب و المواد", "كل ما تحتاجه في مكان واحد",
            spr(276, 551, 147, 145), null, "#E7E2FE", "#D6CEFB", "#4B3FD1"
        ) { open(SubjectsActivity::class.java) }

        // الصف 2
        row = newRow()
        tvDailySub = addCard(
            row, "تحدي اليوم", "اختبر نفسك الآن",
             spr(124, 551, 148, 157), null, "#FFD9EE", "#FDC4E6", "#E5365A"
        ) {
            if (challengeDoneToday) open(DailyChallengeActivity::class.java)
            else openWithAd(DailyChallengeActivity::class.java)
        }
        addCard(
            row, "أدوات المذاكرة", "ذاكر بذكاء وتنظم وقتك",
            spr(427, 551, 152, 144), null, "#D2F6DE", "#B9EFCD", "#2F7BD8"
        ) { openWithAd(StudyToolsActivity::class.java) }

        // الصف 3
        row = newRow()
        addCard(
            row, "تحدي الأصحاب", "تنافس مع أصدقائك",
            spr(308, 715, 151, 125), null, "#E3D8FE", "#D0BCFC", "#6A3CD6"
        ) { open(ChallengeHomeActivity::class.java) }
        addCard(
            row, "مستواي الدراسي", "تابع تقدمك في كل مادة",
            spr(0, 715, 136, 131), null, "#D3E8FF", "#BEDDFD", "#2F6BEA"
        ) { open(ReportActivity::class.java) }

        // الصف 4
        row = newRow()
        addCard(
            row, "تكريماتي", "إنجازاتك تستحق التقدير",
            spr(0, 551, 120, 160), null, "#CDF2E5", "#BDEBE3", "#1FAE7C"
        ) { open(MyHonorsActivity::class.java) }
        addCard(
            row, "اقتراح او شكوي", "رأيك يهمنا",
            spr(140, 715, 164, 128), null, "#FDE8DC", "#FDD5BD", "#E5413A"
        ) { open(SuggestionActivity::class.java) }

        // الصف 5
        row = newRow()
        addCard(
            row, "التقييمات الأسبوعية", "",
            spr(0, 325, 295, 222), null, "#7A33E0", "#4F1AA0", "#5A2BC8",
            dark = true, size = 64
        ) { open(WeeklyUpdatesActivity::class.java) }
        addCard(
            row, "المدرس الذكي", "اسأل في أي مادة لأولى ثانوي",
          null, "🤖", "#FFF3E0", "#FFE0B2", "#F57C00"
        ) { open(AiTutorActivity::class.java) }

        // الصف 6: رحلة التفوق
        row = newRow()
        tvJourneySub = addCard(
            row, "رحلة التفوق 🚀", "جاري تحميل لقبك...",
            null, "🏔️", "#7A33E0", "#4F1AA0", "#5A2BC8",
            dark = true
        ) { open(JourneyActivity::class.java) } 
    }

    // الكارت عمودي: الاستيكر والسهم فوق، والعنوان والوصف تحتهم بعرض الكارت كله،
    // والكارت بيكبر مع النص، فمفيش كلمة بتتقطع حتى لو حجم الخط في الموبايل كبير
    private fun addCard(
        row: LinearLayout, title: String, sub: String,
        sticker: Bitmap?, emoji: String?,
        c1: String, c2: String, accent: String,
        dark: Boolean = false, badge: Boolean = false, size: Int = 52,
        onClick: () -> Unit
    ): TextView {
        val card = FrameLayout(this)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        lp.setMargins(dp(6), dp(6), dp(6), dp(6))
        card.layoutParams = lp
        card.minimumHeight = dp(140)

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
        )
        bg.cornerRadius = dp(22).toFloat()
        bg.setStroke(dp(1), Color.parseColor(if (dark) "#55FFFFFF" else "#B3FFFFFF"))
        card.background = bg
        card.clipToOutline = true
        card.elevation = dp(3).toFloat()
        card.isClickable = true
        card.setOnClickListener { onClick() }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(14), dp(14), dp(14))

        // الصف العلوي: السهم + الاستيكر
        val top = FrameLayout(this)
        col.addView(top, LinearLayout.LayoutParams(-1, dp(size)))

        val arrow = TextView(this)
        arrow.text = "›"
        arrow.textDirection = View.TEXT_DIRECTION_LTR
        arrow.gravity = Gravity.CENTER
        arrow.textSize = 20f
        arrow.includeFontPadding = false
        arrow.setTypeface(null, Typeface.BOLD)
        arrow.setTextColor(Color.parseColor(accent))
        val abg = GradientDrawable()
        abg.shape = GradientDrawable.OVAL
        abg.setColor(Color.WHITE)
        arrow.background = abg
        top.addView(arrow, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.START or Gravity.CENTER_VERTICAL))

        if (sticker != null) {
            val iv = ImageView(this)
            iv.setImageBitmap(sticker)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            top.addView(iv, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.END))
        } else if (emoji != null) {
            val e = TextView(this)
            e.text = emoji
            e.textSize = 34f
            e.gravity = Gravity.CENTER
            top.addView(e, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.END))
        }

        // العنوان
        val titleTv = TextView(this)
        titleTv.text = title
        titleTv.textSize = 14.5f
        titleTv.setTypeface(null, Typeface.BOLD)
        titleTv.setTextColor(Color.parseColor(if (dark) "#FFFFFF" else "#241B4E"))
        titleTv.gravity = Gravity.START
        val tlp = LinearLayout.LayoutParams(-1, -2)
        tlp.topMargin = dp(8)
        col.addView(titleTv, tlp)

        // الوصف
        val subTv = TextView(this)
        subTv.text = sub
        subTv.textSize = 10.5f
        subTv.setTextColor(Color.parseColor(if (dark) "#D8CCFF" else "#5E5A7E"))
        subTv.gravity = Gravity.START
        subTv.setPadding(0, dp(3), 0, 0)
        if (sub.isEmpty()) subTv.visibility = View.GONE
        col.addView(subTv, LinearLayout.LayoutParams(-1, -2))

        card.addView(col, FrameLayout.LayoutParams(-1, -2))

        // شارة "جديد"
        if (badge) {
            val b = TextView(this)
            b.text = "🔥 جديد"
            b.textSize = 10f
            b.setTypeface(null, Typeface.BOLD)
            b.setTextColor(Color.WHITE)
            b.setPadding(dp(10), dp(3), dp(10), dp(3))
            val bb = GradientDrawable()
            bb.setColor(Color.parseColor("#E53935"))
            val r = dp(14).toFloat()
            bb.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, r, r)
            b.background = bb
            card.addView(b, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP))
        }

        row.addView(card)
        return subTv
    }

    // ---------- الشريط السفلي ----------
    private fun setupNav() {
        val bar = findViewById<LinearLayout>(R.id.navBar)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F1EEFF"))
        bg.cornerRadius = dp(26).toFloat()
        bg.setStroke(dp(1), Color.parseColor("#DDD7FA"))
        bar.background = bg
        bar.elevation = dp(6).toFloat()

        val line = findViewById<View>(R.id.navHomeLine)
        val lbg = GradientDrawable()
        lbg.setColor(Color.parseColor("#5B2BC9"))
        lbg.cornerRadius = dp(2).toFloat()
        line.background = lbg

        findViewById<ImageView>(R.id.ivNavHome).setImageBitmap(spr(561, 715, 57, 52))
        findViewById<ImageView>(R.id.ivNavTeachers).setImageBitmap(spr(622, 715, 58, 49))
        findViewById<ImageView>(R.id.ivNavSchedule).setImageBitmap(spr(684, 715, 43, 47))
        findViewById<ImageView>(R.id.ivNavAccount).setImageBitmap(spr(731, 715, 37, 46))

        findViewById<View>(R.id.navTeachers).setOnClickListener { openWithAd(TeachersListActivity::class.java) }
        findViewById<View>(R.id.navSchedule).setOnClickListener { open(ScheduleActivity::class.java) }
        findViewById<View>(R.id.navAccount).setOnClickListener { open(AccountActivity::class.java) }
    }

    private fun checkDailyLogin() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val lastLoginDate = prefs.getString("last_login_date", "")

        if (lastLoginDate != today) {
            val month = PointsManager.addPoints(prefs, 1)
            prefs.edit().putString("last_login_date", today).apply()

            val id = prefs.getString("student_id", "") ?: ""
            val name = prefs.getString("student_name", "") ?: ""
            if (id.isNotEmpty() && name.isNotEmpty()) {
                LeaderboardManager.submitScore(id, name, month)
            }
        }
    }

    private fun checkForUpdate() {
        val queue = com.android.volley.toolbox.Volley.newRequestQueue(this)
        val url = "https://raw.githubusercontent.com/alifathi12366-arch/1Bac/main/version.json"

        val request = com.android.volley.toolbox.StringRequest(
            com.android.volley.Request.Method.GET, url,
            { response ->
                try {
                    val json = org.json.JSONObject(response)
                    val latestVersion = json.getInt("latestVersion")
                    val downloadUrl = json.getString("downloadUrl")
                    val currentVersion = packageManager.getPackageInfo(packageName, 0).let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            it.longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION")
                            it.versionCode
                        }
                    }

                    if (latestVersion > currentVersion) {
                        val builder = android.app.AlertDialog.Builder(this)
                        builder.setTitle("تحديث جديد متاح")
                        builder.setMessage("فيه نسخة جديدة من التطبيق، حمّلها دلوقتي")
                        builder.setPositiveButton("تحميل") { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
                            startActivity(intent)
                        }
                        builder.setNegativeButton("لاحقًا", null)
                        builder.show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error ->
                error.printStackTrace()
            }
        )
        queue.add(request)
    }

    private fun loadAd() {
        val webView = findViewById<WebView>(R.id.adWebView) ?: return

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        webView.clearCache(true)
        webView.loadUrl("https://alifathi12366-arch.github.io/1Bac/ad.html")
    }

    private fun checkTeacherStatus() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val isTeacher = prefs.getBoolean("is_teacher", false)
        if (!isTeacher) return

        val code = prefs.getString("teacher_code", "") ?: ""
        if (code.isEmpty()) return

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("teacherCodes")
            .document(code)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val expiresAt = doc.getLong("expiresAt") ?: 0L
                val now = System.currentTimeMillis()
                val daysLeft = (expiresAt - now) / (1000 * 60 * 60 * 24)

                prefs.edit()
                    .putLong("teacher_expires_at", expiresAt)
                    .putBoolean("teacher_expired", now > expiresAt)
                    .putBoolean("teacher_expiring_soon", daysLeft in 0..5)
                    .apply()
            }
    }

    override fun onResume() {
        super.onResume()
        if (uiReady) {
            refreshHomeCards()
            showTeacherBanners()
        }
    }

    private fun showTeacherBanners() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val codes = prefs.getStringSet("joined_teacher_codes", emptySet()) ?: emptySet()
        if (codes.isEmpty()) return
        val parent = findViewById<ViewGroup>(R.id.bannerContainer)

        for (code in codes) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("teacherActivity").document(code).get()
                .addOnSuccessListener { d ->
                    if (!d.exists()) return@addOnSuccessListener
                    val at = d.getLong("updatedAt") ?: 0L
                    val seen = prefs.getLong("seen_act_$code", 0L)
                    val tag = "actBanner_$code"
                    if (at <= seen || parent.findViewWithTag<View>(tag) != null) return@addOnSuccessListener

                    val t = TextView(this)
                    t.tag = tag
                    t.text = "🔔 أ. ${d.getString("teacherName") ?: ""} ${d.getString("text") ?: ""}\n(اضغط للإخفاء)"
                    t.textSize = 15f
                    t.setTextColor(0xFF4E342E.toInt())
                    t.setBackgroundColor(0xFFFFE082.toInt())
                    val pad = (14 * resources.displayMetrics.density).toInt()
                    t.setPadding(pad, pad, pad, pad)
                    val lp = ViewGroup.MarginLayoutParams(-1, -2)
                    lp.topMargin = (8 * resources.displayMetrics.density).toInt()
                    t.setOnClickListener {
                        prefs.edit().putLong("seen_act_$code", at).apply()
                        parent.removeView(t)
                    }
                    parent.addView(t, 0, lp)
                }
        }
    }

    private fun refreshHomeCards() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val id = prefs.all["student_id"]?.toString().orEmpty()
        val key = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("Africa/Cairo")
        val today = fmt.format(java.util.Date())

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("dailyAttempts").document("${key}_$today").get()
            .addOnSuccessListener { a ->
                if (a.exists()) {
                    challengeDoneToday = true
                    val s = a.getLong("score")?.toInt() ?: 0
                    val t = a.getLong("total")?.toInt() ?: 5
                    tvDailySub.text = "أنهيته ✅ نتيجتك $s من $t"
                } else {
                    challengeDoneToday = false
                    db.collection("dailyChallenges").document(today).get()
                        .addOnSuccessListener { c ->
                            val n = (c.get("questions") as? List<*>)?.size ?: 0
                            tvDailySub.text = if (n > 0) "اختبر نفسك الآن ($n أسئلة)"
                            else "لا يوجد تحدٍّ لليوم بعد"
                        }
                }
            }
                    db.collection("dailyAttempts").whereEqualTo("studentKey", key)
            .aggregate(com.google.firebase.firestore.AggregateField.sum("score"))
            .get(com.google.firebase.firestore.AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                val total = (snap.get(com.google.firebase.firestore.AggregateField.sum("score")) as? Number)
                    ?.toLong() ?: 0L
                tvJourneySub.text = Journey.summary(total)
            }

        // النقاط والمركز تحت بعض
        LeaderboardManager.getHomeSummary(key) { _, rank, pts ->
            tvMyRank.text = if (rank > 0) {
                "🌟 النقاط: $pts\n🏆 المركز: #$rank"
            } else {
                "🌟 النقاط: $pts\n🏆 المركز: لم يُحدَّد بعد"
            }
        }
    }
}
