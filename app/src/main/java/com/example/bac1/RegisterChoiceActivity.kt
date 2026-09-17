package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class RegisterChoiceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_choice)

        findViewById<Button>(R.id.btnStudent).setOnClickListener {
            startActivity(Intent(this, StudentRegisterActivity::class.java))
        }
        findViewById<Button>(R.id.btnTeacher).setOnClickListener {
            startActivity(Intent(this, TeacherRegisterActivity::class.java))
        }
    }
}
