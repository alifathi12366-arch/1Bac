package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class StudyToolsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_tools)

        findViewById<View>(R.id.cardEnglish).setOnClickListener {
            startActivity(Intent(this, EnglishWordsActivity::class.java))
        }
        findViewById<View>(R.id.cardTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }
        findViewById<View>(R.id.cardFocus).setOnClickListener {
            startActivity(Intent(this, FocusTimerActivity::class.java))
        }
        findViewById<View>(R.id.cardLibrary).setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }
        findViewById<View>(R.id.cardBank).setOnClickListener {
            startActivity(Intent(this, StudentQuestionBankActivity::class.java))
        }
    }
}
