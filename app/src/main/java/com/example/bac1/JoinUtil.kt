package com.example.bac1

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object JoinUtil {

    // معرّف الطالب (نفس اللي كان بيتستخدم قبل كده)
    fun identifier(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val phone = prefs.getString("student_phone", "") ?: ""
        if (phone.isNotEmpty()) return phone
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun docId(teacherCode: String, identifier: String) = "${teacherCode}_$identifier"

    fun normPhone(raw: String): String {
        val r = raw.trim().filter { it.isDigit() || it == '+' }
        return when {
            r.startsWith("+") -> r
            r.startsWith("0") -> "+20" + r.substring(1)
            else -> "+20$r"
        }
    }

    fun validPhone(n: String): Boolean = n.filter { it.isDigit() }.length in 11..13

    fun localPhone(p: String): String = if (p.startsWith("+20")) "0" + p.substring(3) else p

    // قبول طالب: بيدخل في users (القايمة اللي كل الشاشات بتقرأ منها)
        fun approve(
        db: FirebaseFirestore, code: String, identifier: String, studentName: String,
        fullName: String, phone: String, parentPhone: String, uid: String, done: (Boolean) -> Unit
    ) {
        if (identifier.isEmpty()) {
            done(false)
            return
        }
        val id = docId(code, identifier)
        val userRef = db.collection("users").document(id)
        val reqRef = db.collection("joinRequests").document(id)
        userRef.get()
            .addOnSuccessListener { ex ->
                val already = ex.exists()
                val data = hashMapOf<String, Any>(
                    "joined_teacher_code" to code, "identifier" to identifier,
                    "name" to studentName, "fullName" to fullName,
                    "phone" to phone, "parentPhone" to parentPhone,
                    "joinedAt" to System.currentTimeMillis()
                )
                if (uid.isNotEmpty()) data["uid"] = uid
                val batch = db.batch()
                batch.set(userRef, data, SetOptions.merge())
                batch.set(
                    reqRef,
                    hashMapOf<String, Any>("status" to "approved", "decidedAt" to System.currentTimeMillis()),
                    SetOptions.merge()
                )
                batch.commit()
                    .addOnSuccessListener {
                        if (!already) {
                            db.collection("teachers").document(code)
                                .update("studentsCount", FieldValue.increment(1))
                        }
                        done(true)
                    }
                    .addOnFailureListener { done(false) }
            }
            .addOnFailureListener { done(false) }
    }
        
    fun reject(ref: DocumentReference, done: (Boolean) -> Unit) = mark(ref, "rejected", done)

    fun block(ref: DocumentReference, done: (Boolean) -> Unit) = mark(ref, "blocked", done)

    private fun mark(ref: DocumentReference, status: String, done: (Boolean) -> Unit) {
        ref.set(
            hashMapOf<String, Any>("status" to status, "decidedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        )
            .addOnSuccessListener { done(true) }
            .addOnFailureListener { done(false) }
    }

    // إخراج طالب: يتحظر، ويتشال من القايمة ومن مجموعات المدرس
    fun removeStudent(
        db: FirebaseFirestore, code: String, teacherName: String,
        user: DocumentSnapshot, done: (Boolean) -> Unit
    ) {
        val id = user.id
        val identifier = user.getString("identifier") ?: id.removePrefix("${code}_")
        val name = user.getString("name") ?: ""
        val batch = db.batch()
        batch.set(
            db.collection("joinRequests").document(id),
            hashMapOf<String, Any>(
                "teacherCode" to code, "teacherName" to teacherName, "identifier" to identifier,
                "studentName" to name,
                "fullName" to (user.getString("fullName") ?: ""),
                "phone" to (user.getString("phone") ?: ""),
                "parentPhone" to (user.getString("parentPhone") ?: ""),
                "status" to "blocked", "decidedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )
        batch.delete(user.reference)
        batch.commit()
            .addOnSuccessListener {
                db.collection("teachers").document(code).update("studentsCount", FieldValue.increment(-1))
                if (name.isNotEmpty()) {
                    db.collection("teacherGroups").whereArrayContains("studentNames", name).get()
                        .addOnSuccessListener { gs ->
                            gs.documents.filter { it.getString("teacherCode") == code }.forEach {
                                it.reference.update("studentNames", FieldValue.arrayRemove(name))
                            }
                        }
                }
                done(true)
            }
            .addOnFailureListener { done(false) }
    }

    // لو الطالب اتشال من عند المدرس، مينفعش يفتح صفحته
    fun requireMember(act: AppCompatActivity, code: String) {
        if (code.isEmpty()) return
        FirebaseFirestore.getInstance().collection("users")
            .document(docId(code, identifier(act))).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    Toast.makeText(act, "إنت مش مسجّل مع الأستاذ ده", Toast.LENGTH_LONG).show()
                    act.finish()
                }
            }
    }

    // شريط "عندك طلبات انضمام" في شاشة المدرس
    fun showPending(act: AppCompatActivity, grid: LinearLayout, code: String) {
        val parent = grid.parent as? ViewGroup ?: return
        val d = act.resources.displayMetrics.density
        val existing = parent.findViewWithTag<TextView>("pendingBanner")
        val banner = existing ?: TextView(act).also { b ->
            b.tag = "pendingBanner"
            b.textSize = 14f
            b.setTypeface(null, Typeface.BOLD)
            b.setTextColor(Color.parseColor("#4E342E"))
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#FFE082"))
            bg.cornerRadius = 14 * d
            b.background = bg
            b.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
            b.layoutParams = lp
            b.visibility = View.GONE
            b.setOnClickListener { act.startActivity(Intent(act, StudentsManageActivity::class.java)) }
            parent.addView(b, parent.indexOfChild(grid))
        }
        FirebaseFirestore.getInstance().collection("joinRequests")
            .whereEqualTo("teacherCode", code).whereEqualTo("status", "pending")
            .count().get(AggregateSource.SERVER)
            .addOnSuccessListener { s ->
                val n = s.count
                if (n > 0) {
                    banner.text = "⏳ عندك $n طلب انضمام جديد، اضغط للمراجعة"
                    banner.visibility = View.VISIBLE
                } else {
                    banner.visibility = View.GONE
                }
            }
    }
}
