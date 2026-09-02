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

        setupSubject(R.id.cardArabic, "اللغة العربية", "القراءة، النصوص، الأدب، النحو", "#F5C518", "", "")
        setupSubject(R.id.cardEnglish, "اللغة الإنجليزية", "Reading Comprehension & Writing", "#4FA3E3", "https://drive.google.com/file/d/1Z39u9VOs8SH6nrHxZ3IE3SznDxbQPu5V/view?usp=drivesdk", "https://youtube.com/@mahmoudradwan.englishawy?si=IG4D6QJ7oUNo8nVA")
        setupSubject(R.id.cardMath, "الرياضيات", "الحساب، الدوال، الهندسة", "#3DBFA0", "", "")
        setupSubject(R.id.cardScience, "العلوم المتكاملة", "الفيزياء والكيمياء وعلوم الحياة", "#5C7CFA", "", "")
        setupSubject(R.id.cardPhilosophy, "الفلسفة والمنطق", "المفاهيم الفلسفية والاستدلال", "#9ACD32", "https://drive.google.com/file/d/1WO_PYfE_j5SIOKMpX0UMah85cbkuM1k1/view?usp=drivesdk", "https://youtube.com/@filasofmasr?si=_DUPM-YnU_foOrud")
        setupSubject(R.id.cardHistory, "التاريخ", "الحضارات القديمة والتاريخ المعاصر", "#E06B9E", "https://drive.google.com/file/d/1bZgJHy_363Ic1J4csuEUOsK3h0VzB0rn/view?usp=drivesdk", "https://youtube.com/@-mostafaarafa4617?si=qrWg04WNl9jGYVdu")
        setupSubject(R.id.cardProgramming, "البرمجة", "أساسيات البرمجة والذكاء الاصطناعي", "#8E9BAE", "", "")
        setupSubject(R.id.cardIslamic, "التربية الإسلامية", "العقيدة، الفقه، والسيرة النبوية", "#7B7FE0", "", "")
        setupSubject(R.id.cardFrench, "اللغة الفرنسية", "القواعد والتعبير الكتابي والفهم", "#B58ED6", "", "")

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
        teacherLink: String
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
            intent.putExtra("bookLink", bookLink)
            intent.putExtra("teacherLink", teacherLink)
            startActivity(intent)
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
