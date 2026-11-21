
package com.example.bac1

import android.os.Bundle
import android.content.ContentValues
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

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

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
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "خطأ في فتح القاموس: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        addButton.setOnClickListener { addWord() }
        searchButton.setOnClickListener { searchWords() }
    }

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
        )?.use { }
    }

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

    private fun addWord() {
        val english = englishInput.text.toString().trim()
        val arabic = arabicInput.text.toString().trim()
        val db = database ?: return

        if (english.isEmpty() || arabic.isEmpty()) {
            toast("اكتب الكلمة ومعناها أولًا")
            return
        }

        val duplicate = db.rawQuery(
            "SELECT 1 FROM words WHERE english = ? COLLATE NOCASE AND arabic = ? LIMIT 1",
            arrayOf(english, arabic)
        ).use { it.moveToFirst() }

        if (duplicate) {
            toast("الكلمة بنفس المعنى موجودة بالفعل")
            return
        }

        val values = ContentValues().apply {
            put("english", english)
            put("arabic", arabic)
        }

        if (db.insert("words", null, values) != -1L) {
            englishInput.text.clear()
            arabicInput.text.clear()
            updateWordCount()
            toast("تمت إضافة الكلمة بنجاح")
        } else {
            toast("لم تتم الإضافة")
        }
    }

    private fun searchWords() {
        val query = searchInput.text.toString().trim()
        val db = database ?: return

        resultsContainer.removeAllViews()

        if (query.isEmpty()) {
            toast("اكتب كلمة للبحث عنها")
            return
        }

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
    }

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
                    database?.delete(
                        "words",
                        "rowid = ?",
                        arrayOf(id.toString())
                    )
                    searchWords()
                    updateWordCount()
                    toast("تم الحذف")
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

                val duplicate = database?.rawQuery(
                    """
                    SELECT 1 FROM words
                    WHERE english = ? COLLATE NOCASE
                    AND arabic = ?
                    AND rowid != ?
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(english, arabic, id.toString())
                )?.use { it.moveToFirst() } ?: false

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
            }
            .setNegativeButton("إلغاء", null)
            .show()
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        database?.close()
        database = null
        super.onDestroy()
    }
}
