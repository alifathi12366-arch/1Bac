package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class JoinTeacherActivity : AppCompatActivity() {

    private class Info(
        val teacherCode: String, val teacherName: String, val id: String,
        val studentName: String, val full: String, val phone: String, val parent: String,
        val auto: Boolean, val paused: Boolean
    )

    private val db = FirebaseFirestore.getInstance()
    private lateinit var btn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun label(text: String, size: Float = 16f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    private fun edit(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        e.setTextColor(0xFF000000.toInt())
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        container.addView(label("🎓 الانضمام لمدرس", 22f))
        container.addView(label("اكتب بياناتك علشان المدرس يقبلك. البيانات دي بتظهر للمدرس بس.", 13f))

        container.addView(label("كود المدرس"))
        val etCode = edit("اكتب الكود اللي المدرس إداهولك")
        container.addView(etCode)

        container.addView(label("اسمك ثلاثي"))
        val etFull = edit("مثال: محمد أحمد علي")
        etFull.setText(prefs.getString("join_full_name", "") ?: "")
        container.addView(etFull)

        container.addView(label("رقمك"))
        val etPhone = edit("مثال: 01012345678", InputType.TYPE_CLASS_PHONE)
        val savedPhone = prefs.getString("join_phone", null) ?: prefs.getString("student_phone", "") ?: ""
        etPhone.setText(JoinUtil.localPhone(savedPhone))
        container.addView(etPhone)

        container.addView(label("رقم ولي الأمر"))
        val etParent = edit("مثال: 01212345678", InputType.TYPE_CLASS_PHONE)
        etParent.setText(JoinUtil.localPhone(prefs.getString("join_parent_phone", "") ?: ""))
        container.addView(etParent)

        btn = Button(this)
        btn.text = "📩 إرسال طلب الانضمام"
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        btn.layoutParams = lp
        container.addView(btn)

        btn.setOnClickListener {
            val code = etCode.text.toString().trim()
            val full = etFull.text.toString().trim().replace(Regex("\\s+"), " ")
            val phone = JoinUtil.normPhone(etPhone.text.toString())
            val parent = JoinUtil.normPhone(etParent.text.toString())
            when {
                code.isEmpty() -> toast("اكتب الكود الأول")
                full.split(" ").size < 3 -> toast("اكتب اسمك ثلاثي (3 كلمات على الأقل)")
                !JoinUtil.validPhone(phone) -> toast("رقمك غير صحيح")
                !JoinUtil.validPhone(parent) -> toast("رقم ولي الأمر غير صحيح")
                phone == parent -> toast("رقم ولي الأمر لازم يكون غير رقمك")
                                else -> {
                    btn.isEnabled = false
                    AuthGate.ensure { ok ->
                        if (ok) lookup(code, full, phone, parent)
                        else fail("تعذّر تسجيل الدخول الآمن، تحقق من الإنترنت وحاول تاني")
                    }
                }
            }
        }
    }

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        btn.isEnabled = true
    }

    private fun done(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MyTeachersActivity::class.java))
        finish()
    }

    private fun rememberTeacher(code: String) {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val s = HashSet(prefs.getStringSet("joined_teacher_codes", emptySet()) ?: emptySet())
        s.add(code)
        prefs.edit().putStringSet("joined_teacher_codes", s).apply()
    }

    private fun lookup(code: String, full: String, phone: String, parent: String) {
        db.collection("joinCodes").document(code).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    fail("الكود غير صحيح")
                } else {
                    prepare(doc.getString("teacherCode") ?: "", doc.getString("teacherName") ?: "", full, phone, parent)
                }
            }
            .addOnFailureListener { fail("حصل خطأ، تحقق من الإنترنت وحاول تاني") }
    }

    private fun prepare(tc: String, tn: String, full: String, phone: String, parent: String) {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("join_full_name", full)
            .putString("join_phone", phone)
            .putString("join_parent_phone", parent)
            .apply()
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = JoinUtil.identifier(this)
        db.collection("teachers").document(tc).get()
            .addOnSuccessListener { t ->
                evaluate(
                    Info(
                        tc, tn, id, studentName, full, phone, parent,
                        t.getBoolean("autoAccept") ?: false, t.getBoolean("joinPaused") ?: false
                    )
                )
            }
            .addOnFailureListener { fail("حصل خطأ، تحقق من الإنترنت وحاول تاني") }
    }

    private fun evaluate(info: Info) {
        val reqId = JoinUtil.docId(info.teacherCode, info.id)
        db.collection("joinRequests").document(reqId).get()
            .addOnSuccessListener { r -> handle(info, r.getString("status") ?: "") }
            .addOnFailureListener { fail("حصل خطأ، تحقق من الإنترنت وحاول تاني") }
    }

    private fun handle(info: Info, st: String) {
        when {
            st == "blocked" -> fail("مش مسموح لك تنضم لأستاذ ${info.teacherName}. كلّمه مباشرة.")
            st == "pending" -> done("طلبك لسه قيد المراجعة ⏳")
            st == "approved" -> {
                db.collection("users").document(JoinUtil.docId(info.teacherCode, info.id)).get()
                    .addOnSuccessListener { u ->
                        if (u.exists()) {
                            rememberTeacher(info.teacherCode)
                            done("إنت مسجّل مع أستاذ ${info.teacherName} بالفعل ✅")
                        } else {
                            send(info)
                        }
                    }
                    .addOnFailureListener { fail("حصل خطأ، تحقق من الإنترنت وحاول تاني") }
            }
            info.paused -> fail("الأستاذ موقّف استقبال الطلبات دلوقتي، جرّب بعدين")
            else -> send(info)
        }
    }

    private fun send(info: Info) {
        val reqId = JoinUtil.docId(info.teacherCode, info.id)
        db.collection("joinRequests").document(reqId).set(
            hashMapOf<String, Any>(
                "teacherCode" to info.teacherCode, "teacherName" to info.teacherName,
                "identifier" to info.id, "studentName" to info.studentName,
                "fullName" to info.full, "phone" to info.phone, "parentPhone" to info.parent,
                "uid" to AuthGate.uid(), "status" to "pending", "createdAt" to System.currentTimeMillis()
            )
        )
            .addOnSuccessListener {
                if (info.auto) {
                    JoinUtil.approve(
                    db, info.teacherCode, info.id, info.studentName, info.full, info.phone, info.parent, AuthGate.uid()
                    ) { ok ->
                        if (ok) {
                            rememberTeacher(info.teacherCode)
                            done("تم قبولك مع أستاذ ${info.teacherName} ✅")
                        } else {
                            done("تم إرسال طلبك ⏳")
                        }
                    }
                } else {
                    done("تم إرسال طلبك للأستاذ ${info.teacherName} ⏳ هتلاقي حالته في \"مدرسيني\"")
                }
            }
            .addOnFailureListener { fail("تعذّر إرسال الطلب، تحقق من الإنترنت وحاول تاني") }
    }
}
