package com.example.bac1

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction

object ChallengeManager {

    const val START_POINTS = 3
    const val MAX_PLAYERS = 10
    private const val ROOM_MILLIS = 24L * 60 * 60 * 1000

    private fun db() = FirebaseFirestore.getInstance()

    private fun randomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..5).map { chars.random() }.joinToString("")
    }

    private fun fail(msg: String): Nothing =
        throw FirebaseFirestoreException(msg, FirebaseFirestoreException.Code.ABORTED)

    // رصيد نقاط التحدي (ينشأ بـ 3 نقاط أول مرة)
    fun getBalance(studentId: String, onResult: (Int) -> Unit) {
        val ref = db().collection("challengeWallets").document(studentId)
        ref.get().addOnSuccessListener { d ->
            if (d.exists()) {
                onResult((d.getLong("points") ?: 0L).toInt())
            } else {
                ref.set(hashMapOf("points" to START_POINTS))
                onResult(START_POINTS)
            }
        }.addOnFailureListener { onResult(-1) }
    }

    // إنشاء غرفة: يخصم نقطة ويرجّع الكود
    fun createRoom(
        studentId: String, studentName: String,
        onResult: (code: String?, error: String?) -> Unit
    ) {
        fun attempt(left: Int) {
            val code = randomCode()
            val walletRef = db().collection("challengeWallets").document(studentId)
            val roomRef = db().collection("rooms").document(code)
            db().runTransaction(Transaction.Function<String> { tx ->
                val w = tx.get(walletRef)
                val room = tx.get(roomRef)
                val bal = if (w.exists()) (w.getLong("points") ?: 0L).toInt() else START_POINTS
                if (bal < 1) fail("no_points")
                if (room.exists()) fail("collision")
                val now = System.currentTimeMillis()
                tx.set(walletRef, hashMapOf("points" to (bal - 1)))
                tx.set(roomRef, hashMapOf(
                    "code" to code,
                    "ownerId" to studentId,
                    "ownerName" to studentName,
                    "createdAt" to now,
                    "expiresAt" to now + ROOM_MILLIS,
                    "maxPlayers" to MAX_PLAYERS,
                    "players" to listOf(hashMapOf("id" to studentId, "name" to studentName)),
                    "playerIds" to listOf(studentId)
                ))
                code
            }).addOnSuccessListener { onResult(code, null) }
                .addOnFailureListener { e ->
                    val m = e.message ?: ""
                    when {
                        m.contains("no_points") -> onResult(null, "رصيدك من نقاط التحدي انتهى")
                        m.contains("collision") && left > 0 -> attempt(left - 1)
                        else -> onResult(null, "تعذّر إنشاء الغرفة، تحقق من الإنترنت")
                    }
                }
        }
        attempt(3)
    }

    // الدخول لغرفة بالكود (مجاني)
    fun joinRoom(
        code: String, studentId: String, studentName: String,
        onResult: (ok: Boolean, message: String) -> Unit
    ) {
        val roomRef = db().collection("rooms").document(code)
        db().runTransaction(Transaction.Function<Unit> { tx ->
            val r = tx.get(roomRef)
            if (!r.exists()) fail("not_found")
            val expires = r.getLong("expiresAt") ?: 0L
            if (System.currentTimeMillis() > expires) fail("expired")
            val ids = (r.get("playerIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            if (ids.contains(studentId)) return@Function
            val max = (r.getLong("maxPlayers") ?: MAX_PLAYERS.toLong()).toInt()
            if (ids.size >= max) fail("full")
            tx.update(
                roomRef,
                "players", FieldValue.arrayUnion(hashMapOf("id" to studentId, "name" to studentName)),
                "playerIds", FieldValue.arrayUnion(studentId)
            )
        }).addOnSuccessListener { onResult(true, "تم الدخول إلى الغرفة ✅") }
            .addOnFailureListener { e ->
                val m = e.message ?: ""
                onResult(false, when {
                    m.contains("not_found") -> "الكود غير صحيح"
                    m.contains("expired") -> "انتهت صلاحية هذه الغرفة"
                    m.contains("full") -> "الغرفة ممتلئة"
                    else -> "تعذّر الدخول، تحقق من الإنترنت"
                })
            }
    }

    // غرفي الصالحة (الأحدث أولًا)
    fun myRooms(studentId: String, onResult: (List<DocumentSnapshot>) -> Unit) {
        db().collection("rooms").whereArrayContains("playerIds", studentId).get()
            .addOnSuccessListener { res ->
                val now = System.currentTimeMillis()
                onResult(
                    res.documents
                        .filter { (it.getLong("expiresAt") ?: 0L) > now }
                        .sortedByDescending { it.getLong("createdAt") ?: 0L }
                )
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
