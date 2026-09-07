package com.example.bac1

import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

object LeaderboardManager {

    private fun getCurrentMonthKey(): String {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        return "$year-$month"
    }

    fun submitScore(studentId: String, studentName: String, points: Int) {
        val db = FirebaseFirestore.getInstance()
        val monthKey = getCurrentMonthKey()
        val docId = "$monthKey-$studentId"

        val data = hashMapOf(
            "studentId" to studentId,
            "studentName" to studentName,
            "points" to points,
            "month" to monthKey
        )

        db.collection("leaderboard").document(docId).set(data)
    }

    fun getTopStudents(onResult: (List<Pair<String, Int>>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val monthKey = getCurrentMonthKey()

        db.collection("leaderboard")
            .whereEqualTo("month", monthKey)
            .orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { doc ->
                    val name = doc.getString("studentName") ?: return@mapNotNull null
                    val points = doc.getLong("points")?.toInt() ?: 0
                    Pair(name, points)
                }
                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }
}
