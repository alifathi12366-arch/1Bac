package com.example.bac1

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

object RoomChat {

    val PRESETS = listOf(
        "هخسرك 😂",
        "استعد! 🔥",
        "حظ سعيد 🍀",
        "كنت متوقعها 😎",
        "إنت لسه بتحل؟ 😏",
        "جولة ثانية؟ ⚔️"
    )

    const val MAX_PER_PLAYER = 10
    const val COOLDOWN_MS = 5000L

    private var lastSentMs = 0L

    private fun db() = FirebaseFirestore.getInstance()

    fun send(
        code: String, studentId: String, studentName: String, text: String,
        onResult: (ok: Boolean, message: String) -> Unit
    ) {
        if (text !in PRESETS) {
            onResult(false, "رسالة غير مسموحة")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSentMs < COOLDOWN_MS) {
            onResult(false, "انتظر ثواني قبل الرسالة التالية")
            return
        }
        db().collection("roomMessages")
            .whereEqualTo("code", code)
            .whereEqualTo("senderId", studentId)
            .get()
            .addOnSuccessListener { res ->
                if (res.size() >= MAX_PER_PLAYER) {
                    onResult(false, "وصلت للحد الأقصى من الرسائل في هذه الغرفة")
                    return@addOnSuccessListener
                }
                lastSentMs = now
                db().collection("roomMessages").add(hashMapOf(
                    "code" to code,
                    "senderId" to studentId,
                    "senderName" to studentName,
                    "text" to text,
                    "createdAt" to now
                )).addOnSuccessListener { onResult(true, "") }
                    .addOnFailureListener { onResult(false, "تعذّر الإرسال") }
            }
            .addOnFailureListener { onResult(false, "تعذّر الإرسال") }
    }

    // آخر 20 رسالة (الأحدث أولًا)
    fun latest(code: String, onResult: (List<DocumentSnapshot>) -> Unit) {
        db().collection("roomMessages").whereEqualTo("code", code).get()
            .addOnSuccessListener { res ->
                onResult(
                    res.documents
                        .sortedByDescending { it.getLong("createdAt") ?: 0L }
                        .take(20)
                )
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
