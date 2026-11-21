package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class AccountActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val saveNameButton = findViewById<Button>(R.id.saveNameButton)
        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        val pointsText = findViewById<TextView>(R.id.pointsText)
        val lastLoginText = findViewById<TextView>(R.id.lastLoginText)
        val facebookButton = findViewById<Button>(R.id.facebookButton)

        var studentId = prefs.getString("student_id", "") ?: ""
        if (studentId.isEmpty()) {
            studentId = (1000..9999).random().toString()
            prefs.edit().putString("student_id", studentId).apply()
        }

        val savedName = prefs.getString("student_name", "") ?: ""
        if (savedName.isNotEmpty()) {
            nameInput.setText(savedName)
            nameInput.isEnabled = false
            saveNameButton.isEnabled = false
            saveNameButton.text = "الاسم محفوظ"
            welcomeText.text = "أهلاً بيك يا $savedName (كودك: #$studentId) 👋"
        }

        val points = PointsManager.getMonthPoints(prefs)
        pointsText.text = "نقاطك هذا الشهر: $points (الإجمالي: ${prefs.getInt("student_points", 0)})"

        val lastLogin = prefs.getString("last_login_date", "")
        if (!lastLogin.isNullOrEmpty()) {
            lastLoginText.text = "آخر دخول ليك: $lastLogin"
        }

        if (savedName.isNotEmpty()) {
            LeaderboardManager.submitScore(studentId, savedName, points)
        }

        saveNameButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                prefs.edit().putString("student_name", name).apply()
                welcomeText.text = "أهلاً بيك يا $name (كودك: #$studentId) 👋"
                nameInput.isEnabled = false
                saveNameButton.isEnabled = false
                saveNameButton.text = "الاسم محفوظ"
                LeaderboardManager.submitScore(studentId, name, points)
            }
        }

        // ضغطة طويلة مخفية للأدمن (لكودك فقط + رقم سري)
        welcomeText.setOnLongClickListener {
            if (studentId == "1089") {
                val pinInput = EditText(this)
                pinInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                android.app.AlertDialog.Builder(this)
                    .setTitle("الرقم السري")
                    .setView(pinInput)
                    .setPositiveButton("دخول") { _, _ ->
                        if (pinInput.text.toString() == "7391") {
                          val items = arrayOf(
    "➕ أسئلة اليوم",
    "⚔️ أسئلة تحدي الأصحاب",
    "📢 التقييمات الأسبوعية",
    "💡 الاقتراحات",
    "🆔 معرّف حسابي (للقواعد)",
    "🧹 تنضيف أرقام الطلاب القديمة",
    "📚 إدارة قاموس 1Bac"
)
                            android.app.AlertDialog.Builder(this)
                                .setTitle("لوحة الأدمن")
                                .setItems(items) { _, which ->
                                    when (which) {
    0 -> startActivity(
        Intent(this, AdminQuestionsActivity::class.java)
    )

    1 -> startActivity(
        Intent(this, AdminQuestionsActivity::class.java)
            .putExtra("collection", "friendChallenges")
    )

    2 -> startActivity(
        Intent(this, AdminWeeklyActivity::class.java)
    )

    3 -> startActivity(
        Intent(this, AdminSuggestionsActivity::class.java)
    )

    4 -> showMyUid()

    5 -> cleanOldPhones()

    6 -> startActivity(
        Intent(this, DictionaryAdminActivity::class.java)
    )
}
                                }
                                .show()
                        }
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            true
        }

        facebookButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/1F8T5LvFYz/"))
            startActivity(intent)
        }

        // تم حذف أزرار (هل أنت مدرس؟) و (كود التفعيل) من هنا تماماً بناءً على طلبك

        // 🎁 زرار دعوة الأصدقاء
        val inviteBtn = findViewById<Button>(R.id.leaderboardButton)
        inviteBtn.text = "🎁 دعوة الأصدقاء (اكسب نقاط)"
        inviteBtn.visibility = View.VISIBLE
        inviteBtn.setOnClickListener {
            val invitedCount = prefs.getInt("invited_friends_count", 0)
            val message = "ادع أصدقاءك لتحميل تطبيق 1Bac واكسب نقطة في المسابقة الشهرية لكل صديق!\n\n" +
                    "كود الدعوة الخاص بك: $studentId\n" +
                    "أصدقاؤك الذين انضموا: $invitedCount من 5\n\n" +
                    "اطلب من صديقك إدخال هذا الكود عند تسجيله لأول مرة في التطبيق."

            android.app.AlertDialog.Builder(this)
                .setTitle("دعوة الأصدقاء 🤝")
                .setMessage(message)
                .setPositiveButton("نسخ الكود") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Student Code", studentId)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "تم نسخ الكود بنجاح! ابعته لأصحابك 🚀", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إغلاق", null)
                .show()
        }

        // تم حذف زر الاقتراحات أو الشكوى البرمجي من هنا أيضاً
    }
        private fun showMyUid() {
        AuthGate.ensure { ok ->
            val uid = AuthGate.uid()
            if (!ok || uid.isEmpty()) {
                Toast.makeText(this, "تعذّر تسجيل الدخول: " + AuthGate.lastError, Toast.LENGTH_LONG).show()
                return@ensure
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("معرّف حسابك")
                .setMessage(uid + "\n\nانسخه وحطه في Firestore: config ← admins ← uids")
                .setPositiveButton("نسخ") { _, _ ->
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("uid", uid))
                    Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إغلاق", null)
                .show()
        }
    }

    // يمسح أرقام الموبايل القديمة من students (الأرقام الجديدة بتتحفظ في مكان خاص)
    private fun cleanOldPhones() {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        Toast.makeText(this, "جارٍ التنضيف...", Toast.LENGTH_SHORT).show()
        var total = 0
        fun step() {
            db.collection("students").whereGreaterThan("phone", "").limit(400).get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) {
                        Toast.makeText(this, "تم تنضيف $total طالب ✅", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    val batch = db.batch()
                    snap.documents.forEach {
                        batch.update(it.reference, "phone", com.google.firebase.firestore.FieldValue.delete())
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            total += snap.size()
                            step()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "فشل: " + it.message, Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "فشل: " + it.message, Toast.LENGTH_LONG).show()
                }
        }
        step()
    }
}
