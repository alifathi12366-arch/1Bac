package com.example.bac1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class TeachersListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teachers_list)

        val recyclerView = findViewById<RecyclerView>(R.id.teachersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        FirebaseFirestore.getInstance()
            .collection("teachers")
            .get()
            .addOnSuccessListener { result ->
                val teachers = result.documents.map { doc ->
                    TeacherItem(
                        name = doc.getString("name") ?: "",
                        subject = doc.getString("subject") ?: "",
                        phone = doc.getString("phone") ?: ""
                    )
                }
                recyclerView.adapter = TeacherAdapter(teachers)
            }
    }
}
