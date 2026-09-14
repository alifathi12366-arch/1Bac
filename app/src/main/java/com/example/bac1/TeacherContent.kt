package com.example.yourApp // ⚠️ غير اسم الباكيج هنا لاسم الباكيج بتاع تطبيقك

data class TeacherContent(
    val id: String = "",
    val teacherId: String = "",
    val title: String = "",
    val description: String = "",
    val youtubeUrl: String = "",
    val pdfUrl: String = "",
    val directVideoUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
