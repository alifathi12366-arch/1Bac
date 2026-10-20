package com.example.bac1

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var uiReady = false
    private var challengeDoneToday = false

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

        if ((prefs.getString("student_id", "") ?: "").isEmpty()) {
            prefs.edit().putString("student_id", (1000..9999).random().toString()).apply()
        }

        checkForUpdate()
        loadAd()
        checkDailyLogin()
        checkTeacherStatus()

        // شريط التنقل السفلي
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_schedule -> {
                    startActivity(Intent(this, ScheduleActivity::class.java))
                    true
                }
                R.id.nav_teachers -> {
                    val i = Intent(this, InterstitialActivity::class.java)
                    i.putExtra("TARGET_CLASS", TeachersListActivity::class.java.name)
                    startActivity(i)
                    true
                }
                R.id.nav_library -> {
                    val i = Intent(this, InterstitialActivity::class.java)
                    i.putExtra("TARGET_CLASS", LibraryActivity::class.java.name)
                    startActivity(i)
                    true
                }
                R.id.nav_account -> {
                    startActivity(Intent(this, AccountActivity::class.java))
                    true
                }
                else -> true
            }
        }

        // الكروت
        findViewById<View>(R.id.cardSubjects).setOnClickListener {
            startActivity(Intent(this, SubjectsActivity::class.java))
        }
        findViewById<View>(R.id.cardWeekly).setOnClickListener {
            startActivity(Intent(this, WeeklyUpdatesActivity::class.java))
        }
        findViewById<View>(R.id.cardDailyChallenge).setOnClickListener {
            if (challengeDoneToday) {
                startActivity(Intent(this, DailyChallengeActivity::class.java))
            } else {
                val i = Intent(this, InterstitialActivity::class.java)
                i.putExtra("TARGET_CLASS", DailyChallengeActivity::class.java.name)
                startActivity(i)
            }
        }
        findViewById<View>(R.id.cardFriends).setOnClickListener {
            startActivity(Intent(this, ChallengeHomeActivity::class.java))
        }
                findViewById<View>(R.id.cardHonors).setOnClickListener {
            startActivity(Intent(this, MyHonorsActivity::class.java))
                }
        findViewById<View>(R.id.cardMonthly).setOnClickListener {
            val i = Intent(this, InterstitialActivity::class.java)
            i.putExtra("TARGET_CLASS", LeaderboardActivity::class.java.name)
            startActivity(i)
        }
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
                    val currentVersion = 2

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
        if (uiReady) refreshHomeCards()
    }

        private fun refreshHomeCards() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val id = prefs.all["student_id"]?.toString().orEmpty()
        val key = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("Africa/Cairo")
        val today = fmt.format(java.util.Date())

        val tvSub = findViewById<TextView>(R.id.tvDailySub)
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("dailyAttempts").document("${key}_$today").get()
            .addOnSuccessListener { a ->
                if (a.exists()) {
                    challengeDoneToday = true
                    val s = a.getLong("score")?.toInt() ?: 0
                    val t = a.getLong("total")?.toInt() ?: 5
                    tvSub.text = "أنهيتَ تحدي اليوم ✅ نتيجتك $s من $t"
                } else {
                    challengeDoneToday = false
                    db.collection("dailyChallenges").document(today).get()
                        .addOnSuccessListener { c ->
                            val n = (c.get("questions") as? List<*>)?.size ?: 0
                            tvSub.text = if (n > 0) "$n أسئلة · نقطة لكل إجابة صحيحة · اضغط للبدء"
                            else "لا يوجد تحدٍّ لليوم بعد"
                        }
                }
            }

        LeaderboardManager.getHomeSummary(key) { _, rank, pts ->
            findViewById<TextView>(R.id.tvMyRank).text =
                if (rank > 0) "ترتيبك: #$rank · $pts نقطة · اضغط للتفاصيل"
                else "اضغط للتفاصيل"
        }
    }
}
