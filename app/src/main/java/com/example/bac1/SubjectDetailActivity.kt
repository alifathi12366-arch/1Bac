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
        val subjectKey = intent.getStringExtra("subjectKey") ?: ""
        val bookLink = intent.getStringExtra("bookLink") ?: ""
        val bookLink2 = intent.getStringExtra("bookLink2") ?: ""
        val bookLink3 = intent.getStringExtra("bookLink3") ?: ""
        val teacherLink = intent.getStringExtra("teacherLink") ?: ""

        val bookLabel1 = intent.getStringExtra("bookLabel1") ?: "فتح كتاب المادة"
        val bookLabel2 = intent.getStringExtra("bookLabel2") ?: "فتح كتاب المادة"
        val bookLabel3 = intent.getStringExtra("bookLabel3") ?: "فتح كتاب المادة"

        val titleView = findViewById<TextView>(R.id.detailTitle)
        titleView.text = subjectName

        val bookButton = findViewById<Button>(R.id.openBookButton)
        val bookButton2 = findViewById<Button>(R.id.openBookButton2)
        val bookButton3 = findViewById<Button>(R.id.openBookButton3)
        val teacherButton = findViewById<Button>(R.id.openTeacherButton)
        val lessonsButton = findViewById<Button>(R.id.lessonsButton)

        if (bookLink.isNotEmpty()) {
            bookButton.text = bookLabel1
            bookButton.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bookLink)))
            }
        } else {
            bookButton.visibility = android.view.View.GONE
        }

        if (bookLink2.isNotEmpty()) {
            bookButton2.text = bookLabel2
            bookButton2.visibility = android.view.View.VISIBLE
            bookButton2.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bookLink2)))
            }
        }

        if (bookLink3.isNotEmpty()) {
            bookButton3.text = bookLabel3
            bookButton3.visibility = android.view.View.VISIBLE
            bookButton3.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bookLink3)))
            }
        }

        if (teacherLink.isNotEmpty()) {
            teacherButton.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(teacherLink)))
            }
        } else {
            teacherButton.visibility = android.view.View.GONE
        }

        if (subjectKey.isNotEmpty()) {
            lessonsButton.setOnClickListener {
                val intent = Intent(this, LessonsActivity::class.java)
                intent.putExtra("subjectKey", subjectKey)
                intent.putExtra("subjectName", subjectName)
                startActivity(intent)
            }
        } else {
            lessonsButton.visibility = android.view.View.GONE
        }
    }
}
