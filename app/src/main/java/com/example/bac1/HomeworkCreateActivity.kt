package com.example.bac1

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.UUID

class HomeworkCreateActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val drafts = mutableListOf<HomeworkUtil.Draft>()
    private val groupChecks = mutableListOf<CheckBox>()
    private val actionBtns = mutableListOf<Button>()
    private var deadlineMs = 0L
    private var busy = false

    private lateinit var etTitle: EditText
    private lateinit var tvDeadline: TextView
    private lateinit var groupsBox: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var previewBox: LinearLayout
    private lateinit var btnPublish: Button

    private val pickFromBankLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode != RESULT_OK) return@registerForActivityResult
            val rawList = res.data?.getStringArrayListExtra("picked") ?: return@registerForActivityResult
            val imported = mutableListOf<HomeworkUtil.Draft>()
            var skipped = 0
            rawList.forEach { item ->
                val parts = item.split("\u0001")
                if (parts.size < 6) {
                    skipped++
                    return@forEach
                }
                val qText = parts[0].trim()
                val opts = parts.subList(1, 5).map { it.trim() }
                val corr1Based = parts[5].trim().toIntOrNull() ?: 1
                val corr0Based = (corr1Based - 1).coerceIn(-1, 3)
                if (qText.isEmpty() || opts.any { it.isEmpty() }) {
                    skipped++
                } else {
                    imported.add(HomeworkUtil.Draft(qText, opts.toMutableList(), corr0Based))
                }
            }
            if (imported.isNotEmpty()) {
                addDrafts(imported, skipped)
            } else if (skipped > 0) {
                toast("تعذّر قراءة الأسئلة المختارة")
            }
        }

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

    private fun actionBtn(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.isAllCaps = false
        b.text = text
        b.setOnClickListener { if (!busy) onClick() }
        actionBtns.add(b)
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)

        container.addView(label("📚 واجب جديد", 22f))

        container.addView(label("عنوان الواجب"))
        etTitle = EditText(this)
        etTitle.hint = "مثال: واجب الوحدة الأولى"
        etTitle.setTextColor(0xFF000000.toInt())
        container.addView(etTitle)

        container.addView(label("آخر موعد للتسليم"))
        tvDeadline = TextView(this)
        tvDeadline.text = "📅 اضغط لتحديد الموعد"
        tvDeadline.textSize = 16f
        tvDeadline.setTextColor(0xFF222222.toInt())
        tvDeadline.setBackgroundColor(0xFFE0E0E0.toInt())
        tvDeadline.setPadding(dp(12), dp(12), dp(12), dp(12))
        tvDeadline.setOnClickListener { pickDeadline() }
        container.addView(tvDeadline)

        container.addView(label("👥 حدد مجموعة أو أكثر للواجب:"))
        groupsBox = LinearLayout(this)
        groupsBox.orientation = LinearLayout.VERTICAL
        container.addView(groupsBox)

        container.addView(label("📝 الأسئلة", 20f))
        container.addView(label("اختار طريقة أو أكتر لإضافة الأسئلة:", 13f))
        container.addView(actionBtn("🏦 اختر من بنك الأسئلة") { openQuestionBankPicker() })
        container.addView(actionBtn("📋 ألصق أسئلتك") { pasteDialog() })

        tvStatus = label("", 14f)
        container.addView(tvStatus)
        tvCount = label("عدد الأسئلة: 0", 16f)
        tvCount.setTypeface(null, Typeface.BOLD)
        container.addView(tvCount)
        container.addView(label("اضغط على الاختيار الصح عشان تحدده كإجابة صحيحة، و✖ لحذف السؤال.", 12f))
        previewBox = LinearLayout(this)
        previewBox.orientation = LinearLayout.VERTICAL
        container.addView(previewBox)

        btnPublish = Button(this)
        btnPublish.text = "🚀 نشر الواجب"
        btnPublish.setTextColor(0xFFFFFFFF.toInt())
        btnPublish.backgroundTintList = ColorStateList.valueOf(0xFF4CAF50.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        btnPublish.layoutParams = lp
        btnPublish.setOnClickListener { publish() }
        container.addView(btnPublish)

        loadGroups()
    }

    private fun openQuestionBankPicker() {
        val intent = Intent(this, QuestionBankActivity::class.java)
        intent.putExtra("pick", true)
        pickFromBankLauncher.launch(intent)
    }

    private fun loadGroups() {
        val code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""
        db.collection("teacherGroups").whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { gs ->
                groupsBox.removeAllViews()
                groupChecks.clear()
                val list = gs.documents.map { Pair(it.id, it.getString("name") ?: "مجموعة") }
                    .sortedBy { it.second }
                if (list.isEmpty()) {
                    groupsBox.addView(label("لا توجد مجموعات مسجلة حالياً.", 14f))
                    return@addOnSuccessListener
                }
                list.forEach { (gid, name) ->
                    val cb = CheckBox(this)
                    cb.text = name
                    cb.tag = gid
                    cb.isEnabled = true
                    cb.setTextColor(0xFF000000.toInt())
                    groupsBox.addView(cb)
                    groupChecks.add(cb)
                }
            }
            .addOnFailureListener {
                groupsBox.addView(label("تعذّر تحميل المجموعات", 14f))
            }
    }

    private fun pickDeadline() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val c = Calendar.getInstance()
                c.set(y, m, d, h, min, 0)
                c.set(Calendar.MILLISECOND, 0)
                deadlineMs = c.timeInMillis
                tvDeadline.text = "📅 آخر موعد: " + HomeworkUtil.fmtDate(deadlineMs)
            }, 23, 0, false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ---------- إضافة الأسئلة ----------
    private fun addDrafts(list: List<HomeworkUtil.Draft>, skipped: Int) {
        if (drafts.size + list.size > 200) {
            toast("الحد الأقصى 200 سؤال في الواجب")
            return
        }
        drafts.addAll(list)
        val noAns = list.count { it.correct < 0 }
        var msg = "✅ اتضاف ${list.size} سؤال"
        if (skipped > 0) msg += " · $skipped اتسابوا (ناقصين)"
        if (noAns > 0) msg += " · ⚠️ $noAns محتاجين تحدد إجابتهم"
        tvStatus.text = msg
        renderPreview()
    }

    private fun renderPreview() {
        tvCount.text = "عدد الأسئلة: ${drafts.size}"
        previewBox.removeAllViews()
        drafts.forEachIndexed { i, q ->
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(12), dp(10), dp(12), dp(10))
            val bg = GradientDrawable()
            bg.setColor(Color.WHITE)
            bg.cornerRadius = dp(12).toFloat()
            bg.setStroke(
                dp(if (q.correct < 0) 2 else 1),
                if (q.correct < 0) 0xFFE53935.toInt() else 0xFFDDDDDD.toInt()
            )
            card.background = bg
            val clp = LinearLayout.LayoutParams(-1, -2)
            clp.topMargin = dp(8)
            card.layoutParams = clp

            val head = LinearLayout(this)
            head.orientation = LinearLayout.HORIZONTAL
            val h = TextView(this)
            h.text = "السؤال ${i + 1}"
            h.textSize = 13f
            h.setTextColor(0xFF6B7280.toInt())
            head.addView(h, LinearLayout.LayoutParams(0, -2, 1f))
            val x = TextView(this)
            x.text = "✖"
            x.textSize = 18f
            x.setPadding(dp(10), 0, dp(10), 0)
            x.setOnClickListener {
                drafts.remove(q)
                renderPreview()
            }
            head.addView(x)
            card.addView(head)

            val t = TextView(this)
            t.text = q.text
            t.textSize = 16f
            t.setTypeface(null, Typeface.BOLD)
            t.setTextColor(0xFF1F2A44.toInt())
            card.addView(t)

            q.options.forEachIndexed { k, opt ->
                val o = TextView(this)
                val ok = k == q.correct
                o.text = (if (ok) "✅  " else "○  ") + opt
                o.textSize = 15f
                o.setTextColor(if (ok) 0xFF1B5E20.toInt() else 0xFF333333.toInt())
                o.setPadding(dp(10), dp(8), dp(10), dp(8))
                val og = GradientDrawable()
                og.setColor(if (ok) 0xFFE8F5E9.toInt() else 0xFFF5F6F8.toInt())
                og.cornerRadius = dp(8).toFloat()
                o.background = og
                val olp = LinearLayout.LayoutParams(-1, -2)
                olp.topMargin = dp(4)
                o.layoutParams = olp
                o.setOnClickListener {
                    q.correct = k
                    renderPreview()
                }
                card.addView(o)
            }
            if (q.correct < 0) {
                val w = TextView(this)
                w.text = "⚠️ اضغط على الاختيار الصح"
                w.textSize = 12f
                w.setTextColor(0xFFE53935.toInt())
                card.addView(w)
            }
            previewBox.addView(card)
        }
    }

    private fun bigEdit(hint: String, minLines: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.minLines = minLines
        e.gravity = Gravity.TOP or Gravity.START
        e.setTextColor(0xFF000000.toInt())
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        return e
    }

    private fun pasteDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(8), dp(18), 0)
        val ex = TextView(this)
        ex.text = "الشكل المقبول:\n1) عاصمة مصر؟\nأ) القاهرة\nب) أسوان\nج) الأقصر\nد) طنطا\nالإجابة: أ\n\n" +
                "أو علّم الإجابة الصحيحة بـ ✓ جنب الاختيار."
        ex.textSize = 12f
        ex.setTextColor(0xFF6B7280.toInt())
        box.addView(ex)
        val et = bigEdit("ألصق الأسئلة هنا", 8)
        box.addView(et)
        val sv = ScrollView(this)
        sv.addView(box)

        AlertDialog.Builder(this)
            .setTitle("📋 ألصق أسئلتك")
            .setView(sv)
            .setPositiveButton("إضافة") { _, _ ->
                val p = HomeworkUtil.parsePasted(et.text.toString())
                if (p.list.isEmpty()) toast("ما قدرتش أفهم أي سؤال، راجع الشكل المقبول")
                else addDrafts(p.list, p.skipped)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------- النشر ----------
    private fun publish() {
        if (busy) return
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            toast("اكتب عنوان الواجب")
            return
        }
        if (deadlineMs <= System.currentTimeMillis()) {
            toast("حدد آخر موعد في المستقبل")
            return
        }
        val groups = groupChecks.filter { it.isChecked }.map { it.tag as String }
        if (groups.isEmpty()) {
            toast("اختر مجموعة واحدة على الأقل")
            return
        }
        if (drafts.isEmpty()) {
            toast("ضيف أسئلة الأول")
            return
        }
        val bad = drafts.indexOfFirst { it.correct < 0 }
        if (bad >= 0) {
            toast("السؤال ${bad + 1} من غير إجابة صحيحة")
            return
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val id = UUID.randomUUID().toString()
        busy = true
        actionBtns.forEach { it.isEnabled = false }
        btnPublish.isEnabled = false

        db.collection("homeworks").document(id).set(
            hashMapOf<String, Any>(
                "homeworkId" to id,
                "teacherCode" to (prefs.getString("teacher_code", "") ?: ""),
                "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                "title" to title,
                "deadline" to deadlineMs,
                "groups" to groups,
                "questions" to drafts.map {
                    CourseUtil.questionToMap(CourseUtil.Q(it.text, it.options.toList(), it.correct, ""))
                },
                "createdAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            TeacherActivityLog.post(this, "نزّل واجب جديد 📚")
            toast("تم نشر الواجب ✅")
            finish()
        }.addOnFailureListener {
            busy = false
            actionBtns.forEach { it.isEnabled = true }
            btnPublish.isEnabled = true
            Toast.makeText(this, "فشل النشر: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
