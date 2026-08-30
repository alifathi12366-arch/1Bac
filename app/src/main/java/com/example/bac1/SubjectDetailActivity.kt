package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SubjectDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subject_detail)

        val subjectName = intent.getStringExtra("subjectName") ?: ""
        val bookLink = intent.getStringExtra("bookLink") ?: ""
        val teacherLink = intent.getStringExtra("teacherLink") ?: ""

        val titleView = findViewById<TextView>(R.id.detailTitle)
        titleView.text = subjectName

        val bookButton = findViewById<Button>(R.id.openBookButton)
        bookButton.setOnClickListener {
            if (bookLink.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookLink))
                startActivity(intent)
            }
        }

        val teacherButton = findViewById<Button>(R.id.openTeacherButton)
        teacherButton.setOnClickListener {
            if (teacherLink.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(teacherLink))
                startActivity(intent)
            }
        }
    }
}










