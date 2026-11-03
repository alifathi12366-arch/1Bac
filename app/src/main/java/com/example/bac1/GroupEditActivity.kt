package com.example.bac1

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class GroupEditActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private lateinit var slotsBox: LinearLayout
    private lateinit var studentsBox: LinearLayout
    private lateinit var etName: EditText
    private lateinit var etDur: EditText
    private val slots = mutableListOf<Pair<Int, String>>()
    private val selected = mutableSetOf<String>()
    private var groupId: String? = null
    private var code = ""
    private val dayNames = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(10), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        container = findViewById(R.id.adminContainer)
        code = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""
        groupId = intent.getStringExtra("groupId")

        container.addView(tv(if (groupId == null) "➕ مجموعة جديدة" else "✏️ تعديل المجموعة", 22f, true))

        container.addView(tv("اسم المجموعة"))
        etName = EditText(this)
        etName.hint = "مثال: مجموعة السبت"
        etName.setTextColor(0xFF000000.toInt())
        container.addView(etName)

        container.addView(tv("مدة الحصة بالدقائق"))
        etDur = EditText(this)
        etDur.inputType = InputType.TYPE_CLASS_NUMBER
        etDur.setText("90")
        etDur.setTextColor(0xFF000000.toInt())
        container.addView(etDur)

        container.addView(tv("🕐 مواعيد الحصص", 18f, true))
        slotsBox = LinearLayout(this)
        slotsBox.orientation = LinearLayout.VERTICAL
        container.addView(slotsBox)

        val addSlot = Button(this)
        addSlot.text = "➕ إضافة موعد حصة"
        addSlot.setOnClickListener { pickSlot() }
        container.addView(addSlot)

        container.addView(tv("👥 طلاب المجموعة", 18f, true))
        studentsBox = LinearLayout(this)
        studentsBox.orientation = LinearLayout.VERTICAL
        container.addView(studentsBox)

        val save = Button(this)
        save.text = "💾 حفظ"
        save.setTextColor(0xFFFFFFFF.toInt())
        save.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        save.setOnClickListener { save(save) }
        container.addView(save)

        if (groupId != null) {
            val att = Button(this)
            att.text = "📋 سجل الحضور والغياب"
            att.setOnClickListener {
                startActivity(Intent(this, GroupAttendanceActivity::class.java)
                    .putExtra("groupId", groupId))
            }
            container.addView(att)

            val del = Button(this)
            del.text = "🗑️ مسح المجموعة"
            del.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("مسح المجموعة؟")
                    .setPositiveButton("مسح") { _, _ ->
                        db.collection("teacherGroups").document(groupId!!).delete()
                            .addOnSuccessListener { finish() }
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            container.addView(del)

            db.collection("teacherGroups").document(groupId!!).get().addOnSuccessListener { d ->
                etName.setText(d.getString("name") ?: "")
                etDur.setText((d.getLong("durationMin") ?: 90L).toString())
                (d.get("slots") as? List<*>)?.forEach { s ->
                    val m = s as? Map<*, *> ?: return@forEach
                    val day = (m["day"] as? Number)?.toInt() ?: return@forEach
                    val time = m["time"] as? String ?: return@forEach
                    slots.add(day to time)
                }
                (d.get("studentNames") as? List<*>)?.forEach { (it as? String)?.let { n -> selected.add(n) } }
                renderSlots()
                loadStudents()
            }
        } else {
            loadStudents()
        }
    }

    private fun pickSlot() {
        AlertDialog.Builder(this)
            .setTitle("اختر اليوم")
            .setItems(dayNames) { _, which ->
                TimePickerDialog(this, { _, h, m ->
                    slots.add((which + 1) to "%02d:%02d".format(h, m))
                    renderSlots()
                }, 17, 0, true).show()
            }
            .show()
    }

    private fun renderSlots() {
        slotsBox.removeAllViews()
        if (slots.isEmpty()) slotsBox.addView(tv("لا توجد مواعيد بعد.", 13f))
        slots.forEachIndexed { i, (day, time) ->
            val b = Button(this)
            b.isAllCaps = false
            b.text = "${dayNames[day - 1]} · $time  ✖ (اضغط للحذف)"
            b.setOnClickListener {
                slots.removeAt(i)
                renderSlots()
            }
            slotsBox.addView(b)
        }
    }

    private fun loadStudents() {
        studentsBox.removeAllViews()
        db.collection("users").whereEqualTo("joined_teacher_code", code).get()
            .addOnSuccessListener { res ->
                val names = res.documents.mapNotNull { it.getString("name") }
                    .filter { it.isNotBlank() }.distinct().sorted()
                if (names.isEmpty()) studentsBox.addView(tv("لا يوجد طلاب مسجلون معك بعد.", 13f))
                names.forEach { n ->
                    val cb = CheckBox(this)
                    cb.text = n
                    cb.textSize = 16f
                    cb.setTextColor(0xFF000000.toInt())
                    cb.isChecked = selected.contains(n)
                    cb.setOnCheckedChangeListener { _, on -> if (on) selected.add(n) else selected.remove(n) }
                    studentsBox.addView(cb)
                }
            }
            .addOnFailureListener { studentsBox.addView(tv("تعذّر تحميل الطلاب", 13f)) }
    }

    private fun save(btn: Button) {
        val name = etName.text.toString().trim()
        val dur = etDur.text.toString().trim().toLongOrNull() ?: 90L
        if (name.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "اكتب اسم المجموعة", Toast.LENGTH_SHORT).show()
            return
        }
        val data = hashMapOf<String, Any>(
            "teacherCode" to code,
            "name" to name,
            "durationMin" to dur,
            "slots" to slots.map { hashMapOf("day" to it.first, "time" to it.second) },
            "studentNames" to selected.toList(),
            "updatedAt" to System.currentTimeMillis()
        )
        btn.isEnabled = false
        val col = db.collection("teacherGroups")
        val task = if (groupId == null) {
            data["createdAt"] = System.currentTimeMillis()
            col.add(data)
        } else {
            col.document(groupId!!).update(data)
        }
        task.addOnSuccessListener {
            Toast.makeText(this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            btn.isEnabled = true
            Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show()
        }
    }
}
