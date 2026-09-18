package com.example.bac1

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TeacherMyContentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_my_content)

        val recyclerView = findViewById<RecyclerView>(R.id.myContentRecyclerView)
        val noContentText = findViewById<TextView>(R.id.noMyContentText)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""

        FirebaseFirestore.getInstance()
            .collection("teacherContent")
            .whereEqualTo("teacherCode", teacherCode)
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
