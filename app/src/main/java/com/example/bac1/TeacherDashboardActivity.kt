package com.example.bac1

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class TeacherDashboardActivity : AppCompatActivity() {

    // زوّد الرقم ده مع كل نسخة جديدة للمدرسين
    private val currentTeacherVersion = 3

    private lateinit var sheet: Bitmap
    private lateinit var grid: LinearLayout
    private lateinit var tvWelcome: TextView
    private lateinit var tvSubject: TextView
    private lateinit var tvJoinCode: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var tvStudents: TextView
    private lateinit var tvAttendance: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvRatingLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun spr(x: Int, y: Int, w: Int, h: Int): Bitmap = Bitmap.createBitmap(sheet, x, y, w, h)

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_teacher_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.teacherRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        checkTeacherUpdate()
        secureTeacher()

        val opts = BitmapFactory.Options()
        opts.inScaled = false
        sheet = BitmapFactory.decodeResource(resources, R.drawable.home_sprites, opts)

        findViewById<ImageView>(R.id.ivLogo).setImageBitmap(spr(299, 325, 270, 193))

        tvWelcome = findViewById(R.id.tvWelcome)
        tvSubject = findViewById(R.id.tvSubject)
        tvJoinCode = findViewById(R.id.tvJoinCode)
        tvExpiry = findViewById(R.id.tvExpiry)
        tvStudents = findViewById(R.id.tvStatStudents)
        tvAttendance = findViewById(R.id.tvStatAttendance)
        tvRating = findViewById(R.id.tvStatRating)
        tvRatingLabel = findViewById(R.id.tvRatingLabel)

        // خلفيات الكروت
        val wbg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#7A33E0"), Color.parseColor("#4F1AA0"))
        )
        wbg.cornerRadius = dp(22).toFloat()
        findViewById<View>(R.id.welcomeCard).background = wbg
        findViewById<View>(R.id.welcomeCard).elevation = dp(3).toFloat()

        val sbg = GradientDrawable()
        sbg.setColor(Color.WHITE)
        sbg.cornerRadius = dp(22).toFloat()
        sbg.setStroke(dp(1), Color.parseColor("#DDD7FA"))
        findViewById<View>(R.id.statsCard).background = sbg
        findViewById<View>(R.id.statsCard).elevation = dp(2).toFloat()

        val cbg = GradientDrawable()
        cbg.setColor(Color.parseColor("#33FFFFFF"))
        cbg.cornerRadius = dp(14).toFloat()
        tvJoinCode.background = cbg
        tvJoinCode.setOnClickListener { open(SetJoinCodeActivity::class.java) }

        val ebg = GradientDrawable()
        ebg.setColor(Color.parseColor("#FFE0B2"))
        ebg.cornerRadius = dp(14).toFloat()
        tvExpiry.background = ebg

        grid = findViewById(R.id.gridContainer)
        buildGrid()
    }

    override fun onResume() {
        super.onResume()
        refreshInfo()
    }

    // ---------- بيانات المدرس والإحصائيات ----------
    private fun refreshInfo() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val name = prefs.getString("teacher_name", "") ?: ""
        val subject = prefs.getString("teacher_subject", "") ?: ""
        val code = prefs.getString("teacher_code", "") ?: ""
        val join = prefs.getString("teacher_join_code", "") ?: ""
        val expired = prefs.getBoolean("teacher_expired", false)
        val expiringSoon = prefs.getBoolean("teacher_expiring_soon", false)

        tvWelcome.text = "مرحباً أستاذ $name 👋"
        tvSubject.text = if (subject.isNotEmpty()) "مادة: $subject" else "معاً نصنع فرقاً في تعليم أولى ثانوي"
        tvJoinCode.text = if (join.isNotEmpty()) "🔑 كود طلابك: $join  ✏️"
        else "🔑 لسه محددتش كود لطلابك، اضغط للتحديد"

        when {
            expired -> {
                tvExpiry.text = "⚠️ اشتراكك انتهى، تواصل معانا لتجديده"
                tvExpiry.visibility = View.VISIBLE
            }
            expiringSoon -> {
                tvExpiry.text = "⚠️ اشتراكك هينتهي قريبًا، جدد عشان طلابك يفضلوا شايفين المحتوى"
                tvExpiry.visibility = View.VISIBLE
            }
            else -> tvExpiry.visibility = View.GONE
        }

        if (code.isNotEmpty()) loadStats(code)
        if (code.isNotEmpty()) JoinUtil.showPending(this, grid, code)
    }

    private fun fmt(n: Long): String = when {
        n >= 1_000_000L -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
        n >= 1000L -> String.format(Locale.US, "%.1fK", n / 1000.0).replace(".0K", "K")
        else -> n.toString()
    }

    private fun loadStats(code: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("users").whereEqualTo("joined_teacher_code", code)
            .count().get(AggregateSource.SERVER)
            .addOnSuccessListener { tvStudents.text = fmt(it.count) }
            .addOnFailureListener { tvStudents.text = "0" }

        var total = 0L
        var pending = 2
        for (col in listOf("attendance", "attendanceSessions")) {
            db.collection(col).whereEqualTo("teacherCode", code)
                .count().get(AggregateSource.SERVER)
                .addOnSuccessListener {
                    total += it.count
                    pending -= 1
                    if (pending == 0) tvAttendance.text = fmt(total)
                }
                .addOnFailureListener {
                    pending -= 1
                    if (pending == 0) tvAttendance.text = fmt(total)
                }
        }

        db.collection("teachers").document(code).get()
            .addOnSuccessListener { doc ->
                val rating = doc.getDouble("rating") ?: 0.0
                val count = (doc.getLong("ratingCount") ?: 0L).toInt()
                if (count > 0) {
                    tvRating.text = String.format(Locale.US, "%.1f", rating)
                    tvRatingLabel.text = "متوسط تقييمك ($count)"
                } else {
                    tvRating.text = "—"
                    tvRatingLabel.text = "لسه مفيش تقييمات"
                }
            }
    }

    // ---------- الكروت ----------
    private fun newRow(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.isBaselineAligned = false
        grid.addView(r, LinearLayout.LayoutParams(-1, -2))
        return r
    }

    private fun buildGrid() {
        var row = newRow()
        addCard(
            row, "الامتحانات", "أضف امتحاناً جديداً",
            spr(427, 551, 152, 144), null, "#E3D8FE", "#D0BCFC", "#6A3CD6"
        ) { open(AddExamActivity::class.java) }
        addCard(
            row, "نتائج الامتحانات", "درجات طلابك وتصحيح المقالي",
            spr(0, 715, 136, 131), null, "#D3E8FF", "#BEDDFD", "#2F6BEA"
        ) { open(TeacherResultsActivity::class.java) }

        row = newRow()
        addCard(
            row, "رسائل لطلابي", "اكتب رسالة تظهر لطلابك ",
            null, "📢", "#CDF2E5", "#BDEBE3", "#1FAE7C"
        ) { open(TeacherPublishActivity::class.java) }
        addCard(
            row, "رسائلي", "المرسلة لطلابي ",
            spr(276, 551, 147, 145), null, "#E7E2FE", "#D6CEFB", "#4B3FD1"
        ) { open(TeacherMyContentActivity::class.java) }

        row = newRow()
        addCard(
            row, "التحديات", "أنشئ تحدي جديد لطلابك",
            spr(124, 551, 148, 157), null, "#FFD9EE", "#FDC4E6", "#E5365A"
        ) { open(AddTeacherChallengeActivity::class.java) }
        addCard(
            row, "تحدياتي ونتائجها", "تابع نتائج التحديات",
            spr(573, 325, 156, 165), null, "#FCE9C8", "#FDDA96", "#F08A24"
        ) { open(TeacherChallengesActivity::class.java) }

        row = newRow()
        addCard(
            row, "الألعاب", "دوري الأبطال وعجلة الحظ والمزيد",
            null, "🎮", "#D2F6DE", "#B9EFCD", "#2F7BD8"
        ) { open(GamesHubActivity::class.java) }
        addCard(
            row, "تكريم طلابي", "كافئ الطلاب المميزين",
            spr(0, 551, 120, 160), null, "#FFF1C9", "#FFE08A", "#B8860B"
        ) { open(HonorActivity::class.java) }

        row = newRow()
        addCard(
            row, "مجموعاتي", "إدارة المجموعات والحصص",
            spr(308, 715, 151, 125), null, "#E3D8FE", "#D0BCFC", "#6A3CD6"
        ) { open(TeacherGroupsActivity::class.java) }
        addCard(
            row, "أكتر الأسئلة غلطًا", "راجع الأخطاء الشائعة",
            null, "❌", "#FDE8DC", "#FDD5BD", "#E5413A"
        ) { open(ExamAnalysisActivity::class.java) }

        row = newRow()
        addCard(
            row, "بنك أسئلتي", "أضف أسئلتك الخاصة",
            null, "🏦", "#D2F6DE", "#B9EFCD", "#2F7BD8"
        ) { open(QuestionBankActivity::class.java) }
        addCard(
            row, "مراجعاتي", "تابع مراجعاتك المنشورة",
            null, "📘", "#D3E8FF", "#BEDDFD", "#2F6BEA"
        ) { open(ReviewListActivity::class.java) }

        row = newRow()
        addCard(
            row, "أسئلة الطلاب", "اقرأ أسئلتهم وردّ عليهم",
            spr(140, 715, 164, 128), null, "#FFD9EE", "#FDC4E6", "#E5365A"
        ) { open(TeacherQuestionsActivity::class.java) }
        addCard(
            row, "ملفي الشخصي", "نبذتك وواتساب",
            null, "👤", "#E7E2FE", "#D6CEFB", "#4B3FD1"
        ) { open(TeacherProfileActivity::class.java) }
        row = newRow()
                row = newRow()
        addCard(
            row, "طلابي وطلبات الانضمام", "اقبل أو ارفض الطلبات، وشوف بيانات كل طالب وولي أمره",
            null, "🧑‍🎓", "#E3D8FE", "#D0BCFC", "#6A3CD6"
        ) { open(StudentsManageActivity::class.java) }
                addCard(
            row, "تقرير ولي الأمر", "صورة بدرجات الطالب وحضوره تبعتها لولي أمره",
            null, "📊", "#FCE9C8", "#FDDA96", "#F08A24"
        ) { open(ParentReportActivity::class.java) }
        addCard(
            row, "الكورسات والفيديوهات", "فيديوهات يوتيوب وكل درس عليه امتحان",
            null, "🎬", "#4F46E5", "#312E81", "#6366F1",
            dark = true
        ) { open(CourseTeacherHomeActivity::class.java) }
                row = newRow()
        addCard(
            row, "الواجبات", "اكتب واجب بالصق أو بالتصوير، وتابع مين سلّم ومين لأ",
            null, "📚", "#0EA5E9", "#0369A1", "#0284C7",
            dark = true
        ) { open(HomeworkTeacherHomeActivity::class.java) }
        row = newRow()
        addCard(
            row, "السبورة الذكية", "اكتب وشرح درسك بكل سهولة",
            null, "🖊️", "#7A33E0", "#4F1AA0", "#5A2BC8",
            dark = true
        ) { open(WhiteboardActivity::class.java) }
    }

    // الكارت بيكبر على حسب النص، فمفيش حاجة بتتقطع
    private fun addCard(
        row: LinearLayout, title: String, sub: String,
        sticker: Bitmap?, emoji: String?,
        c1: String, c2: String, accent: String,
        dark: Boolean = false, size: Int = 64,
        onClick: () -> Unit
    ) {
        val card = FrameLayout(this)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        lp.setMargins(dp(6), dp(6), dp(6), dp(6))
        card.layoutParams = lp
        card.minimumHeight = dp(120)

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
        )
        bg.cornerRadius = dp(22).toFloat()
        bg.setStroke(dp(1), Color.parseColor(if (dark) "#55FFFFFF" else "#B3FFFFFF"))
        card.background = bg
        card.clipToOutline = true
        card.elevation = dp(3).toFloat()
        card.isClickable = true
        card.setOnClickListener { onClick() }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPaddingRelative(dp(14), dp(16), dp(size + 14), dp(46))

        val titleTv = TextView(this)
        titleTv.text = title
        titleTv.textSize = 14.5f
        titleTv.setTypeface(null, Typeface.BOLD)
        titleTv.setTextColor(Color.parseColor(if (dark) "#FFFFFF" else "#241B4E"))
        titleTv.maxLines = 3
        titleTv.gravity = Gravity.START
        col.addView(titleTv, LinearLayout.LayoutParams(-1, -2))

        val subTv = TextView(this)
        subTv.text = sub
        subTv.textSize = 10.5f
        subTv.setTextColor(Color.parseColor(if (dark) "#D8CCFF" else "#5E5A7E"))
        subTv.maxLines = 4
        subTv.gravity = Gravity.START
        subTv.setPadding(0, dp(4), 0, 0)
        col.addView(subTv, LinearLayout.LayoutParams(-1, -2))
        card.addView(col, FrameLayout.LayoutParams(-1, -2))

        if (sticker != null) {
            val iv = ImageView(this)
            iv.setImageBitmap(sticker)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            val ilp = FrameLayout.LayoutParams(dp(size), dp(size), Gravity.END or Gravity.TOP)
            ilp.marginEnd = dp(8)
            ilp.topMargin = dp(12)
            card.addView(iv, ilp)
        } else if (emoji != null) {
            val e = TextView(this)
            e.text = emoji
            e.textSize = 38f
            e.gravity = Gravity.CENTER
            val elp = FrameLayout.LayoutParams(dp(size), dp(size), Gravity.END or Gravity.TOP)
            elp.marginEnd = dp(8)
            elp.topMargin = dp(12)
            card.addView(e, elp)
        }

        val arrow = TextView(this)
        arrow.text = "›"
        arrow.textDirection = View.TEXT_DIRECTION_LTR
        arrow.gravity = Gravity.CENTER
        arrow.textSize = 20f
        arrow.includeFontPadding = false
        arrow.setTypeface(null, Typeface.BOLD)
        arrow.setTextColor(Color.parseColor(accent))
        val abg = GradientDrawable()
        abg.shape = GradientDrawable.OVAL
        abg.setColor(Color.WHITE)
        arrow.background = abg
        val alp = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.END or Gravity.BOTTOM)
        alp.marginEnd = dp(10)
        alp.bottomMargin = dp(10)
        card.addView(arrow, alp)

        row.addView(card)
    }
        // ربط حساب المدرس بالجهاز (للأمان)
    private fun secureTeacher() {
        val code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""
        if (code.isEmpty()) return
        AuthGate.ensure { ok ->
            if (!ok) {
                android.widget.Toast.makeText(
                    this, "تعذّر تسجيل الدخول الآمن، تحقق من الإنترنت", android.widget.Toast.LENGTH_LONG
                ).show()
                return@ensure
            }
            TeacherAuth.claim(FirebaseFirestore.getInstance(), code) { r ->
                if (r == "other") {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("⚠️ حسابك مربوط بجهاز تاني")
                        .setMessage("الحساب ده اتربط بجهاز تاني، فمش هتقدر تشوف بيانات طلابك من هنا. كلّم الإدارة عشان تفك الربط.")
                        .setPositiveButton("حسنًا", null)
                        .show()
                }
            }
        }
    }

    // تحديث خاص بالمدرسين فقط (ملف version_teacher.json)
    private fun checkTeacherUpdate() {
        val queue = com.android.volley.toolbox.Volley.newRequestQueue(this)
        val url = "https://raw.githubusercontent.com/alifathi12366-arch/1Bac/main/version_teacher.json"

        val request = com.android.volley.toolbox.StringRequest(
            com.android.volley.Request.Method.GET, url,
            { response ->
                try {
                    val json = org.json.JSONObject(response)
                    val latest = json.getInt("latestVersion")
                    val downloadUrl = json.getString("downloadUrl")
                    if (latest > currentTeacherVersion && downloadUrl.startsWith("http")) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("تحديث جديد للمدرسين")
                            .setMessage("فيه نسخة جديدة فيها مميزات جديدة، حمّلها دلوقتي")
                            .setPositiveButton("تحميل") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)))
                            }
                            .setNegativeButton("لاحقًا", null)
                            .show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() }
        )
        queue.add(request)
    }
}
