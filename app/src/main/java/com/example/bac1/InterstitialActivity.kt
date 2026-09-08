package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InterstitialActivity : AppCompatActivity() {

    private var canSkip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interstitial)

        val webView = findViewById<WebView>(R.id.interstitialWebView)
        val countdownText = findViewById<TextView>(R.id.countdownText)
        val skipButton = findViewById<Button>(R.id.skipButton)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val newWebView = WebView(this@InterstitialActivity)
                newWebView.settings.javaScriptEnabled = true
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        webView.clearCache(true)
        webView.loadUrl("https://alifathi12366-arch.github.io/1Bac/interstitial.html")

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = ((millisUntilFinished / 1000) + 1).toString()
            }
            override fun onFinish() {
                countdownText.visibility = View.GONE
                skipButton.visibility = View.VISIBLE
                canSkip = true
            }
        }.start()

        skipButton.setOnClickListener { goToTarget() }
    }

    private fun goToTarget() {
        val targetClassName = intent.getStringExtra("TARGET_CLASS")
        if (targetClassName != null) {
            try {
                startActivity(Intent(this, Class.forName(targetClassName)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        finish()
    }

    override fun onBackPressed() {
        if (canSkip) goToTarget()
    }
}
