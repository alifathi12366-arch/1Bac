
package com.example.bac1

import android.database.sqlite.SQLiteDatabase
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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

class EnglishWordsActivity : AppCompatActivity(),
    TextToSpeech.OnInitListener {

    private data class Word(
        val en: String,
        val ar: String,
        val sEn: String = "",
        val sAr: String = ""
    )

    private data class DictionaryEntry(
        val en: String,
        val ar: String
    )

    companion object {
        private const val MAX_RESULTS = 12
        private const val DB_NAME = "1bac_dictionary.db"
        private const val INITIAL_VERSION = 1L
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var current: Word? = null

    private val saved = mutableListOf<Word>()

    private var database: SQLiteDatabase? = null
    private var dictionaryReady = false

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

    private val prefs by lazy {
        getSharedPreferences("bac1_words", MODE_PRIVATE)
    }

    private val updatePrefs by lazy {
        getSharedPreferences("dictionary_update", MODE_PRIVATE)
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

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

        tvSentEn.visibility = View.GONE
        tvSentAr.visibility = View.GONE

        findViewById<Button>(R.id.btnSpeakSentence)
            .visibility = View.GONE

        btnTranslate.setOnClickListener {
            translate()
        }

        findViewById<Button>(R.id.btnSpeakWord)
            .setOnClickListener {
                current?.let { speak(it.en) }
            }

        findViewById<Button>(R.id.btnSaveWord)
            .setOnClickListener {
                saveCurrent()
            }

        loadSaved()
        renderSaved()
        loadDictionary()
    }

    // ---------------- النطق ----------------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)

            ttsReady =
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED

            tts?.setSpeechRate(0.9f)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Toast.makeText(
                this,
                "النطق مش متاح على موبايلك",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "word_speech"
        )
    }

    // ---------------- تحميل قاعدة البيانات ----------------

    private fun loadDictionary() {
        progress.visibility = View.VISIBLE
        btnTranslate.isEnabled = false
        tvError.visibility = View.GONE

        Thread {
            var db: SQLiteDatabase? = null

            try {
                val dbFile = File(filesDir, DB_NAME)
                val backupFile = File(filesDir, "$DB_NAME.backup")

                // استرجاع النسخة الاحتياطية عند الحاجة
                if (!dbFile.exists() && backupFile.exists()) {
                    backupFile.renameTo(dbFile)
                }

                // إنشاء النسخة الأساسية أول مرة فقط
                if (!dbFile.exists() || dbFile.length() == 0L) {
                    assets.open(DB_NAME).use { input ->
                        FileOutputStream(dbFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // فحص النسخة المحلية
                if (!isValidDictionary(dbFile)) {
                    if (backupFile.exists() &&
                        isValidDictionary(backupFile)
                    ) {
                        dbFile.delete()
                        backupFile.renameTo(dbFile)
                    } else {
                        throw Exception("قاعدة البيانات المحلية غير صالحة")
                    }
                }

                // محاولة تحديث القاموس من GitHub
                try {
                    checkDictionaryUpdate(dbFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // الاحتفاظ بالنسخة القديمة عند فشل التحديث
                }

                // فتح قاعدة البيانات بعد محاولة التحديث
                db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )

                db.rawQuery(
                    "SELECT english, arabic FROM words LIMIT 1",
                    null
                ).use { }

                runOnUiThread {
                    database?.close()
                    database = db
                    dictionaryReady = true

                    progress.visibility = View.GONE
                    btnTranslate.isEnabled = true

                    Toast.makeText(
                        this,
                        "قاموس 1Bac جاهز للاستخدام",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                db?.close()

                runOnUiThread {
                    progress.visibility = View.GONE
                    btnTranslate.isEnabled = true

                    showError(
                        "مشكلة في القاموس: ${e.message}"
                    )
                }
            }
        }.start()
    }

    // ---------------- تحديث القاموس ----------------

    private fun checkDictionaryUpdate(dbFile: File) {

        val document = Tasks.await(
            FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("dictionary")
                .get()
        )

        if (!document.exists()) return

        val remoteVersion =
            document.getLong("version") ?: return

        val downloadUrl =
            document.getString("downloadUrl") ?: return

        // النسخة الموجودة داخل التطبيق هي الإصدار الأول
        val currentVersion = updatePrefs.getLong(
            "version",
            INITIAL_VERSION
        )

        if (remoteVersion <= currentVersion) return

        val tempFile = File(filesDir, "$DB_NAME.tmp")
        val backupFile = File(filesDir, "$DB_NAME.backup")

        try {
            tempFile.delete()

            val connection =
                URL(downloadUrl).openConnection() as HttpURLConnection

            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true

            try {
                connection.connect()

                if (connection.responseCode !=
                    HttpURLConnection.HTTP_OK
                ) {
                    throw Exception("فشل تحميل القاموس")
                }

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

            } finally {
                connection.disconnect()
            }

            // فحص الملف الجديد قبل اعتماده
            if (!isValidDictionary(tempFile)) {
                throw Exception("ملف القاموس الجديد غير صالح")
            }

            // حفظ النسخة القديمة
            if (backupFile.exists()) {
                backupFile.delete()
            }

            if (!dbFile.renameTo(backupFile)) {
                throw Exception("تعذر حفظ النسخة القديمة")
            }

            // استبدال النسخة القديمة بالجديدة
            if (!tempFile.renameTo(dbFile)) {
                backupFile.renameTo(dbFile)
                throw Exception("تعذر اعتماد القاموس الجديد")
            }

            // حفظ الإصدار بعد نجاح الاستبدال
            val savedVersion = updatePrefs.edit()
                .putLong("version", remoteVersion)
                .commit()

            if (!savedVersion) {
                dbFile.delete()
                backupFile.renameTo(dbFile)
                throw Exception("تعذر حفظ رقم الإصدار")
            }

            // حذف النسخة الاحتياطية بعد نجاح كل شيء
            backupFile.delete()

        } finally {
            tempFile.delete()
        }
    }

    // ---------------- فحص سلامة قاعدة البيانات ----------------

    private fun isValidDictionary(file: File): Boolean {

        if (!file.exists() || file.length() == 0L) {
            return false
        }

        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->

                db.rawQuery(
                    "PRAGMA quick_check",
                    null
                ).use { cursor ->

                    if (!cursor.moveToFirst() ||
                        cursor.getString(0) != "ok"
                    ) {
                        return false
                    }
                }

                db.rawQuery(
                    "SELECT english, arabic FROM words LIMIT 1",
                    null
                ).use { }
            }

            true

        } catch (e: Exception) {
            false
        }
    }

    // ---------------- تنظيف النص العربي ----------------

    private fun normalizeArabic(text: String): String {
        return Normalizer.normalize(
            text,
            Normalizer.Form.NFKC
        )
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace("ـ", "")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
            .replace(Regex("[،؛,:.!؟()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsArabicPhrase(
        text: String,
        query: String
    ): Boolean {

        val cleanText = normalizeArabic(text)
        val cleanQuery = normalizeArabic(query)

        if (cleanText.isEmpty() || cleanQuery.isEmpty()) {
            return false
        }

        val paddedText = " $cleanText "
        val paddedQuery = " $cleanQuery "

        return paddedText.contains(paddedQuery)
    }

    // ---------------- البحث الإنجليزي ----------------

    private fun searchEnglish(
        query: String
    ): List<DictionaryEntry> {

        val db = database ?: return emptyList()
        val results = mutableListOf<DictionaryEntry>()

        db.rawQuery(
            """
            SELECT english, arabic
            FROM words
            WHERE TRIM(english) = ? COLLATE NOCASE
            LIMIT 100
            """.trimIndent(),
            arrayOf(query)
        ).use { cursor ->

            while (cursor.moveToNext()) {
                results.add(
                    DictionaryEntry(
                        cursor.getString(0),
                        cursor.getString(1)
                    )
                )
            }
        }

        if (results.isNotEmpty()) {
            return results.distinctBy {
                it.en.lowercase(Locale.ROOT) + "|" + it.ar
            }.take(MAX_RESULTS)
        }

        db.rawQuery(
            """
            SELECT english, arabic
            FROM words
            WHERE english LIKE ? COLLATE NOCASE
            ORDER BY english
            LIMIT 100
            """.trimIndent(),
            arrayOf("$query%")
        ).use { cursor ->

            while (cursor.moveToNext()) {
                results.add(
                    DictionaryEntry(
                        cursor.getString(0),
                        cursor.getString(1)
                    )
                )
            }
        }

        return results.distinctBy {
            it.en.lowercase(Locale.ROOT) + "|" + it.ar
        }.take(MAX_RESULTS)
    }

    // ---------------- البحث العربي ----------------

    private fun searchArabic(
        query: String
    ): List<DictionaryEntry> {

        val db = database ?: return emptyList()
        val candidates = mutableListOf<DictionaryEntry>()

        db.rawQuery(
            """
            SELECT english, arabic
            FROM words
            WHERE arabic LIKE ?
            LIMIT 1000
            """.trimIndent(),
            arrayOf("%$query%")
        ).use { cursor ->

            while (cursor.moveToNext()) {
                candidates.add(
                    DictionaryEntry(
                        cursor.getString(0),
                        cursor.getString(1)
                    )
                )
            }
        }

        return candidates
            .filter {
                containsArabicPhrase(it.ar, query)
            }
            .distinctBy {
                it.en.lowercase(Locale.ROOT) + "|" + it.ar
            }
            .sortedBy {
                if (
                    normalizeArabic(it.ar) ==
                    normalizeArabic(query)
                ) 0 else 1
            }
            .take(MAX_RESULTS)
    }

    // ---------------- تنفيذ الترجمة ----------------

    private fun translate() {

        val query = etWord.text.toString().trim()

        if (query.isEmpty()) {
            Toast.makeText(
                this,
                "اكتب كلمة الأول",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!dictionaryReady) {
            Toast.makeText(
                this,
                "القاموس لسه بيتم تحميله",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        tvError.visibility = View.GONE
        cardResult.visibility = View.GONE

        btnTranslate.isEnabled = false
        progress.visibility = View.VISIBLE

        Thread {
            try {
                val isArabic = query.any {
                    it in '\u0600'..'\u06FF'
                }

                val results = if (isArabic) {
                    searchArabic(query)
                } else {
                    searchEnglish(query)
                }

                runOnUiThread {
                    progress.visibility = View.GONE
                    btnTranslate.isEnabled = true

                    if (results.isEmpty()) {
                        showError(
                            "مش لاقي الكلمة دي في القاموس"
                        )
                        return@runOnUiThread
                    }

                    val englishMeanings = results
                        .map { it.en }
                        .distinctBy {
                            it.lowercase(Locale.ROOT)
                        }

                    val arabicMeanings = results
                        .map { it.ar }
                        .distinct()

                    val word = Word(
                        en = englishMeanings.joinToString("، "),
                        ar = arabicMeanings.joinToString("\n")
                    )

                    showWord(word)
                }

            } catch (e: Exception) {
                e.printStackTrace()

                runOnUiThread {
                    progress.visibility = View.GONE
                    btnTranslate.isEnabled = true
                    showError("حصل خطأ أثناء البحث")
                }
            }
        }.start()
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun showWord(word: Word) {
        current = word

        tvEn.text = word.en
        tvAr.text = word.ar

        tvSentEn.visibility = View.GONE
        tvSentAr.visibility = View.GONE

        cardResult.visibility = View.VISIBLE
    }

    // ---------------- حفظ الكلمات ----------------

    private fun loadSaved() {
        saved.clear()

        try {
            val arr = JSONArray(
                prefs.getString("list", "[]") ?: "[]"
            )

            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)

                saved.add(
                    Word(
                        item.optString("en"),
                        item.optString("ar"),
                        item.optString("sEn"),
                        item.optString("sAr")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persist() {
        val arr = JSONArray()

        saved.forEach {
            arr.put(
                JSONObject()
                    .put("en", it.en)
                    .put("ar", it.ar)
                    .put("sEn", it.sEn)
                    .put("sAr", it.sAr)
            )
        }

        prefs.edit()
            .putString("list", arr.toString())
            .apply()
    }

    private fun saveCurrent() {
        val word = current ?: return

        if (saved.any {
                it.en.equals(word.en, ignoreCase = true)
            }) {
            Toast.makeText(
                this,
                "الكلمة محفوظة قبل كده",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        saved.add(0, word)

        persist()
        renderSaved()

        Toast.makeText(
            this,
            "تم حفظ الكلمة ⭐",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------------- عرض المحفوظات ----------------

    private fun renderSaved() {
        savedBox.removeAllViews()

        tvSavedTitle.text =
            if (saved.isEmpty()) {
                "كلماتي المحفوظة"
            } else {
                "كلماتي المحفوظة (${saved.size})"
            }

        if (saved.isEmpty()) {
            val text = TextView(this)

            text.text =
                "لسه ما حفظتش كلمات.\nترجم كلمة واضغط احفظ."

            text.gravity = Gravity.CENTER
            text.textSize = 14f

            text.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.text_gray
                )
            )

            text.setPadding(0, dp(16), 0, dp(16))

            savedBox.addView(text)
            return
        }

        saved.forEach { word ->
            savedBox.addView(savedRow(word))
        }
    }

    private fun savedRow(word: Word): View {
        val row = LinearLayout(this)

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundColor(Color.WHITE)

        row.setPadding(
            dp(12),
            dp(10),
            dp(8),
            dp(10)
        )

        val params = LinearLayout.LayoutParams(
            -1,
            -2
        )

        params.topMargin = dp(8)
        row.layoutParams = params

        val column = LinearLayout(this)

        column.orientation = LinearLayout.VERTICAL

        column.layoutParams = LinearLayout.LayoutParams(
            0,
            -2,
            1f
        )

        val english = TextView(this)

        english.text = word.en
        english.textSize = 18f
        english.setTypeface(null, Typeface.BOLD)
        english.textDirection = View.TEXT_DIRECTION_LTR
        english.gravity = Gravity.START

        english.setTextColor(
            Color.parseColor("#1565C0")
        )

        column.addView(english)

        val arabic = TextView(this)

        arabic.text = word.ar
        arabic.textSize = 15f

        arabic.setTextColor(
            ContextCompat.getColor(
                this,
                R.color.text_dark
            )
        )

        column.addView(arabic)
        row.addView(column)

        val speakButton = TextView(this)

        speakButton.text = "🔊"
        speakButton.textSize = 22f

        speakButton.setPadding(
            dp(10),
            dp(6),
            dp(10),
            dp(6)
        )

        speakButton.setOnClickListener {
            speak(word.en)
        }

        row.addView(speakButton)

        val deleteButton = TextView(this)

        deleteButton.text = "🗑️"
        deleteButton.textSize = 20f

        deleteButton.setPadding(
            dp(10),
            dp(6),
            dp(10),
            dp(6)
        )

        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("حذف الكلمة")
                .setMessage(
                    "تحذف \"${word.en}\" من كلماتك؟"
                )
                .setPositiveButton("احذف") { _, _ ->
                    saved.remove(word)
                    persist()
                    renderSaved()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        row.addView(deleteButton)

        return row
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()

        database?.close()
        database = null

        super.onDestroy()
    }
}
