package com.example.bac1

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class EnglishWordsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private class Word(val en: String, val ar: String, val sEn: String, val sAr: String)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var current: Word? = null
    private val saved = mutableListOf<Word>()

    private lateinit var etWord: EditText
    private lateinit var btnTranslate: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var cardResult: View
    private lateinit var tvEn: TextView
    private lateinit var tvAr: TextView
    private lateinit var tvSentEn: TextView
    private lateinit var tvSentAr: TextView
    private lateinit var savedBox: LinearLayout
    private lateinit var tvSavedTitle: TextView

    private val prefs by lazy { getSharedPreferences("bac1_words", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_english_words)

        tts = TextToSpeech(this, this)

        etWord = findViewById(R.id.etWord)
        btnTranslate = findViewById(R.id.btnTranslate)
        progress = findViewById(R.id.progressWord)
        tvError = findViewById(R.id.tvWordError)
        cardResult = findViewById(R.id.cardResult)
        tvEn = findViewById(R.id.tvEn)
        tvAr = findViewById(R.id.tvAr)
        tvSentEn = findViewById(R.id.tvSentEn)
        tvSentAr = findViewById(R.id.tvSentAr)
        savedBox = findViewById(R.id.savedContainer)
        tvSavedTitle = findViewById(R.id.tvSavedTitle)

        btnTranslate.setOnClickListener { translate() }
        findViewById<Button>(R.id.btnSpeakWord).setOnClickListener { current?.let { speak(it.en) } }
        findViewById<Button>(R.id.btnSpeakSentence).setOnClickListener { current?.let { speak(it.sEn) } }
        findViewById<Button>(R.id.btnSaveWord).setOnClickListener { saveCurrent() }

        loadSaved()
        renderSaved()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale.US)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.9f)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Toast.makeText(this, "النطق مش متاح على موبايلك", Toast.LENGTH_SHORT).show()
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "w")
    }

    // ---------- الترجمة ----------
    private fun translate() {
        val text = etWord.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "اكتب كلمة الأول", Toast.LENGTH_SHORT).show()
            return
        }
        if (text.length > 40) {
            Toast.makeText(this, "اكتب كلمة أو جملة قصيرة (لحد 40 حرف)", Toast.LENGTH_SHORT).show()
            return
        }
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            showError("المفتاح مش متضبط في البناء")
            return
        }

        tvError.visibility = View.GONE
        cardResult.visibility = View.GONE
        progress.visibility = View.VISIBLE
        btnTranslate.isEnabled = false

        Thread {
            var word: Word? = null
            var error: String? = null
            try {
                val prompt = "You are a friendly English tutor for Egyptian high-school students. " +
                    "The student typed: \"$text\". " +
                    "If it is Arabic, translate it to the most natural English word or phrase. " +
                    "If it is English, translate it to Arabic. " +
                    "Then write ONE short, casual English sentence using the English word, " +
                    "in the style of a guy chatting with his guy friend (relaxed and friendly, " +
                    "clean and appropriate for students). " +
                    "Also give the Arabic translation of that sentence in Egyptian colloquial Arabic. " +
                    "Reply ONLY with JSON in this exact shape: " +
                    "{\"english\":\"\",\"arabic\":\"\",\"sentence_en\":\"\",\"sentence_ar\":\"\"}"

                val body = JSONObject()
                    .put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    ))
                    .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent")

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                conn.disconnect()

                if (code !in 200..299) {
                    error = when (code) {
                        429 -> "الحد المجاني خلص دلوقتي، جرب بعد شوية"
                        400, 401, 403 -> "فيه مشكلة في المفتاح أو الطلب ($code)"
                        else -> "حصل خطأ ($code)"
                    }
                } else {
                    val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.optBoolean("thought", false)) continue
                        sb.append(p.optString("text", ""))
                    }
                    val clean = sb.toString().replace("```json", "").replace("```", "").trim()
                    val o = JSONObject(clean)
                    val en = o.optString("english").trim()
                    if (en.isEmpty()) {
                        error = "ما قدرتش أترجم الكلمة دي، جرب كلمة تانية"
                    } else {
                        word = Word(
                            en, o.optString("arabic").trim(),
                            o.optString("sentence_en").trim(), o.optString("sentence_ar").trim()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                error = "تعذّر الاتصال، تحقق من الإنترنت وحاول تاني"
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                progress.visibility = View.GONE
                btnTranslate.isEnabled = true
                if (word != null) showWord(word) else showError(error ?: "حصل خطأ")
            }
        }.start()
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun showWord(w: Word) {
        current = w
        tvEn.text = w.en
        tvAr.text = w.ar
        tvSentEn.text = w.sEn
        tvSentAr.text = w.sAr
        cardResult.visibility = View.VISIBLE
        speak(w.en)
    }

    // ---------- الحفظ ----------
    private fun loadSaved() {
        saved.clear()
        try {
            val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                saved.add(Word(o.optString("en"), o.optString("ar"), o.optString("sEn"), o.optString("sAr")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        saved.forEach {
            arr.put(JSONObject().put("en", it.en).put("ar", it.ar).put("sEn", it.sEn).put("sAr", it.sAr))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun saveCurrent() {
        val w = current ?: return
        if (saved.any { it.en.equals(w.en, ignoreCase = true) }) {
            Toast.makeText(this, "الكلمة محفوظة قبل كده", Toast.LENGTH_SHORT).show()
            return
        }
        saved.add(0, w)
        persist()
        renderSaved()
        Toast.makeText(this, "تم حفظ الكلمة ⭐", Toast.LENGTH_SHORT).show()
    }

    private fun renderSaved() {
        savedBox.removeAllViews()
        tvSavedTitle.text = if (saved.isEmpty()) "كلماتي المحفوظة" else "كلماتي المحفوظة (${saved.size})"
        if (saved.isEmpty()) {
            val t = TextView(this)
            t.text = "لسه ما حفظتش كلمات.\nترجم كلمة واضغط \"احفظ\"."
            t.gravity = Gravity.CENTER
            t.textSize = 14f
            t.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            t.setPadding(0, dp(16), 0, dp(16))
            savedBox.addView(t)
            return
        }
        saved.forEach { w -> savedBox.addView(savedRow(w)) }
    }

    private fun savedRow(w: Word): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundColor(Color.WHITE)
        row.setPadding(dp(12), dp(10), dp(8), dp(10))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(8)
        row.layoutParams = lp

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)

        val en = TextView(this)
        en.text = w.en
        en.textSize = 18f
        en.setTypeface(null, Typeface.BOLD)
        en.textDirection = View.TEXT_DIRECTION_LTR
        en.gravity = Gravity.START
        en.setTextColor(Color.parseColor("#1565C0"))
        col.addView(en)

        val ar = TextView(this)
        ar.text = w.ar
        ar.textSize = 15f
        ar.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
        col.addView(ar)

        if (w.sEn.isNotEmpty()) {
            val s = TextView(this)
            s.text = w.sEn
            s.textSize = 13f
            s.textDirection = View.TEXT_DIRECTION_LTR
            s.gravity = Gravity.START
            s.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            s.setPadding(0, dp(4), 0, 0)
            col.addView(s)
        }
        row.addView(col)

        val sp = TextView(this)
        sp.text = "🔊"
        sp.textSize = 22f
        sp.setPadding(dp(10), dp(6), dp(10), dp(6))
        sp.setOnClickListener { speak(if (w.sEn.isNotEmpty()) w.en + ". " + w.sEn else w.en) }
        row.addView(sp)

        val del = TextView(this)
        del.text = "🗑️"
        del.textSize = 20f
        del.setPadding(dp(10), dp(6), dp(10), dp(6))
        del.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("حذف الكلمة")
                .setMessage("تحذف \"${w.en}\" من كلماتك؟")
                .setPositiveButton("احذف") { _, _ ->
                    saved.remove(w)
                    persist()
                    renderSaved()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
        row.addView(del)
        return row
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
