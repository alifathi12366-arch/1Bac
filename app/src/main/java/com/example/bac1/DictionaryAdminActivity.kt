
package com.example.bac1

import android.os.Bundle
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import java.io.File

class DictionaryAdminActivity : AppCompatActivity() {

    private var database: SQLiteDatabase? = null

    private lateinit var englishInput: EditText
    private lateinit var arabicInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var countText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var addButton: Button
    private lateinit var exportButton: Button

    private val EXPORT_REQUEST = 2001

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }

        val title = TextView(this).apply {
            text = "📚 إدارة قاموس 1Bac"
            textSize = 23f
            gravity = Gravity.CENTER
        }

        countText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, dp(15))
        }

        englishInput = EditText(this).apply {
            hint = "الكلمة الإنجليزية"
            setSingleLine(true)
        }

        arabicInput = EditText(this).apply {
            hint = "المعنى بالعربي"
        }

        addButton = Button(this).apply {
            text = "إضافة الكلمة"
            isEnabled = false
        }

        exportButton = Button(this).apply {
            text = "📦 تصدير قاعدة البيانات"
            isEnabled = false
        }

        searchInput = EditText(this).apply {
            hint = "ابحث عن كلمة أو معنى"
            setSingleLine(true)
        }

        val searchButton = Button(this).apply {
            text = "بحث"
        }

        resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(title)
        root.addView(countText)
        root.addView(englishInput)
        root.addView(arabicInput)
        root.addView(addButton)
        root.addView(exportButton)
        root.addView(searchInput)
        root.addView(searchButton)
        root.addView(resultsContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)

        try {
            openDictionary()
            updateWordCount()
            addButton.isEnabled = true
            exportButton.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "خطأ في فتح القاموس: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        addButton.setOnClickListener {
            addWord()
        }

        searchButton.setOnClickListener {
            searchWords()
        }

        exportButton.setOnClickListener {
            exportDictionary()
        }
    }

    // فتح قاعدة البيانات المحلية
    private fun openDictionary() {
        val dbFile = File(filesDir, "1bac_dictionary.db")

        if (!dbFile.exists() || dbFile.length() == 0L) {
            assets.open("1bac_dictionary.db").use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        database = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )

        database?.rawQuery(
            "SELECT english, arabic FROM words LIMIT 1",
            null
        )?.use {
            // التأكد من وجود الجدول والأعمدة
        }
    }

    // تحديث عدد الكلمات
    private fun updateWordCount() {
        database?.rawQuery(
            "SELECT COUNT(*) FROM words",
            null
        )?.use {
            if (it.moveToFirst()) {
                countText.text = "إجمالي الكلمات: ${it.getInt(0)}"
            }
        }
    }

    // إضافة كلمة جديدة
    private fun addWord() {
        val english = englishInput.text.toString().trim()
        val arabic = arabicInput.text.toString().trim()
        val db = database ?: return

        if (english.isEmpty() || arabic.isEmpty()) {
            toast("اكتب الكلمة ومعناها أولًا")
            return
        }

        try {
            val duplicate = db.rawQuery(
                """
                SELECT 1 FROM words
                WHERE english = ? COLLATE NOCASE
                AND arabic = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(english, arabic)
            ).use {
                it.moveToFirst()
            }

            if (duplicate) {
                toast("الكلمة بنفس المعنى موجودة بالفعل")
                return
            }

            val values = ContentValues().apply {
                put("english", english)
                put("arabic", arabic)
            }

            val result = db.insert("words", null, values)

            if (result != -1L) {
                englishInput.text.clear()
                arabicInput.text.clear()
                updateWordCount()
                toast("تمت إضافة الكلمة بنجاح")
            } else {
                toast("لم تتم الإضافة")
            }

        } catch (e: Exception) {
            toast("خطأ أثناء الإضافة: ${e.message}")
        }
    }

    // البحث عن الكلمات والمعاني
    private fun searchWords() {
        val query = searchInput.text.toString().trim()
        val db = database ?: return

        resultsContainer.removeAllViews()

        if (query.isEmpty()) {
            toast("اكتب كلمة للبحث عنها")
            return
        }

        try {
            val cursor = db.rawQuery(
                """
                SELECT rowid, english, arabic
                FROM words
                WHERE english LIKE ? COLLATE NOCASE
                OR arabic LIKE ?
                ORDER BY english
                LIMIT 50
                """.trimIndent(),
                arrayOf("%$query%", "%$query%")
            )

            cursor.use {
                if (!it.moveToFirst()) {
                    showResultMessage("لا توجد نتائج")
                    return
                }

                do {
                    val id = it.getLong(0)
                    val english = it.getString(1) ?: ""
                    val arabic = it.getString(2) ?: ""

                    addResultRow(id, english, arabic)

                } while (it.moveToNext())
            }

        } catch (e: Exception) {
            toast("خطأ في البحث: ${e.message}")
        }
    }

    // عرض نتيجة البحث
    private fun addResultRow(
        id: Long,
        english: String,
        arabic: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val wordText = TextView(this).apply {
            text = "$english\n$arabic"
            textSize = 17f
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val editButton = Button(this).apply {
            text = "تعديل"
        }

        val deleteButton = Button(this).apply {
            text = "حذف"
        }

        editButton.setOnClickListener {
            showEditDialog(id, english, arabic)
        }

        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("حذف الكلمة")
                .setMessage("هل تريد حذف $english؟")
                .setPositiveButton("حذف") { _, _ ->

                    try {
                        database?.delete(
                            "words",
                            "rowid = ?",
                            arrayOf(id.toString())
                        )

                        searchWords()
                        updateWordCount()
                        toast("تم الحذف")

                    } catch (e: Exception) {
                        toast("تعذر الحذف: ${e.message}")
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        buttons.addView(editButton)
        buttons.addView(deleteButton)

        row.addView(wordText)
        row.addView(buttons)

        resultsContainer.addView(row)

        val divider = android.view.View(this).apply {
            setBackgroundColor(0xFFCCCCCC.toInt())
        }

        resultsContainer.addView(
            divider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )
    }

    // تعديل كلمة موجودة
    private fun showEditDialog(
        id: Long,
        oldEnglish: String,
        oldArabic: String
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
        }

        val englishEdit = EditText(this).apply {
            setText(oldEnglish)
            hint = "الكلمة الإنجليزية"
            setSingleLine(true)
        }

        val arabicEdit = EditText(this).apply {
            setText(oldArabic)
            hint = "المعنى بالعربي"
        }

        layout.addView(englishEdit)
        layout.addView(arabicEdit)

        AlertDialog.Builder(this)
            .setTitle("تعديل الكلمة")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->

                val english = englishEdit.text.toString().trim()
                val arabic = arabicEdit.text.toString().trim()

                if (english.isEmpty() || arabic.isEmpty()) {
                    toast("لا تترك أي حقل فارغًا")
                    return@setPositiveButton
                }

                try {
                    val duplicate = database?.rawQuery(
                        """
                        SELECT 1 FROM words
                        WHERE english = ? COLLATE NOCASE
                        AND arabic = ?
                        AND rowid != ?
                        LIMIT 1
                        """.trimIndent(),
                        arrayOf(
                            english,
                            arabic,
                            id.toString()
                        )
                    )?.use {
                        it.moveToFirst()
                    } ?: false

                    if (duplicate) {
                        toast("هذه الكلمة بنفس المعنى موجودة بالفعل")
                        return@setPositiveButton
                    }

                    val values = ContentValues().apply {
                        put("english", english)
                        put("arabic", arabic)
                    }

                    database?.update(
                        "words",
                        values,
                        "rowid = ?",
                        arrayOf(id.toString())
                    )

                    searchWords()
                    updateWordCount()
                    toast("تم تعديل الكلمة")

                } catch (e: Exception) {
                    toast("تعذر التعديل: ${e.message}")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // تصدير قاعدة البيانات
    private fun exportDictionary() {
        try {
            database?.close()
            database = null

            val dbFile = File(
                filesDir,
                "1bac_dictionary.db"
            )

            if (!dbFile.exists() || dbFile.length() == 0L) {
                openDictionary()
                updateWordCount()
                toast("ملف القاموس غير موجود")
                return
            }

            val intent = Intent(
                Intent.ACTION_CREATE_DOCUMENT
            ).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"

                putExtra(
                    Intent.EXTRA_TITLE,
                    "1bac_dictionary.db"
                )
            }

            startActivityForResult(
                intent,
                EXPORT_REQUEST
            )

        } catch (e: Exception) {
            try {
                openDictionary()
                updateWordCount()
            } catch (_: Exception) {
            }

            toast("خطأ في التصدير: ${e.message}")
        }
    }

    // استلام مكان حفظ الملف ونسخ قاعدة البيانات إليه
    @Deprecated("Using legacy activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode != EXPORT_REQUEST) return

        try {
            if (resultCode == RESULT_OK && data?.data != null) {

                val dbFile = File(
                    filesDir,
                    "1bac_dictionary.db"
                )

                contentResolver.openOutputStream(
                    data.data!!
                )?.use { output ->

                    dbFile.inputStream().use { input ->
                        input.copyTo(output)
                    }

                } ?: throw Exception("تعذر إنشاء الملف")

                toast("تم تصدير القاموس بنجاح")

            } else {
                toast("تم إلغاء التصدير")
            }

        } catch (e: Exception) {
            toast("فشل التصدير: ${e.message}")

        } finally {
            try {
                openDictionary()
                updateWordCount()

            } catch (e: Exception) {
                toast("تعذر إعادة فتح القاموس: ${e.message}")
            }
        }
    }

    private fun showResultMessage(message: String) {
        val text = TextView(this).apply {
            this.text = message
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        }

        resultsContainer.addView(text)
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        database?.close()
        database = null
        super.onDestroy()
    }
}
