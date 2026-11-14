package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class TeacherContentActivity : AppCompatActivity() {

    private var teacherCode = ""
    private var teacherName = ""
    private lateinit var gridContainer: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_hub)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.hubRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        teacherCode = intent.getStringExtra(MyTeachersActivity.EXTRA_TEACHER_CODE) ?: ""
        teacherName = intent.getStringExtra(MyTeachersActivity.EXTRA_TEACHER_NAME) ?: ""

        val nameView = findViewById<TextView>(R.id.contentTeacherName)
        gridContainer = findViewById(R.id.gridContainer)

        if (teacherCode.isEmpty()) {
            nameView.text = "حدث خطأ، ارجع واختار المدرس تاني"
            return
        }

        nameView.text = "أستاذ $teacherName"
        addTeacherCard(nameView, teacherCode)
        setupSessionAttendance(findViewById<Button>(R.id.btnMarkAttendance))
        buildCards()
    }

    // ---------- الكروت ----------
    private fun buildCards() {
        var row = CardKit.newRow(this, gridContainer)
        CardKit.addCard(
            this, row, "المحتوى", "دروس وفيديوهات المدرس", "📚",
            "#E7E2FE", "#D6CEFB", "#4B3FD1"
        ) {
            startActivity(
                Intent(this, TeacherLessonsActivity::class.java)
                    .putExtra(MyTeachersActivity.EXTRA_TEACHER_CODE, teacherCode)
                    .putExtra(MyTeachersActivity.EXTRA_TEACHER_NAME, teacherName)
            )
        }
        CardKit.addCard(
            this, row, "الألعاب", "العب وتنافس مع زمايلك", "🎮",
            "#D2F6DE", "#B9EFCD", "#2F7BD8"
        ) {
            startActivity(
                Intent(this, GamesHubActivity::class.java)
                    .putExtra("teacherCode", teacherCode)
                    .putExtra("teacherName", teacherName)
            )
        }

        row = CardKit.newRow(this, gridContainer)
        CardKit.addCard(
            this, row, "امتحانات أستاذي", "امتحانات بمدة وموعد", "📝",
            "#E3D8FE", "#D0BCFC", "#6A3CD6"
        ) {
            startActivity(
                Intent(this, StudentExamsActivity::class.java)
                    .putExtra(MyTeachersActivity.EXTRA_TEACHER_CODE, teacherCode)
            )
        }
        CardKit.addCard(
            this, row, "تحديات المدرس", "تحدى وكسب جوائز", "🎯",
            "#FFE6CC", "#FFD2A8", "#E65100"
        ) {
            startActivity(
                Intent(this, TeacherChallengesActivity::class.java)
                    .putExtra("teacherCode", teacherCode)
            )
        }

        row = CardKit.newRow(this, gridContainer)
        CardKit.addCard(
            this, row, "المراجعات", "مراجعات قبل الامتحان", "📘",
            "#D3E8FF", "#BEDDFD", "#2F6BEA"
        ) {
            startActivity(
                Intent(this, ReviewListActivity::class.java)
                    .putExtra("teacherCode", teacherCode)
            )
        }
        CardKit.addCard(
            this, row, "أخطائي", "راجع أسئلتك الغلط", "❌",
            "#FFD9EE", "#FDC4E6", "#E5365A"
        ) {
            startActivity(
                Intent(this, MyMistakesActivity::class.java)
                    .putExtra(MyTeachersActivity.EXTRA_TEACHER_CODE, teacherCode)
            )
        }

        row = CardKit.newRow(this, gridContainer)
        CardKit.addCard(
            this, row, "اسأل المستر", "اكتب سؤالك عن الدرس", "💬",
            "#CDF2E5", "#BDEBE3", "#1FAE7C"
        ) { showAskDialog() }
        CardKit.addCard(
            this, row, "ردود المدرس", "أسئلتك وإجاباتها", "📨",
            "#FDE8DC", "#FDD5BD", "#E5413A"
        ) {
            startActivity(
                Intent(this, MyQuestionsActivity::class.java)
                    .putExtra(MyTeachersActivity.EXTRA_TEACHER_CODE, teacherCode)
            )
        }

        row = CardKit.newRow(this, gridContainer)
        CardKit.addCard(
            this, row, "قيّم أستاذك", "رأيك يفرق", "⭐",
            "#FCE9C8", "#FDDA96", "#F08A24"
        ) { showRatingDialog() }
    }

    // ---------- اسأل المستر ----------
    private fun showAskDialog() {
        val et = EditText(this)
        et.hint = "اكتب سؤالك للمدرس..."
        et.minLines = 3
        et.gravity = Gravity.TOP or Gravity.START
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, FrameLayout.LayoutParams(-1, -2))

        AlertDialog.Builder(this)
            .setTitle("اسأل المستر 💬")
            .setView(box)
            .setPositiveButton("إرسال") { _, _ ->
                val q = et.text.toString().trim()
                if (q.isEmpty()) {
                    Toast.makeText(this, "اكتب سؤالك الأول", Toast.LENGTH_SHORT).show()
                } else {
                    sendStudentQuestion(teacherCode, q)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------- التقييم ----------
    private fun showRatingDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(16), dp(20), 0)
        val bar = RatingBar(this)
        bar.numStars = 5
        bar.stepSize = 1f
        box.addView(bar, LinearLayout.LayoutParams(-2, -2))

        FirebaseFirestore.getInstance().collection("ratings").document(ratingDocId(teacherCode)).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) bar.rating = (doc.getDouble("stars") ?: 0.0).toFloat()
            }

        AlertDialog.Builder(this)
            .setTitle("قيّم أستاذك ⭐")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val stars = bar.rating.toDouble()
                if (stars <= 0.0) {
                    Toast.makeText(this, "اختار نجوم الأول", Toast.LENGTH_SHORT).show()
                } else {
                    submitRating(teacherCode, stars)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun ratingDocId(teacherCode: String): String {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        return "${teacherCode}_$studentName"
    }

    private fun submitRating(teacherCode: String, newStars: Double) {
        val db = FirebaseFirestore.getInstance()
        val ratingRef = db.collection("ratings").document(ratingDocId(teacherCode))
        val teacherRef = db.collection("teachers").document(teacherCode)

        ratingRef.get().addOnSuccessListener { ratingDoc ->
            val oldStars = if (ratingDoc.exists()) ratingDoc.getDouble("stars") else null

            db.runTransaction { transaction ->
                val teacherSnap = transaction.get(teacherRef)
                val currentAvg = teacherSnap.getDouble("rating") ?: 0.0
                val currentCount = (teacherSnap.getLong("ratingCount") ?: 0L).toInt()

                val newAvg: Double
                val newCount: Int

                if (oldStars == null) {
                    newCount = currentCount + 1
                    newAvg = ((currentAvg * currentCount) + newStars) / newCount
                } else {
                    newCount = currentCount
                    newAvg = if (newCount > 0)
                        ((currentAvg * currentCount) - oldStars + newStars) / newCount
                    else newStars
                }

                transaction.update(teacherRef, "rating", newAvg)
                transaction.update(teacherRef, "ratingCount", newCount)
                transaction.set(ratingRef, hashMapOf("stars" to newStars, "teacherCode" to teacherCode))
            }.addOnSuccessListener {
                Toast.makeText(this, "تم حفظ تقييمك، شكرًا لك 🌟", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "فشل حفظ التقييم: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- كارت بيانات المدرس ----------
    private fun addTeacherCard(anchor: TextView, code: String) {
        FirebaseFirestore.getInstance().collection("teachers").document(code).get()
            .addOnSuccessListener { d ->
                val photo = d.getString("photoUrl") ?: ""
                val subject = d.getString("profileSubject") ?: ""
                val bio = d.getString("bio") ?: ""
                val phone = d.getString("whatsapp") ?: ""
                if (photo.isEmpty() && subject.isEmpty() && bio.isEmpty() && phone.isEmpty()) return@addOnSuccessListener

                val parent = anchor.parent as? ViewGroup ?: return@addOnSuccessListener
                val box = LinearLayout(this)
                box.orientation = LinearLayout.VERTICAL
                box.gravity = Gravity.CENTER_HORIZONTAL
                box.setPadding(dp(12), dp(4), dp(12), dp(12))

                if (photo.startsWith("http")) {
                    val img = android.widget.ImageView(this)
                    box.addView(img, LinearLayout.LayoutParams(dp(110), dp(110)))
                    com.bumptech.glide.Glide.with(this).load(photo).circleCrop().into(img)
                }
                if (subject.isNotEmpty()) {
                    val t = TextView(this)
                    t.text = "مادة: $subject"
                    t.textSize = 15f
                    t.setTextColor(0xFF241B4E.toInt())
                    box.addView(t)
                }
                if (bio.isNotEmpty()) {
                    val t = TextView(this)
                    t.text = bio
                    t.textSize = 13f
                    t.setTextColor(0xFF5E5A7E.toInt())
                    t.gravity = Gravity.CENTER
                    box.addView(t)
                }
                if (phone.isNotEmpty()) {
                    val b = Button(this)
                    b.text = "💬 تواصل واتساب"
                    b.setOnClickListener {
                        var digits = phone.filter { it.isDigit() }
                        if (digits.startsWith("0")) digits = "20" + digits.substring(1)
                        startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$digits"))
                        )
                    }
                    box.addView(b)
                }
                parent.addView(box, parent.indexOfChild(anchor) + 1)
            }
    }

    // ---------- تسجيل حضور الحصة (بيظهر بس لو فيه حصة دلوقتي) ----------
    private fun setupSessionAttendance(btn: Button) {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val name = prefs.getString("student_name", "طالب") ?: "طالب"
        btn.visibility = View.GONE
        val db = FirebaseFirestore.getInstance()
        db.collection("teacherGroups").whereArrayContains("studentNames", name).get()
            .addOnSuccessListener { res ->
                val cal = java.util.Calendar.getInstance()
                val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                val today = cal.get(java.util.Calendar.DAY_OF_WEEK)
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                for (g in res.documents) {
                    if (g.getString("teacherCode") != teacherCode) continue
                    val dur = (g.getLong("durationMin") ?: 90L).toInt()
                    val gname = g.getString("name") ?: ""
                    val slots = g.get("slots") as? List<*> ?: continue
                    for (s in slots) {
                        val m = s as? Map<*, *> ?: continue
                        val day = (m["day"] as? Number)?.toInt() ?: continue
                        val time = m["time"] as? String ?: continue
                        val start = try {
                            val p = time.split(":")
                            p[0].toInt() * 60 + p[1].toInt()
                        } catch (e: Exception) { continue }
                        if (day == today && nowMin in (start - 15)..(start + dur)) {
                            val safe = name.replace("/", "-")
                            val ref = db.collection("attendanceSessions")
                                .document("${teacherCode}_${g.id}_${dateStr}_${time}_$safe")
                            ref.get().addOnSuccessListener { d ->
                                btn.visibility = View.VISIBLE
                                if (d.exists()) {
                                    btn.isEnabled = false
                                    btn.text = "✅ تم تسجيل حضورك لحصة $gname"
                                } else {
                                    btn.isEnabled = true
                                    btn.text = "✋ سجّل حضورك لحصة $gname"
                                    btn.setOnClickListener {
                                        ref.set(hashMapOf(
                                            "teacherCode" to teacherCode,
                                            "groupId" to g.id,
                                            "groupName" to gname,
                                            "studentName" to name,
                                            "date" to dateStr,
                                            "slot" to time,
                                            "createdAt" to System.currentTimeMillis()
                                        )).addOnSuccessListener {
                                            btn.isEnabled = false
                                            btn.text = "✅ تم تسجيل حضورك لحصة $gname"
                                            Toast.makeText(this, "تم تسجيل حضورك 👍", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            return@addOnSuccessListener
                        }
                    }
                }
            }
    }

    private fun sendStudentQuestion(teacherCode: String, questionText: String) {
        val db = FirebaseFirestore.getInstance()
        val sharedPref = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = sharedPref.getString("student_name", "طالب") ?: "طالب"

        val questionData = hashMapOf(
            "studentName" to studentName,
            "teacherCode" to teacherCode,
            "question" to questionText,
            "reply" to "",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("interactive_questions")
            .add(questionData)
            .addOnSuccessListener {
                Toast.makeText(this, "تم إرسال سؤالك للمدرس بنجاح 📩", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "فشل إرسال السؤال: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
