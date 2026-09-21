package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MyTeachersActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEACHER_CODE = "EXTRA_TEACHER_CODE"
        const val EXTRA_TEACHER_NAME = "EXTRA_TEACHER_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_teachers)

        val container = findViewById<LinearLayout>(R.id.myTeachersContainer)
        val noTeachersText = findViewById<TextView>(R.id.noTeachersText)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val joinedCodes = prefs.getStringSet("joined_teacher_codes", emptySet()) ?: emptySet()

        if (joinedCodes.isEmpty()) {
            noTeachersText.visibility = View.VISIBLE
            return
        }

        val db = FirebaseFirestore.getInstance()

        for (code in joinedCodes) {
            db.collection("teachers").document(code).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) return@addOnSuccessListener

                    val name = doc.getString("name") ?: "مدرس"
                    val subject = doc.getString("subject") ?: ""

                    val block = LayoutInflater.from(this).inflate(R.layout.item_my_teacher, container, false)
                    block.findViewById<TextView>(R.id.tvMyTeacherName).text = name
                    block.findViewById<TextView>(R.id.tvMyTeacherSubject).text = "مادة: $subject"

                    block.findViewById<Button>(R.id.btnOpenTeacherContent).setOnClickListener {
                        val intent = Intent(this, TeacherContentActivity::class.java)
                        intent.putExtra(EXTRA_TEACHER_CODE, code)
                        intent.putExtra(EXTRA_TEACHER_NAME, name)
                        startActivity(intent)
                    }

                    container.addView(block)
                }
        }
    }
}
