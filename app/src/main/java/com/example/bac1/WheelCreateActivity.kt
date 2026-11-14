package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WheelCreateActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val fields = mutableListOf<List<EditText>>()
    private val rgs = mutableListOf<RadioGroup>()
    private val nameChecks = mutableListOf<CheckBox>()
    private lateinit var questionsBox: LinearLayout
    private lateinit var namesBox: LinearLayout
    private var groupName = ""
    private var teacherCode = ""

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

    private fun buildQuestions(count: Int) {
        questionsBox.removeAllViews()
        fields.clear()
        rgs.clear()
        for (n in 1..count) {
            questionsBox.addView(label("السؤال $n", 18f))
            val q = edit("نص السؤال", true)
            val o = listOf(edit("الاختيار أ"), edit("الاختيار ب"), edit("الاختيار ج"), edit("الاختيار د"))
            questionsBox.addView(q)
            o.forEach { questionsBox.addView(it) }
            questionsBox.addView(label("الإجابة الصحيحة:", 14f))
            val rg = RadioGroup(this)
            rg.orientation = RadioGroup.HORIZONTAL
            listOf("أ", "ب", "ج", "د").forEach { name ->
                val rb = RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = name
                rb.setTextColor(0xFF000000.toInt())
                rg.addView(rb)
            }
            questionsBox.addView(rg)
            fields.add(listOf(q) + o)
            rgs.add(rg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        teacherCode = prefs.getString("teacher_code", "") ?: ""

        container.addView(label("🎡 عجلة حظ جديدة", 22f))

        container.addView(label("عنوان العجلة"))
        val etTitle = edit("مثال: مراجعة حصة السبت")
        container.addView(etTitle)

        container.addView(label("وقت الإجابة على السؤال بالثواني (من 10 إلى 120)"))
        val etSeconds = edit("30")
        etSeconds.inputType = InputType.TYPE_CLASS_NUMBER
        etSeconds.setText("30")
        container.addView(etSeconds)

        container.addView(label("👥 الحاضرين على العجلة", 18f))
        val pick = Button(this)
        pick.text = "اختر المجموعة"
        pick.setOnClickListener { pickGroup() }
        container.addView(pick)
        namesBox = LinearLayout(this)
        namesBox.orientation = LinearLayout.VERTICAL
        container.addView(namesBox)

        container.addView(label("عدد الأسئلة (من 1 إلى 30)"))
        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        val etCount = edit("5")
        etCount.inputType = InputType.TYPE_CLASS_NUMBER
        etCount.setText("5")
        etCount.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        countRow.addView(etCount)
        val apply = Button(this)
        apply.text = "تطبيق"
        apply.setOnClickListener {
            val c = etCount.text.toString().trim().toIntOrNull() ?: 5
            if (c !in 1..30) Toast.makeText(this, "العدد من 1 إلى 30", Toast.LENGTH_SHORT).show()
            else buildQuestions(c)
        }
        countRow.addView(apply)
        container.addView(countRow)

        questionsBox = LinearLayout(this)
        questionsBox.orientation = LinearLayout.VERTICAL
        container.addView(questionsBox)
        buildQuestions(5)

        val save = Button(this)
        save.text = "🚀 حفظ وفتح العجلة"
        save.setTextColor(0xFFFFFFFF.toInt())
        save.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        save.layoutParams = lp
        container.addView(save)

        save.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val seconds = etSeconds.text.toString().trim().toIntOrNull() ?: 0
            if (title.isEmpty()) {
                Toast.makeText(this, "اكتب عنوان العجلة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (seconds !in 10..120) {
                Toast.makeText(this, "الوقت من 10 إلى 120 ثانية", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val names = nameChecks.filter { it.isChecked }.map { it.text.toString() }
            if (names.size < 2) {
                Toast.makeText(this, "اختر طالبين حاضرين على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val questions = mutableListOf<HashMap<String, Any>>()
            for (i in 0 until fields.size) {
                val f = fields[i]
                val text = f[0].text.toString().trim()
                val opts = f.drop(1).map { it.text.toString().trim() }
                val g = rgs[i]
                if (text.isEmpty() || opts.any { it.isEmpty() } || g.checkedRadioButtonId == -1) {
                    Toast.makeText(this, "السؤال ${i + 1} غير مكتمل", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val correct = g.indexOfChild(g.findViewById(g.checkedRadioButtonId))
                questions.add(
                    hashMapOf<String, Any>("text" to text, "options" to opts, "correctIndex" to correct)
                )
            }

            val id = UUID.randomUUID().toString()
            save.isEnabled = false
            db.collection("wheels").document(id)
                .set(
                    hashMapOf<String, Any>(
                        "wheelId" to id,
                        "teacherCode" to teacherCode,
                        "teacherName" to (prefs.getString("teacher_name", "") ?: ""),
                        "title" to title,
                        "groupName" to groupName,
                        "seconds" to seconds,
                        "questions" to questions,
                        "names" to names,
                        "phase" to "idle",
                        "chosenName" to "",
                        "lastName" to "",
                        "nameSpinId" to 0L,
                        "qSpinId" to 0L,
                        "qIndex" to -1,
                        "answer" to -1,
                        "correct" to false,
                        "used" to emptyList<Any>(),
                        "results" to emptyList<Any>(),
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    TeacherActivityLog.post(this, "فتح عجلة الحظ 🎡 ادخل الألعاب")
                    Toast.makeText(this, "تم فتح العجلة ✅", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, WheelPlayActivity::class.java).putExtra("wheelId", id))
                    finish()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    Toast.makeText(this, "فشل الحفظ: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    // ---------- اختيار المجموعة والحاضرين ----------
    private fun pickGroup() {
        db.collection("teacherGroups").whereEqualTo("teacherCode", teacherCode).get()
            .addOnSuccessListener { res ->
                val docs = res.documents.sortedBy { it.getString("name") ?: "" }
                val labels = ArrayList<String>()
                labels.add("👥 كل طلابي المسجلين")
                docs.forEach { labels.add(it.getString("name") ?: "مجموعة") }
                AlertDialog.Builder(this)
                    .setTitle("اختر المجموعة")
                    .setItems(labels.toTypedArray()) { _, which ->
                        if (which == 0) {
                            loadAllStudents()
                        } else {
                            val g = docs[which - 1]
                            val members = (g.get("studentNames") as? List<*>)
                                ?.mapNotNull { it as? String } ?: emptyList()
                            loadGroup(g.id, g.getString("name") ?: "", members)
                        }
                    }
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "تعذّر تحميل المجموعات", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadGroup(id: String, name: String, members: List<String>) {
        groupName = name
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        db.collection("attendanceSessions")
            .whereEqualTo("groupId", id).whereEqualTo("date", today).get()
            .addOnSuccessListener { res ->
                val present = res.documents.mapNotNull { it.getString("studentName") }.toSet()
                showNames((members + present).distinct().sorted(), present, "مجموعة $name")
            }
            .addOnFailureListener {
                showNames(members.distinct().sorted(), emptySet(), "مجموعة $name")
            }
    }

    private fun loadAllStudents() {
        groupName = "كل الطلاب"
        db.collection("users").whereEqualTo("joined_teacher_code", teacherCode).get()
            .addOnSuccessListener { res ->
                val names = res.documents.mapNotNull { it.getString("name") }
                    .filter { it.isNotBlank() }.distinct().sorted()
                showNames(names, emptySet(), "كل طلابك")
            }
            .addOnFailureListener {
                Toast.makeText(this, "تعذّر تحميل الطلاب", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showNames(names: List<String>, present: Set<String>, title: String) {
        namesBox.removeAllViews()
        nameChecks.clear()
        val hint = if (present.isEmpty()) "مفيش حضور متسجل النهارده، علّم على الحاضرين بإيدك"
        else "الحاضرين النهارده متعلّم عليهم تلقائيًا ✅ (عدّل لو احتجت)"
        namesBox.addView(label("$title: $hint", 13f))
        if (names.isEmpty()) {
            namesBox.addView(label("لا يوجد طلاب.", 14f))
            return
        }
        names.forEach { n ->
            val cb = CheckBox(this)
            cb.text = n
            cb.textSize = 16f
            cb.setTextColor(0xFF000000.toInt())
            cb.isChecked = present.isEmpty() || present.contains(n)
            namesBox.addView(cb)
            nameChecks.add(cb)
        }
    }
}
