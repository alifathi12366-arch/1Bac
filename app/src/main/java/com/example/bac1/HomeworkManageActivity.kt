package com.example.bac1

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class HomeworkManageActivity : AppCompatActivity() {

    private class Sub(val name: String, val score: Int, val total: Int)
    private class Stu(val name: String, val parent: String)

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private var hwId = ""
    private var code = ""
    private var teacherName = ""
    private var title = ""
    private var deadline = 0L
    private var roster: List<Stu> = emptyList()
    private var audience: Set<String> = emptySet()
    private var subs: List<Sub> = emptyList()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(6), 0, dp(3))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        hwId = intent.getStringExtra("hwId") ?: ""
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        code = prefs.getString("teacher_code", "") ?: ""
        teacherName = prefs.getString("teacher_name", "") ?: ""
        load()
    }

    private fun fail() {
        Toast.makeText(this, "تعذّر التحميل، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
    }

    private fun load() {
        db.collection("homeworks").document(hwId).get()
            .addOnSuccessListener { h ->
                if (!h.exists()) {
                    finish()
                    return@addOnSuccessListener
                }
                title = h.getString("title") ?: ""
                deadline = h.getLong("deadline") ?: 0L
                val groups = (h.get("groups") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                db.collection("users").whereEqualTo("joined_teacher_code", code).get()
                    .addOnSuccessListener { us ->
                        roster = us.documents.map { Stu(it.getString("name") ?: "", it.getString("parentPhone") ?: "") }
                            .filter { it.name.isNotBlank() }.distinctBy { it.name }
                        if (groups.isEmpty()) {
                            audience = roster.map { it.name }.toSet()
                            loadSubs()
                        } else {
                            db.collection("teacherGroups").whereEqualTo("teacherCode", code).get()
                                .addOnSuccessListener { gs ->
                                    audience = gs.documents.filter { it.id in groups }.flatMap { g ->
                                        (g.get("studentNames") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                                    }.toSet()
                                    loadSubs()
                                }
                                .addOnFailureListener { fail() }
                        }
                    }
                    .addOnFailureListener { fail() }
            }
            .addOnFailureListener { fail() }
    }

    private fun loadSubs() {
        db.collection("homeworkSubmissions").whereEqualTo("homeworkId", hwId).get()
            .addOnSuccessListener { r ->
                subs = r.documents.map {
                    Sub(
                        it.getString("studentName") ?: "طالب",
                        (it.getLong("score") ?: 0L).toInt(), (it.getLong("total") ?: 0L).toInt()
                    )
                }
                render()
            }
            .addOnFailureListener { fail() }
    }

    private fun render() {
        container.removeAllViews()
        val now = System.currentTimeMillis()
        container.addView(tv("📚 $title", 22f, true))
        container.addView(
            tv(
                (if (deadline < now) "🔒 انتهى الموعد: " else "⏰ آخر موعد: ") + HomeworkUtil.fmtDate(deadline) +
                        (if (deadline > now) "\nباقي ${HomeworkUtil.remaining(deadline - now)}" else ""), 14f
            )
        )

        val submitted = subs.map { it.name }.toSet()
        val missing = audience.filter { it !in submitted }.sorted()
        val avg = if (subs.isEmpty()) 0 else subs.sumOf { if (it.total > 0) it.score * 100 / it.total else 0 } / subs.size
        container.addView(tv("سلّم ${subs.size} من ${audience.size}" + (if (subs.isNotEmpty()) " · المتوسط $avg%" else ""), 16f, true))

        val ed = Button(this)
        ed.text = "⏰ تعديل الموعد"
        ed.setOnClickListener { editDeadline() }
        container.addView(ed)

        container.addView(tv("❌ ما سلّموش (${missing.size})", 18f, true))
        if (missing.isEmpty()) {
            container.addView(tv("الكل سلّم 👏", 14f))
        } else {
            val all = Button(this)
            all.isAllCaps = false
            all.text = "📩 ذكّر كل اللي ما سلّموش (${missing.size}) داخل التطبيق"
            all.setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("تبعت تذكير لـ ${missing.size} طالب؟")
                    .setPositiveButton("ابعت") { _, _ -> sendNotices(missing) }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            container.addView(all)
            missing.forEach { name ->
                val b = Button(this)
                b.isAllCaps = false
                b.text = name
                b.setOnClickListener { studentMenu(name) }
                container.addView(b)
            }
        }

        container.addView(tv("✅ سلّموا (${subs.size})", 18f, true))
        if (subs.isEmpty()) container.addView(tv("لسه محدش سلّم.", 14f))
        subs.sortedByDescending { if (it.total > 0) it.score * 100 / it.total else 0 }.forEach {
            val p = if (it.total > 0) it.score * 100 / it.total else 0
            container.addView(tv("• ${it.name} — ${it.score}/${it.total} ($p%)", 15f))
        }
    }

    private fun noticeText() =
        "⚠️ تذكير من أ. $teacherName: لسه ما سلّمتش واجب «$title». آخر موعد ${HomeworkUtil.fmtDate(deadline)}."

    private fun sendNotices(names: List<String>) {
        val now = System.currentTimeMillis()
        val text = noticeText()
        names.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { n ->
                batch.set(
                    db.collection("notices").document("${hwId}_${n.replace("/", "-")}"),
                    hashMapOf<String, Any>(
                        "studentName" to n, "teacherCode" to code, "teacherName" to teacherName,
                        "homeworkId" to hwId, "title" to title, "text" to text, "createdAt" to now
                    )
                )
            }
            batch.commit()
                .addOnSuccessListener { Toast.makeText(this, "تم إرسال التذكير ✅", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { Toast.makeText(this, "فشل الإرسال", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun studentMenu(name: String) {
        val parent = roster.firstOrNull { it.name == name }?.parent ?: ""
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("📩 رسالة تحذير داخل التطبيق", "💬 واتساب لولي الأمر", "📞 اتصل بولي الأمر")) { _, which ->
                when (which) {
                    0 -> sendNotices(listOf(name))
                    1 -> {
                        val digits = parent.filter { it.isDigit() }
                        if (digits.isEmpty()) {
                            Toast.makeText(this, "مفيش رقم ولي أمر متسجّل", Toast.LENGTH_SHORT).show()
                        } else {
                            val msg = "السلام عليكم، نفيدكم بأن الطالب/ة $name لم يسلّم واجب «$title» " +
                                    "المطلوب تسليمه قبل ${HomeworkUtil.fmtDate(deadline)}. برجاء المتابعة. — أ. $teacherName"
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(msg)))
                            )
                        }
                    }
                    2 -> {
                        if (parent.isEmpty()) Toast.makeText(this, "مفيش رقم ولي أمر متسجّل", Toast.LENGTH_SHORT).show()
                        else startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$parent")))
                    }
                    else -> {}
                }
            }
            .show()
    }

    private fun editDeadline() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val c = Calendar.getInstance()
                c.set(y, m, d, h, min, 0)
                c.set(Calendar.MILLISECOND, 0)
                db.collection("homeworks").document(hwId).update("deadline", c.timeInMillis)
                    .addOnSuccessListener { load() }
                    .addOnFailureListener { Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show() }
            }, 23, 0, false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
}
