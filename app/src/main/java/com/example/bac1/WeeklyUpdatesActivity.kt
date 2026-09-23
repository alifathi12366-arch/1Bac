package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class WeeklyUpdatesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_updates)

        loadAd()

        val container = findViewById<LinearLayout>(R.id.updatesContainer)
        val noUpdatesText = findViewById<TextView>(R.id.noUpdatesText)

        FirebaseFirestore.getInstance().collection("weeklyUpdates")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    noUpdatesText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                noUpdatesText.visibility = View.GONE
                container.removeAllViews()

                // تجميع التقييمات حسب اسم المادة (source)
                val groupedBySubject = result.documents.groupBy { 
                    it.getString("source") ?: "تقييمات عامة" 
                }

                for ((subjectName, docs) in groupedBySubject) {
                    
                    // عنوان المادة (مثلاً: 📘 اللغة العربية)
                    val headerText = TextView(this)
                    headerText.text = "📘 $subjectName"
                    headerText.textSize = 18f
                    headerText.setTextColor(android.graphics.Color.parseColor("#FFD700"))
                    headerText.setTypeface(null, android.graphics.Typeface.BOLD)
                    headerText.setPadding(16, 28, 16, 12)
                    container.addView(headerText)

                    // ترتيب آمن جداً للأسابيع يقرأ الرقم بجميع أشكاله
                    val sortedDocs = docs.sortedBy { doc ->
                        doc.getLong("weekNumber") 
                            ?: doc.getString("weekNumber")?.toLongOrNull() 
                            ?: 0L 
                    }

                    for (doc in sortedDocs) {
                        val title = doc.getString("title") ?: ""
                        val imageUrl = doc.getString("imageUrl") ?: ""
                        val pdfUrl = doc.getString("pdfUrl") ?: ""

                        val block = layoutInflater.inflate(R.layout.item_weekly_update, container, false)
                        block.findViewById<TextView>(R.id.tvUpdateTitle).text = title
                        block.findViewById<TextView>(R.id.tvUpdateSource).text = subjectName

                        val imageView = block.findViewById<ImageView>(R.id.ivUpdateImage)
                        if (imageUrl.isNotBlank()) {
                            imageView.visibility = View.VISIBLE
                            Glide.with(this).load(imageUrl).into(imageView)
                        } else {
                            imageView.visibility = View.GONE
                        }

                        // الضغط على التقييم لفتح الـ PDF
                        block.setOnClickListener {
                            if (pdfUrl.isNotBlank()) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl))
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this, "لا يوجد ملف PDF مرفق", Toast.LENGTH_SHORT).show()
                            }
                        }

                        container.addView(block)
                    }
                }
            }
            .addOnFailureListener { e ->
                noUpdatesText.visibility = View.VISIBLE
            }
    }

    private fun loadAd() {
        val webView = findViewById<WebView>(R.id.adWebViewUpdates) ?: return

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.clearCache(true)
        webView.loadUrl("https://alifathi12366-arch.github.io/1Bac/ad.html")
    }
}
