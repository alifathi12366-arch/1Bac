package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layoutRes = resources.getIdentifier("activity_main", "layout", packageName)
        if (layoutRes != 0) setContentView(layoutRes)

        // ربط صريح ومباشر بدون استدعاءات مجهولة
        val btnAddExamId = resources.getIdentifier("btnAddExam", "id", packageName)
        val btnStudentExamsId = resources.getIdentifier("btnStudentExams", "id", packageName)
        val btnTeacherResultsId = resources.getIdentifier("btnTeacherResults", "id", packageName)

        if (btnAddExamId != 0) {
            findViewById<Button>(btnAddExamId)?.setOnClickListener {
                startActivity(Intent(this, AddExamActivity::class.java))
            }
        }

        if (btnStudentExamsId != 0) {
            findViewById<Button>(btnStudentExamsId)?.setOnClickListener {
                startActivity(Intent(this, StudentExamsActivity::class.java))
            }
        }

        if (btnTeacherResultsId != 0) {
            findViewById<Button>(btnTeacherResultsId)?.setOnClickListener {
                startActivity(Intent(this, TeacherResultsActivity::class.java))
            }
        }
    }
}
