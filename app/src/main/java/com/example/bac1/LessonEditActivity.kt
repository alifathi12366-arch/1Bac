package com.example.bac1

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class LessonEditActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var courseId = ""
    private var lessonId: String? = null
    private var lessons: List<CourseUtil.Lesson> = emptyList()
    private var loaded = false

    private val qText = mutableListOf<EditText>()
    private val qOpts = mutableListOf<List<EditText>>()
    private val qRg = mutableListOf<RadioGroup>()
    private val qNote = mutableListOf<EditText>()

    private lateinit var questionsBox: LinearLayout
    private lateinit var etTitle: EditText
    private lateinit var etLink: EditText
    private lateinit var etDesc: EditText
    private lateinit var etCount: EditText
    private lateinit var saveBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    private fun edit(hint: String, multiline: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setTextColor(0xFF000000.toInt())
        if (multiline) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        return e
    }

    // بيحفظ اللي اتكتب عشان لما تغيّر عدد الأسئلة ما يضيعش
    private fun currentSeed(): List<CourseUtil.Q> = qText.indices.map { i ->
        val rg = qRg[i]
        val c = if (rg.checkedRadioButtonId == -1) -1
        else rg.indexOfChild(rg.findViewById(rg.checkedRadioButtonId))
        CourseUtil.Q(
            qText[i].text.toString(), qOpts[i].map { it.text.toString() }, c, qNote[i].text.toString()
        )
    }

    private fun buildQuestions(count: Int, seeds: List<CourseUtil.Q>) {
        questionsBox.removeAllViews()
        qText.clear()
        qOpts.clear()
        qRg.clear()
        qNote.clear()
        val names = listOf("أ", "ب", "ج", "د")
        for (n in 1..count) {
            val seed = seeds.getOrNull(n - 1)
            questionsBox.addView(label("السؤال $n", 18f))
            val q = edit("نص السؤال", true)
            q.setText(seed?.text ?: "")
            questionsBox.addView(q)
            val opts = names.mapIndexed { i, nm ->
                val e = edit("الاختيار $nm")
                e.setText(seed?.options?.getOrNull(i) ?: "")
                questionsBox.addView(e)
                e
            }
            questionsBox.addView(label("الإجابة الصحيحة:", 14f))
            val rg = RadioGroup(this)
            rg.orientation = RadioGroup.HORIZONTAL
            names.forEach { nm ->
                val rb = RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = nm
                rb.setTextColor(0xFF000000.toInt())
                rg.addView(rb)
            }
            val c = seed?.correct ?: -1
            if (c in 0..3) (rg.getChildAt(c) as RadioButton).isChecked = true
            questionsBox.addView(rg)
            val note = edit("💡 ملاحظة بعد الإجابة (اختياري)", true)
            note.setText(seed?.note ?: "")
            questionsBox.addView(note)

            qText.add(q)
            qOpts.add(opts)
            qRg.add(rg)
            qNote.add(note)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        courseId = intent.getStringExtra("courseId") ?: ""
        lessonId = intent.getStringExtra("lessonId")

        container.addView(label(if (lessonId == null) "➕ درس جديد" else "✏️ تعديل الدرس", 22f))

        container.addView(label("عنوان الدرس"))
        etTitle = edit("مثال: الدرس الأول - المقدمة")
        container.addView(etTitle)

        container.addView(label("رابط الفيديو من يوتيوب"))
        container.addView(label("ارفع الفيديو على يوتيوب بوضع \"غير مدرج\" والصق الرابط هنا.", 12f))
        etLink = edit("https://youtu.be/...")
        etLink.inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
        container.addView(etLink)

        container.addView(label("وصف قصير للدرس (اختياري)"))
        etDesc = edit("ملخص أو ملاحظات للطالب", true)
        container.addView(etDesc)

        container.addView(label("🧪 امتحان الدرس", 20f))
        container.addView(label("عدد الأسئلة (من 1 إلى 10)"))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        etCount = edit("3")
        etCount.inputType = InputType.TYPE_CLASS_NUMBER
        etCount.setText("3")
        etCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        countRow.addView(etCount)
        val apply = Button(this)
        apply.text = "تطبيق"
        apply.setOnClickListener {
            val c = etCount.text.toString().trim().toIntOrNull() ?: 3
            if (c !in 1..10) Toast.makeText(this, "العدد من 1 إلى 10", Toast.LENGTH_SHORT).show()
            else buildQuestions(c, currentSeed())
        }
        countRow.addView(apply)
        container.addView(countRow)

        questionsBox = LinearLayout(this)
        questionsBox.orientation = LinearLayout.VERTICAL
        container.addView(questionsBox)

        saveBtn = Button(this)
        saveBtn.text = "💾 حفظ الدرس"
        saveBtn.setTextColor(0xFFFFFFFF.toInt())
        saveBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        saveBtn.isEnabled = false
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        saveBtn.layoutParams = lp
        saveBtn.setOnClickListener { save() }
        container.addView(saveBtn)

        load()
    }

    private fun load() {
        db.collection("courses").document(courseId).get()
            .addOnSuccessListener { d ->
                lessons = CourseUtil.parseLessons(d.get("lessons"))
                val existing = lessons.firstOrNull { it.id == lessonId }
                if (existing != null) {
                    etTitle.setText(existing.title)
                    etLink.setText("https://youtu.be/${existing.videoId}")
                    etDesc.setText(existing.desc)
                    val n = existing.qs.size.coerceIn(1, 10)
                    etCount.setText(n.toString())
                    buildQuestions(n, existing.qs)
                } else {
                    buildQuestions(3, emptyList())
                }
                loaded = true
                saveBtn.isEnabled = true
            }
            .addOnFailureListener {
                Toast.makeText(this, "تعذّر التحميل، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
            }
    }

    private fun save() {
        if (!loaded) return
        val title = etTitle.text.toString().trim()
        val vid = CourseUtil.videoId(etLink.text.toString())
        if (title.isEmpty()) {
            Toast.makeText(this, "اكتب عنوان الدرس", Toast.LENGTH_SHORT).show()
            return
        }
        if (vid == null) {
            Toast.makeText(this, "رابط اليوتيوب غير صحيح", Toast.LENGTH_SHORT).show()
            return
        }
        val qs = mutableListOf<CourseUtil.Q>()
        for (i in qText.indices) {
            val t = qText[i].text.toString().trim()
            val opts = qOpts[i].map { it.text.toString().trim() }
            val g = qRg[i]
            if (t.isEmpty() || opts.any { it.isEmpty() } || g.checkedRadioButtonId == -1) {
                Toast.makeText(this, "السؤال ${i + 1} غير مكتمل", Toast.LENGTH_SHORT).show()
                return
            }
            val c = g.indexOfChild(g.findViewById(g.checkedRadioButtonId))
            qs.add(CourseUtil.Q(t, opts, c, qNote[i].text.toString().trim()))
        }

        val isNew = lessonId == null
        val lesson = CourseUtil.Lesson(
            lessonId ?: CourseUtil.newId(), title, vid, etDesc.text.toString().trim(), qs
        )
        val newList = if (isNew) lessons + lesson
        else lessons.map { if (it.id == lesson.id) lesson else it }

        saveBtn.isEnabled = false
        db.collection("courses").document(courseId)
            .update("lessons", newList.map { CourseUtil.lessonToMap(it) })
            .addOnSuccessListener {
                if (isNew) TeacherActivityLog.post(this, "نزّل درس جديد في كورس 🎬")
                Toast.makeText(this, "تم حفظ الدرس ✅", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                saveBtn.isEnabled = true
                Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
