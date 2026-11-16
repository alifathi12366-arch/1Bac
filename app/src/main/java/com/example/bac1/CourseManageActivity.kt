package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CourseManageActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private var courseId = ""
    private var title = ""
    private var pass = 60
    private var lessons: List<CourseUtil.Lesson> = emptyList()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        courseId = intent.getStringExtra("courseId") ?: ""
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        db.collection("courses").document(courseId).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    finish()
                    return@addOnSuccessListener
                }
                title = d.getString("title") ?: ""
                pass = (d.getLong("passPercent") ?: 60L).toInt()
                lessons = CourseUtil.parseLessons(d.get("lessons"))
                render()
            }
            .addOnFailureListener {
                Toast.makeText(this, "تعذّر التحميل، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
            }
    }

    private fun render() {
        container.removeAllViews()
        container.addView(tv("🎬 $title", 22f, true))

        val rename = Button(this)
        rename.text = "✏️ تغيير اسم الكورس"
        rename.setOnClickListener { askText("اسم الكورس", title, false) { v -> update("title", v) } }
        container.addView(rename)

        val pb = Button(this)
        pb.text = "🎯 نسبة النجاح في كل امتحان: $pass%  (اضغط للتعديل)"
        pb.isAllCaps = false
        pb.setOnClickListener {
            askText("نسبة النجاح (من 10 إلى 100)", pass.toString(), true) { v ->
                val n = v.toIntOrNull()
                if (n == null || n !in 10..100) {
                    Toast.makeText(this, "اكتب رقم من 10 إلى 100", Toast.LENGTH_SHORT).show()
                } else {
                    update("passPercent", n.toLong())
                }
            }
        }
        container.addView(pb)

        container.addView(tv("الدروس بالترتيب (الطالب بيفتح الدرس اللي بعده بس لما ينجح في امتحان اللي قبله)", 13f))
        if (lessons.isEmpty()) container.addView(tv("لسه مفيش دروس، ضيف أول درس.", 14f))
        lessons.forEachIndexed { i, l ->
            val b = Button(this)
            b.isAllCaps = false
            b.text = "${i + 1}. ${l.title} · ${l.qs.size} سؤال"
            b.setOnClickListener { lessonMenu(i) }
            container.addView(b)
        }

        val add = Button(this)
        add.text = "➕ درس جديد"
        add.setOnClickListener {
            startActivity(Intent(this, LessonEditActivity::class.java).putExtra("courseId", courseId))
        }
        container.addView(add)

        val prog = Button(this)
        prog.text = "📊 تقدّم الطلاب"
        prog.setOnClickListener {
            startActivity(Intent(this, CourseProgressActivity::class.java).putExtra("courseId", courseId))
        }
        container.addView(prog)
    }

    private fun askText(head: String, current: String, number: Boolean, onOk: (String) -> Unit) {
        val et = EditText(this)
        et.setText(current)
        et.setSelection(et.text.length)
        if (number) et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setPadding(dp(20), dp(12), dp(20), dp(12))
        AlertDialog.Builder(this)
            .setTitle(head)
            .setView(et)
            .setPositiveButton("حفظ") { _, _ ->
                val v = et.text.toString().trim()
                if (v.isNotEmpty()) onOk(v)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun update(field: String, value: Any) {
        db.collection("courses").document(courseId).update(field, value)
            .addOnSuccessListener { load() }
            .addOnFailureListener {
                Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show()
            }
    }

    private fun lessonMenu(i: Int) {
        val l = lessons[i]
        AlertDialog.Builder(this)
            .setTitle(l.title)
            .setItems(arrayOf("✏️ تعديل الدرس", "⬆ نقل لفوق", "⬇ نقل لتحت", "🗑 حذف الدرس")) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(this, LessonEditActivity::class.java)
                            .putExtra("courseId", courseId)
                            .putExtra("lessonId", l.id)
                    )
                    1 -> if (i > 0) move(i, i - 1)
                    2 -> if (i < lessons.size - 1) move(i, i + 1)
                    3 -> confirmDelete(i)
                    else -> {}
                }
            }
            .show()
    }

    private fun move(from: Int, to: Int) {
        val list = lessons.toMutableList()
        val x = list.removeAt(from)
        list.add(to, x)
        saveLessons(list)
    }

    private fun confirmDelete(i: Int) {
        AlertDialog.Builder(this)
            .setTitle("حذف الدرس")
            .setMessage("تحذف \"${lessons[i].title}\"؟")
            .setPositiveButton("احذف") { _, _ ->
                val list = lessons.toMutableList()
                list.removeAt(i)
                saveLessons(list)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun saveLessons(list: List<CourseUtil.Lesson>) {
        db.collection("courses").document(courseId)
            .update("lessons", list.map { CourseUtil.lessonToMap(it) })
            .addOnSuccessListener { load() }
            .addOnFailureListener {
                Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show()
            }
    }
}
