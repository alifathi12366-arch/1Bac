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
        val docRef = db.collection("leaderboard").document(docId)

        docRef.get().addOnSuccessListener { snapshot ->
            val oldPoints = snapshot.getLong("points")?.toInt() ?: -1
            val oldTimestamp = snapshot.getLong("reachedAt")

            val reachedAt = if (points != oldPoints || oldTimestamp == null) {
                System.currentTimeMillis()
            } else {
                oldTimestamp
            }

            val data = hashMapOf(
                "studentId" to studentId,
                "studentName" to studentName,
                "points" to points,
                "month" to monthKey,
                "reachedAt" to reachedAt
            )

            docRef.set(data)
        }.addOnFailureListener {
            val data = hashMapOf(
                "studentId" to studentId,
                "studentName" to studentName,
                "points" to points,
                "month" to monthKey,
                "reachedAt" to System.currentTimeMillis()
            )
            docRef.set(data)
        }
    }

    fun getTopStudents(onResult: (List<Pair<String, Int>>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val monthKey = getCurrentMonthKey()

        db.collection("leaderboard")
            .whereEqualTo("month", monthKey)
            .orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { doc ->
                    val rawName = doc.getString("studentName") ?: return@mapNotNull null
                    val id = doc.getString("studentId") ?: ""
                    val name = "$rawName #$id"
                    val points = doc.getLong("points")?.toInt() ?: 0
                    val reachedAt = doc.getLong("reachedAt") ?: Long.MAX_VALUE
                    Triple(name, points, reachedAt)
                }
                val sorted = list.sortedWith(
                    compareByDescending<Triple<String, Int, Long>> { it.second }
                        .thenBy { it.third }
                ).take(10).map { Pair(it.first, it.second) }
                onResult(sorted)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun getHomeSummary(
        myKey: String,
        onResult: (top: List<String>, myRank: Int, myPoints: Int) -> Unit
    ) {
        FirebaseFirestore.getInstance().collection("leaderboard")
            .whereEqualTo("month", getCurrentMonthKey())
            .orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { result ->
                val rows = result.documents.mapNotNull { doc ->
                    val name = doc.getString("studentName") ?: return@mapNotNull null
                    val id = doc.getString("studentId") ?: ""
                    val pts = doc.getLong("points")?.toInt() ?: 0
                    val at = doc.getLong("reachedAt") ?: Long.MAX_VALUE
                    listOf(name, id, pts, at)
                }.sortedWith(
                    compareByDescending<List<Any>> { it[2] as Int }
                        .thenBy { it[3] as Long }
                )
                val top = rows.take(3).map { "${it[0]} #${it[1]} · ${it[2]} نقطة" }
                val idx = rows.indexOfFirst { it[1] == myKey }
                val pts = if (idx >= 0) rows[idx][2] as Int else 0
                onResult(top, if (idx >= 0) idx + 1 else -1, pts)
            }
            .addOnFailureListener { onResult(emptyList(), -1, 0) }
    }
}
