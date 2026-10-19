package com.example.bac1

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction

object ChallengeSettler {

    private fun fail(msg: String): Nothing =
        throw FirebaseFirestoreException(msg, FirebaseFirestoreException.Code.ABORTED)

    // يقفل الغرفة ويوزّع النقاط مرة واحدة فقط
    fun settle(code: String, onDone: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val roomRef = db.collection("rooms").document(code)

        db.runTransaction(Transaction.Function<Unit> { tx ->
            val r = tx.get(roomRef)
            if (!r.exists()) fail("not_found")
            if (r.getBoolean("settled") == true) return@Function

            val ids = (r.get("playerIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val expired = System.currentTimeMillis() > (r.getLong("expiresAt") ?: 0L)

            val results = ids.map { id ->
                id to tx.get(db.collection("roomResults").document("${code}_$id"))
            }
            val finished = results.filter {
                it.second.exists() && it.second.getBoolean("finished") == true
            }
            val allDone = finished.size == ids.size
            val ready = (allDone && ids.size >= 2) || expired
            if (!ready) fail("not_ready")

            var winners = emptyList<String>()
            var top = 0
            if (finished.size >= 2) {
                top = finished.maxOf { (it.second.getLong("score") ?: 0L).toInt() }
                if (top > 0) {
                    winners = finished
                        .filter { (it.second.getLong("score") ?: 0L).toInt() == top }
                        .map { it.first }
                }
            }
            val pts = if (winners.size == 1) 2 else 1

            // كل القراءات قبل الكتابات
            val wallets = winners.map { id ->
                id to tx.get(db.collection("challengeWallets").document(id))
            }
            for ((id, w) in wallets) {
                val bal = if (w.exists()) (w.getLong("points") ?: 0L).toInt()
                else ChallengeManager.START_POINTS
                tx.set(
                    db.collection("challengeWallets").document(id),
                    hashMapOf("points" to (bal + pts))
                )
            }

            tx.update(roomRef, mapOf<String, Any>(
                "settled" to true,
                "winnerIds" to winners,
                "tie" to (winners.size > 1),
                "topScore" to top,
                "pointsAwarded" to (if (winners.isEmpty()) 0 else pts),
                "settledAt" to System.currentTimeMillis()
            ))
        }).addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }
}
