package com.example.bac1

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MyQuestionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_questions)

        val container = findViewById<LinearLayout>(R.id.myQuestionsContainer)
        val noQuestionsText = findViewById<TextView>(R.id.noMyQuestionsText)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val teacherCode = intent.getStringExtra(MyTeachersActivity.EXTRA_TEACHER_CODE) ?: ""

        FirebaseFirestore.getInstance().collection("interactive_questions")
            .whereEqualTo("teacherCode", teacherCode)
            .whereEqualTo("studentName", studentName)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    noQuestionsText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                for (doc in result.documents) {
                    val question = doc.getString("question") ?: ""
                    val reply = doc.getString("reply") ?: ""

                    val row = TextView(this)
                    row.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                    row.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                    row.textSize = 14f
                    row.setPadding(24, 20, 24, 20)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = 14
                    row.layoutParams = params

                    row.text = if (reply.isNotEmpty())
                        "❓ سؤالك: $question\n\n✅ رد المدرس: $reply"
                    else
                        "❓ سؤالك: $question\n\n⏳ لسه المدرس ما ردش"

                    container.addView(row)
                }
            }
    }
}
