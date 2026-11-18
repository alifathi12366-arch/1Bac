package com.example.bac1

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BacApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthGate.ensure()
    }
}

object AuthGate {

    fun uid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // يسجّل دخول مجهول لو لسه مفيش حساب (المستخدم مش بيحس بيه)
    fun ensure(onReady: (Boolean) -> Unit = {}) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onReady(true)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(true) }
            .addOnFailureListener { onReady(false) }
    }
}

object TeacherAuth {

    // النتيجة: ok (اتربط أو مربوط من قبل)، other (مربوط بجهاز تاني)، missing (مفيش ملف للمدرس)، error
    fun claim(db: FirebaseFirestore, code: String, onResult: (String) -> Unit) {
        val uid = AuthGate.uid()
        if (uid.isEmpty() || code.isEmpty()) {
            onResult("error")
            return
        }
        val ref = db.collection("teachers").document(code)
        ref.get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    onResult("missing")
                    return@addOnSuccessListener
                }
                val owner = d.getString("ownerUid") ?: ""
                when {
                    owner.isEmpty() -> ref.update("ownerUid", uid)
                        .addOnSuccessListener { onResult("ok") }
                        .addOnFailureListener { onResult("error") }
                    owner == uid -> onResult("ok")
                    else -> onResult("other")
                }
            }
            .addOnFailureListener { onResult("error") }
    }
}
