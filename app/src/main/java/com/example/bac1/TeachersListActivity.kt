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

        findViewById<android.widget.Button>(R.id.myTeacherContentButton).setOnClickListener {
            startActivity(android.content.Intent(this, TeacherContentActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.joinTeacherEntryButton).setOnClickListener {
            startActivity(android.content.Intent(this, JoinTeacherActivity::class.java))
        }

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
