
package com.example.bac1

import android.os.Bundle
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class DictionaryAdminActivity : AppCompatActivity() {

    private var database: SQLiteDatabase? = null

    private lateinit var englishInput: EditText
    private lateinit var arabicInput: EditText
    private lateinit var countText: TextView
    private lateinit var addButton: Button

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }

        val title = TextView(this).apply {
            text = "📚 إدارة قاموس 1Bac"
            textSize = 23f
            gravity = Gravity.CENTER
        }

        val description = TextView(this).apply {
            text = "إضافة كلمات ومعانيها للقاموس"
            textSize = 15f
            gravity = Gravity.CENTER
        }

        countText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
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

        layout.addView(title)
        layout.addView(description)
        layout.addView(countText)
        layout.addView(englishInput)
        layout.addView(arabicInput)
        layout.addView(addButton)

        setContentView(layout)

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

        addButton.setOnClickListener {
            addWord()
        }
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
        )?.use {
            // التأكد من وجود الجدول والأعمدة
        }
    }

    private fun updateWordCount() {
        val cursor = database?.rawQuery(
            "SELECT COUNT(*) FROM words",
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                countText.text = "إجمالي الكلمات: ${it.getInt(0)}"
            }
        }
    }

    private fun addWord() {
        val english = englishInput.text.toString().trim()
        val arabic = arabicInput.text.toString().trim()

        if (english.isEmpty() || arabic.isEmpty()) {
            Toast.makeText(
                this,
                "اكتب الكلمة ومعناها أولًا",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val db = database ?: return

        try {
            val duplicate = db.rawQuery(
                """
                SELECT 1 FROM words
                WHERE english = ? COLLATE NOCASE
                AND arabic = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(english, arabic)
            )

            val alreadyExists = duplicate.use {
                it.moveToFirst()
            }

            if (alreadyExists) {
                Toast.makeText(
                    this,
                    "الكلمة بنفس المعنى موجودة بالفعل",
                    Toast.LENGTH_SHORT
                ).show()
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

                Toast.makeText(
                    this,
                    "تمت إضافة الكلمة بنجاح ✅",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "لم تتم إضافة الكلمة",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "حدث خطأ: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        database?.close()
        database = null
        super.onDestroy()
    }
}
