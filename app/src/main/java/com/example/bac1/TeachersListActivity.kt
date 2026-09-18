package com.example.bac1

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class TeachersListActivity : AppCompatActivity() {

    private lateinit var teachersRecyclerView: RecyclerView
    private lateinit var myTeacherContentButton: Button
    private lateinit var joinTeacherEntryButton: Button

    private val teachersList = mutableListOf<TeacherModel>()
    private lateinit var adapter: TeacherAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teachers_list)

        teachersRecyclerView = findViewById(R.id.teachersRecyclerView)
        myTeacherContentButton = findViewById(R.id.myTeacherContentButton)
        joinTeacherEntryButton = findViewById(R.id.joinTeacherEntryButton)

        teachersRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TeacherAdapter(teachersList)
        teachersRecyclerView.adapter = adapter

        myTeacherContentButton.setOnClickListener {
            val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            val joined = prefs.getString("joined_teacher_code", "") ?: ""
            if (joined.isEmpty()) {
                Toast.makeText(this, "لسه مسجلتش مع أي مدرس", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, TeacherContentActivity::class.java))
            }
        }

        joinTeacherEntryButton.setOnClickListener {
            startActivity(Intent(this, JoinTeacherActivity::class.java))
        }

        fetchTeachersFromFirestore()
    }

    private fun fetchTeachersFromFirestore() {
        val db = FirebaseFirestore.getInstance()

        db.collection("teachers")
            .get()
            .addOnSuccessListener { documents ->
                teachersList.clear()
                for (doc in documents) {
                    val teacher = doc.toObject(TeacherModel::class.java)
                    teachersList.add(teacher)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "خطأ في جلب البيانات: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
