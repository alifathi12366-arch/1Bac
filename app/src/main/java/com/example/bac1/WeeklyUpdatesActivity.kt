package com.example.bac1

import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class WeeklyUpdatesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_updates)

        loadAd()

        val container = findViewById<LinearLayout>(R.id.updatesContainer)
        val noUpdatesText = findViewById<TextView>(R.id.noUpdatesText)

        FirebaseFirestore.getInstance().collection("weeklyUpdates")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    noUpdatesText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                for (doc in result.documents) {
                    val title = doc.getString("title") ?: ""
                    val imageUrl = doc.getString("imageUrl") ?: ""
                    val source = doc.getString("source") ?: ""

                    val block = layoutInflater.inflate(R.layout.item_weekly_update, container, false)
                    block.findViewById<TextView>(R.id.tvUpdateTitle).text = title
                    block.findViewById<TextView>(R.id.tvUpdateSource).text = source

                    val imageView = block.findViewById<ImageView>(R.id.ivUpdateImage)
                    if (imageUrl.isNotBlank()) {
                        Glide.with(this).load(imageUrl).into(imageView)
                    }

                    container.addView(block)
                }
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
