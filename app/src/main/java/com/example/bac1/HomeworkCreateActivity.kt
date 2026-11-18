package com.example.bac1

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Base64
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
import java.io.ByteArrayOutputStream
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
    private lateinit var allCb: CheckBox
    private lateinit var groupsBox: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var previewBox: LinearLayout
    private lateinit var btnPublish: Button

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) fromImage(uri)
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

        container.addView(label("👥 مين يشوف الواجب؟"))
        allCb = CheckBox(this)
        allCb.text = "كل المجموعات"
        allCb.setTextColor(0xFF000000.toInt())
        allCb.isChecked = true
        allCb.setOnCheckedChangeListener { _, on ->
            groupChecks.forEach {
                it.isEnabled = !on
                if (on) it.isChecked = false
            }
        }
        container.addView(allCb)
        groupsBox = LinearLayout(this)
        groupsBox.orientation = LinearLayout.VERTICAL
        container.addView(groupsBox)

        container.addView(label("📝 الأسئلة", 20f))
        container.addView(label("اختار طريقة (تقدر تستخدم أكتر من طريقة، والأسئلة بتتجمّع):", 13f))
        container.addView(actionBtn("📋 ألصق أسئلتك") { pasteDialog() })
        container.addView(actionBtn("🧠 اطلع أسئلة من نص الدرس (ذكاء اصطناعي)") { aiTextDialog() })
        container.addView(actionBtn("📸 صوّر ورقة الأسئلة (ذكاء اصطناعي)") { picker.launch("image/*") })

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
        btnPublish.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        btnPublish.layoutParams = lp
        btnPublish.setOnClickListener { publish() }
        container.addView(btnPublish)

        loadGroups()
    }

    private fun loadGroups() {
        val code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""
        db.collection("teacherGroups").whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { gs ->
                groupsBox.removeAllViews()
                groupChecks.clear()
                gs.documents.map { Pair(it.id, it.getString("name") ?: "مجموعة") }
                    .sortedBy { it.second }
                    .forEach { (gid, name) ->
                        val cb = CheckBox(this)
                        cb.text = name
                        cb.tag = gid
                        cb.isEnabled = false
                        cb.setTextColor(0xFF000000.toInt())
                        groupsBox.addView(cb)
                        groupChecks.add(cb)
                    }
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
    private fun setBusy(on: Boolean, msg: String) {
        busy = on
        tvStatus.text = msg
        actionBtns.forEach { it.isEnabled = !on }
        btnPublish.isEnabled = !on
    }

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
            bg.setStroke(dp(if (q.correct < 0) 2 else 1), if (q.correct < 0) 0xFFE53935.toInt() else 0xFFDDDDDD.toInt())
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

    private fun aiTextDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(8), dp(18), 0)
        val cnt = EditText(this)
        cnt.hint = "عدد الأسئلة (من 1 إلى 30)"
        cnt.inputType = InputType.TYPE_CLASS_NUMBER
        cnt.setText("10")
        cnt.setTextColor(0xFF000000.toInt())
        box.addView(cnt)
        val et = bigEdit("ألصق نص الدرس أو الملخص هنا", 8)
        box.addView(et)
        val sv = ScrollView(this)
        sv.addView(box)

        AlertDialog.Builder(this)
            .setTitle("🧠 أسئلة من نص الدرس")
            .setView(sv)
            .setPositiveButton("اطلع الأسئلة") { _, _ ->
                val n = cnt.text.toString().trim().toIntOrNull() ?: 10
                val text = et.text.toString().trim()
                if (n !in 1..30) toast("العدد من 1 إلى 30")
                else if (text.length < 40) toast("النص قصير، الصق ملخص أطول")
                else fromText(text.take(12000), n)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private val jsonShape = "[{\"text\":\"\",\"options\":[\"\",\"\",\"\",\"\"],\"correct_index\":0}]"

    private fun fromText(text: String, n: Int) {
        setBusy(true, "⏳ الذكاء الاصطناعي بيكتب الأسئلة...")
        val prompt = "You are an expert teacher. From the lesson text below, write exactly $n multiple-choice " +
                "questions for Egyptian secondary-school students. Write in the same language as the text. " +
                "Each question has exactly 4 options and exactly one correct option (correct_index is zero-based). " +
                "Vary the difficulty and do not repeat questions. Reply ONLY with a JSON array shaped like: " +
                "$jsonShape\n\nLesson text:\n$text"
        Thread {
            val r = GeminiClient.ask(null, prompt, true)
            runOnUiThread { aiDone(r) }
        }.start()
    }

    private fun fromImage(uri: Uri) {
        setBusy(true, "⏳ بقرأ الصورة...")
        Thread {
            val b64 = imageToBase64(uri)
            if (b64 == null) {
                runOnUiThread {
                    setBusy(false, "تعذّر قراءة الصورة")
                }
            } else {
                val prompt = "This image is a paper of multiple-choice questions. Extract every question exactly as " +
                        "written (keep the original language) with its options in order. If the paper shows the " +
                        "correct answers use them, otherwise choose the most likely correct option. " +
                        "correct_index is zero-based. Reply ONLY with a JSON array shaped like: $jsonShape"
                val r = GeminiClient.askImage(prompt, b64, "image/jpeg", true)
                runOnUiThread { aiDone(r) }
            }
        }.start()
    }

    private fun aiDone(r: GeminiClient.Result) {
        if (isFinishing) return
        setBusy(false, "")
        val raw = r.text
        if (raw == null) {
            tvStatus.text = "⚠️ " + (r.error ?: "حصل خطأ")
            return
        }
        val list = HomeworkUtil.parseAi(raw)
        if (list.isEmpty()) tvStatus.text = "⚠️ ما قدرتش أطلع أسئلة، جرّب تاني أو الصق الأسئلة بإيدك"
        else {
            addDrafts(list, 0)
            toast("راجع الأسئلة والإجابات قبل النشر 👀")
        }
    }

    private fun imageToBase64(uri: Uri): String? {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val o1 = BitmapFactory.Options()
            o1.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o1)
            var sample = 1
            while (maxOf(o1.outWidth, o1.outHeight) / sample > 1800) sample *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = sample
            val bmp: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2) ?: return null
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Throwable) {
            null
        }
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
        if (drafts.isEmpty()) {
            toast("ضيف أسئلة الأول")
            return
        }
        val bad = drafts.indexOfFirst { it.correct < 0 }
        if (bad >= 0) {
            toast("السؤال ${bad + 1} من غير إجابة صحيحة")
            return
        }
        val groups = if (allCb.isChecked) emptyList() else groupChecks.filter { it.isChecked }.map { it.tag as String }
        if (!allCb.isChecked && groups.isEmpty()) {
            toast("اختر مجموعة على الأقل أو كل المجموعات")
            return
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val id = UUID.randomUUID().toString()
        btnPublish.isEnabled = false
        db.collection("homeworks").document(id).set(
            hashMapOf<String, Any>(
                "homeworkId" to id,
                "teacherCode" to (prefs.getString("teacher_code", "") ?: ""),
                "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                "title" to title,
                "deadline" to deadlineMs,
                "groups" to groups,
                "questions" to drafts.map { CourseUtil.questionToMap(CourseUtil.Q(it.text, it.options.toList(), it.correct, "")) },
                "createdAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            TeacherActivityLog.post(this, "نزّل واجب جديد 📚")
            toast("تم نشر الواجب ✅")
            finish()
        }.addOnFailureListener {
            btnPublish.isEnabled = true
            Toast.makeText(this, "فشل النشر: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
