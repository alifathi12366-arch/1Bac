package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تحميل تصميم الشاشة الرئيسية الأصلي الخاص بك
        val layoutRes = resources.getIdentifier("activity_main", "layout", packageName)
        if (layoutRes != 0) {
            setContentView(layoutRes)
        }

        // ربط الشاشات بأي عنصر ينقر عليه المستخدم بأمان
        setupNavigation()
    }

    private fun setupNavigation() {
        val btnAddExamId = resources.getIdentifier("btnAddExam", "id", packageName)
        if (btnAddExamId != 0) {
            findViewById<View>(btnAddExamId)?.setOnClickListener {
                startActivity(Intent(this, AddExamActivity::class.java))
            }
        }

        val btnStudentExamsId = resources.getIdentifier("btnStudentExams", "id", packageName)
        if (btnStudentExamsId != 0) {
            findViewById<View>(btnStudentExamsId)?.setOnClickListener {
                startActivity(Intent(this, StudentExamsActivity::class.java))
            }
        }

        val btnTeacherResultsId = resources.getIdentifier("btnTeacherResults", "id", packageName)
        if (btnTeacherResultsId != 0) {
            findViewById<View>(btnTeacherResultsId)?.setOnClickListener {
                startActivity(Intent(this, TeacherResultsActivity::class.java))
            }
        }
    }
}
