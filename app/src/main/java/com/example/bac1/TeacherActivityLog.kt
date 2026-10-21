package com.example.bac1

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object TeacherActivityLog {
    fun post(ctx: Context, text: String) {
        val prefs = ctx.getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val code = prefs.getString("teacher_code", "") ?: ""
        if (code.isEmpty()) return
        FirebaseFirestore.getInstance().collection("teacherActivity").document(code).set(hashMapOf(
            "teacherCode" to code,
            "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
            "text" to text,
            "updatedAt" to System.currentTimeMillis()
        ))
    }
}
