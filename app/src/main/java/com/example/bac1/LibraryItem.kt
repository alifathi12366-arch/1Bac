package com.example.bac1

data class LibraryItem(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileType: String, // "image", "pdf", "video", "audio", "note"
    val subject: String,
    val noteText: String,
    val dateAdded: Long
)
