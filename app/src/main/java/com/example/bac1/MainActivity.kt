package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ربط التصميم مباشرة بطريقة أندرويد القياسية
        setContentView(R.layout.activity_main)

        // إعداد التنقل من خلال القائمة السفلية BottomNavigationView
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.layout.activity_main) // أو استخدام R.id.bottomNav
        
        val navView = findViewById<BottomNavigationView>(resources.getIdentifier("bottomNav", "id", packageName))
        
        navView?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // استبدل R.id.nav_add_exam بـ ID العنصر الموجود داخل قائمة menu/bottom_nav_menu
                resources.getIdentifier("nav_add_exam", "id", packageName) -> {
                    startActivity(Intent(this, AddExamActivity::class.java))
                    true
                }
                resources.getIdentifier("nav_student_exams", "id", packageName) -> {
                    startActivity(Intent(this, StudentExamsActivity::class.java))
                    true
                }
                resources.getIdentifier("nav_teacher_results", "id", packageName) -> {
                    startActivity(Intent(this, TeacherResultsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
}
