package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TeacherContentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_content)

        val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val joinedTeacherCode = prefs.getString("joined_teacher_code", "") ?: ""
        val joinedTeacherName = prefs.getString("joined_teacher_name", "") ?: ""

        val nameView = findViewById<android.widget.TextView>(R.id.contentTeacherName)
        val noContentText = findViewById<android.widget.TextView>(R.id.noContentText)
        val recyclerView = findViewById<RecyclerView>(R.id.contentRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (joinedTeacherCode.isEmpty()) {
            nameView.text = "لسه مسجلتش مع أي مدرس"
            noContentText.visibility = View.VISIBLE
            return
        }

        nameView.text = "أستاذ $joinedTeacherName"

        FirebaseFirestore.getInstance()
            .collection("teacherContent")
            .whereEqualTo("teacherCode", joinedTeacherCode)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val items = result.documents.map { doc ->
                    TeacherContentItem(
                        type = doc.getString("type") ?: "message",
                        text = doc.getString("text") ?: ""
                    )
                }
                if (items.isEmpty()) {
                    noContentText.visibility = View.VISIBLE
                } else {
                    recyclerView.adapter = TeacherContentAdapter(items)
                }
            }
    }
}
