
package com.example.bac1

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DictionaryAdminActivity : AppCompatActivity() {

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

        val englishInput = EditText(this).apply {
            hint = "الكلمة الإنجليزية"
            singleLine = true
        }

        val arabicInput = EditText(this).apply {
            hint = "المعنى بالعربي"
        }

        val addButton = Button(this).apply {
            text = "إضافة الكلمة"
            isEnabled = false
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(englishInput)
        layout.addView(arabicInput)
        layout.addView(addButton)

        setContentView(layout)

        addButton.setOnClickListener {
            Toast.makeText(
                this,
                "هنربط الإضافة بقاعدة البيانات في الخطوة التالية",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
