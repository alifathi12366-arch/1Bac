package com.example.bac1

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        checkForUpdate()
        loadAd()
        checkDailyLogin()

        setupSubject(
            cardId = R.id.cardArabic,
            subjectName = "اللغة العربية",
            desc = "القراءة، النصوص، الأدب، النحو",
            colorHex = "#F5C518",
            bookLink = "https://drive.google.com/file/d/1_wabND8G53RlCFxyIeIP4NYOAJSesBT7/view?usp=drivesdk",
            teacherLink = "https://youtube.com/@mohamedsalah.bassthalk?si=BMSSudooCyUUFvoo",
            bookLink2 = "https://drive.google.com/file/d/12c_-S5uAy-foYtb42oROwS_fq6QOLo9J/view?usp=drivesdk",
            bookLink3 = "https://drive.google.com/file/d/1oH-1lxOgCIbED17eBBvfTIHTi0EZrua1/view?usp=drivesdk",
            bookLabel1 = "كتاب القراءة والنصوص",
            bookLabel2 = "كتاب النحو",
            bookLabel3 = "كتاب القصة",
            subjectKey = ""
        )
        setupSubject(R.id.cardEnglish, "اللغة الإنجليزية", "Reading Comprehension & Writing", "#4FA3E3", "https://drive.google.com/file/d/1Z39u9VOs8SH6nrHxZ3IE3SznDxbQPu5V/view?usp=drivesdk", "https://youtube.com/@mahmoudradwan.englishawy?si=IG4D6QJ7oUNo8nVA", subjectKey = "english")
        setupSubject(R.id.cardMath, "الرياضيات", "الحساب، الدوال، الهندسة", "#3DBFA0", "https://drive.google.com/file/d/1oxls3xtQ4D6UgsXchYI4jB_YU2tedCET/view?usp=drivesdk", "https://youtube.com/@-mrlotfyzahran4469?si=PbZcVrEKd7o8DJYG", subjectKey = "math")
        setupSubject(R.id.cardScience, "العلوم المتكاملة", "الفيزياء والكيمياء وعلوم الحياة", "#5C7CFA", "https://drive.google.com/file/d/1KtD56ULXsVh1imQaN4JIIwjPoFtjdhdr/view?usp=drivesdk", "https://youtube.com/@gohary?si=Gq5uRgIbQWSCmOnu", subjectKey = "science")
        setupSubject(R.id.cardPhilosophy, "الفلسفة والمنطق", "المفاهيم الفلسفية والاستدلال", "#9ACD32", "https://drive.google.com/file/d/1WO_PYfE_j5SIOKMpX0UMah85cbkuM1k1/view?usp=drivesdk", "https://youtube.com/@filasofmasr?si=_DUPM-YnU_foOrud", subjectKey = "philosophy")
        setupSubject(R.id.cardHistory, "التاريخ", "الحضارات القديمة والتاريخ المعاصر", "#E06B9E", "https://drive.google.com/file/d/1bZgJHy_363Ic1J4csuEUOsK3h0VzB0rn/view?usp=drivesdk", "https://youtube.com/@-mostafaarafa4617?si=qrWg04WNl9jGYVdu", subjectKey = "history")
        setupSubject(R.id.cardProgramming, "البرمجة", "أساسيات البرمجة والذكاء الاصطناعي", "#8E9BAE", "https://drive.google.com/file/d/1AtcPFcl7ZNciYnFwSfFLv3mdX5T9tq7z/view?usp=drivesdk", "", subjectKey = "programming")
        setupSubject(R.id.cardIslamic, "التربية الإسلامية", "العقيدة، الفقه، والسيرة النبوية", "#7B7FE0", "https://drive.google.com/file/d/1adHx4kesuOnn965bqjGIp-iUrpBDNc1b/view?usp=drivesdk", "", subjectKey = "")
        setupSubject(R.id.cardFrench, "اللغة الفرنسية", "القواعد والتعبير الكتابي والفهم", "#B58ED6", "https://drive.google.com/file/d/1SWQm7elUNKIM9mEBzogFnbi9ld6UGfbn/view?usp=drivesdk", "", subjectKey = "french")

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_schedule -> {
                    startActivity(Intent(this, ScheduleActivity::class.java))
                    true
                }
                R.id.nav_library -> {
                    startActivity(Intent(this, LibraryActivity::class.java))
                    true
                }
                R.id.nav_account -> {
                    startActivity(Intent(this, AccountActivity::class.java))
                    true
                }
                else -> true
            }
        }
    }

    private fun setupSubject(
        cardId: Int,
        subjectName: String,
        desc: String,
        colorHex: String,
        bookLink: String,
        teacherLink: String,
        bookLink2: String = "",
        bookLink3: String = "",
        bookLabel1: String = "فتح كتاب المادة",
        bookLabel2: String = "فتح كتاب المادة",
        bookLabel3: String = "فتح كتاب المادة",
        subjectKey: String = ""
    ) {
        val card = findViewById<android.view.View>(cardId)
        val nameView = card.findViewById<TextView>(R.id.subjectName)
        val descView = card.findViewById<TextView>(R.id.subjectDesc)
        val iconBox = card.findViewById<LinearLayout>(R.id.iconBox)

        nameView.text = subjectName
        descView.text = desc
        iconBox.setBackgroundColor(Color.parseColor(colorHex))

        card.setOnClickListener {
            val intent = Intent(this, SubjectDetailActivity::class.java)
            intent.putExtra("subjectName", subjectName)
            intent.putExtra("subjectKey", subjectKey)
            intent.putExtra("bookLink", bookLink)
            intent.putExtra("bookLink2", bookLink2)
            intent.putExtra("bookLink3", bookLink3)
            intent.putExtra("bookLabel1", bookLabel1)
            intent.putExtra("bookLabel2", bookLabel2)
            intent.putExtra("bookLabel3", bookLabel3)
            intent.putExtra("teacherLink", teacherLink)
            startActivity(intent)
        }
    }

    private fun checkDailyLogin() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val lastLoginDate = prefs.getString("last_login_date", "")

        android.widget.Toast.makeText(
            this,
            "اليوم: $today | آخر دخول محفوظ: $lastLoginDate",
            android.widget.Toast.LENGTH_LONG
        ).show()

        if (lastLoginDate != today) {
            val currentPoints = prefs.getInt("student_points", 0)
            prefs.edit()
                .putInt("student_points", currentPoints + 1)
                .putString("last_login_date", today)
                .apply()
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
                    val currentVersion = 1

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
        val webView = findViewById<WebView>(R.id.adWebView)
        
        // إعدادات الـ WebView الممتازة لإعلانات Adsterra
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // حل مشكلة ERR_CACHE_MISS نهائياً
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // السماح بتحميل الإعلانات المشفرة وغير المشفرة
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

        // مسح الكاش القديم قبل التحميل لضمان إزالة الخطأ
        webView.clearCache(true)
        webView.loadUrl("https://alifathi12366-arch.github.io/1Bac/ad.html")
    }
}
