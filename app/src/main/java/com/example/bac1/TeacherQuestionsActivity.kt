package com.example.bac1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TeacherQuestionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_questions)

        val container = findViewById<LinearLayout>(R.id.questionsListContainer)
        val noQuestionsText = findViewById<TextView>(R.id.noQuestionsText)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""

        FirebaseFirestore.getInstance().collection("interactive_questions")
            .whereEqualTo("teacherCode", teacherCode)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    noQuestionsText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                for (doc in result.documents) {
                    val docId = doc.id
                    val studentName = doc.getString("studentName") ?: "طالب"
                    val question = doc.getString("question") ?: ""
                    val reply = doc.getString("reply") ?: ""

                    val block = LayoutInflater.from(this).inflate(R.layout.item_teacher_question, container, false)
                    block.findViewById<TextView>(R.id.tvQuestionStudent).text = "❓ $studentName سأل:"
                    block.findViewById<TextView>(R.id.tvQuestionText).text = question

                    val etReply = block.findViewById<EditText>(R.id.etReplyText)
                    val btnSendReply = block.findViewById<Button>(R.id.btnSendReply)
                    val tvExistingReply = block.findViewById<TextView>(R.id.tvExistingReply)

                    if (reply.isNotEmpty()) {
                        tvExistingReply.visibility = View.VISIBLE
                        tvExistingReply.text = "✅ ردك: $reply"
                        etReply.setText(reply)
                    }

                    btnSendReply.setOnClickListener {
                        val replyText = etReply.text.toString().trim()
                        if (replyText.isEmpty()) return@setOnClickListener

                        FirebaseFirestore.getInstance().collection("interactive_questions")
                            .document(docId)
                            .update("reply", replyText)
                            .addOnSuccessListener {
                                tvExistingReply.visibility = View.VISIBLE
                                tvExistingReply.text = "✅ ردك: $replyText"
                            }
                    }

                    container.addView(block)
                }
            }
    }
}
