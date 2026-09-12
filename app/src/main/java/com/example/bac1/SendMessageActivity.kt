package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class SendMessageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_message)

        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<Button>(R.id.sendButton)

        val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""
        val teacherName = prefs.getString("teacher_name", "") ?: ""

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()

            if (messageText.isEmpty()) {
                Toast.makeText(this, "اكتب الرسالة الأول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val messageId = UUID.randomUUID().toString()
            val data = hashMapOf(
                "teacherCode" to teacherCode,
                "teacherName" to teacherName,
                "type" to "message",
                "text" to messageText,
                "createdAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("teacherContent")
                .document(messageId)
                .set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "تم إرسال الرسالة ✅", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
