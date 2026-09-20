package com.example.bac1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TeacherMyContentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_my_content)

        val container = findViewById<LinearLayout>(R.id.myContentListContainer)
        val noContentText = findViewById<TextView>(R.id.noMyContentText)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""

        FirebaseFirestore.getInstance().collection("teacherContent")
            .whereEqualTo("teacherCode", teacherCode)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    noContentText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                for (doc in result.documents) {
                    val docId = doc.id
                    val text = doc.getString("text") ?: ""

                    val block = LayoutInflater.from(this).inflate(R.layout.item_my_content, container, false)
                    block.findViewById<TextView>(R.id.tvMyContentText).text = text

                    block.findViewById<Button>(R.id.btnDeleteContent).setOnClickListener {
                        FirebaseFirestore.getInstance().collection("teacherContent")
                            .document(docId)
                            .delete()
                            .addOnSuccessListener {
                                container.removeView(block)
                            }
                    }

                    container.addView(block)
                }
            }
    }
}
