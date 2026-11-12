package com.example.bac1

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class TasksActivity : AppCompatActivity() {

    private class Task(
        val id: String,
        var title: String,
        val weekly: Boolean,
        var days: MutableList<Int>,
        var done: Boolean,
        val doneKeys: MutableSet<String>
    )

    private val tasks = mutableListOf<Task>()
    private var showWeekly = false

    private lateinit var listBox: LinearLayout
    private lateinit var btnGeneral: Button
    private lateinit var btnWeekly: Button

    private val dayOrder = listOf(
        Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
        Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
    )
    private val dayNames = mapOf(
        Calendar.SATURDAY to "السبت", Calendar.SUNDAY to "الأحد",
        Calendar.MONDAY to "الاثنين", Calendar.TUESDAY to "الثلاثاء",
        Calendar.WEDNESDAY to "الأربعاء", Calendar.THURSDAY to "الخميس",
        Calendar.FRIDAY to "الجمعة"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val prefs by lazy { getSharedPreferences("bac1_tasks", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        listBox = findViewById(R.id.tasksContainer)
        btnGeneral = findViewById(R.id.btnTabGeneral)
        btnWeekly = findViewById(R.id.btnTabWeekly)

        btnGeneral.setOnClickListener { showWeekly = false; render() }
        btnWeekly.setOnClickListener { showWeekly = true; render() }
        findViewById<Button>(R.id.btnAddTask).setOnClickListener { showTaskDialog(null) }

        load()
        render()
    }

    // ---------- التخزين ----------
    private fun weekStartKey(): String {
        val c = Calendar.getInstance()
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) c.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    private fun load() {
        tasks.clear()
        val weekStart = weekStartKey()
        try {
            val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val days = mutableListOf<Int>()
                o.optJSONArray("days")?.let { a -> for (j in 0 until a.length()) days.add(a.getInt(j)) }
                val keys = mutableSetOf<String>()
                o.optJSONArray("doneKeys")?.let { a ->
                    for (j in 0 until a.length()) {
                        val k = a.getString(j)
                        // مسح علامات الأسابيع القديمة
                        if (k.endsWith("_$weekStart")) keys.add(k)
                    }
                }
                tasks.add(
                    Task(
                        o.getString("id"), o.getString("title"), o.optBoolean("weekly", false),
                        days, o.optBoolean("done", false), keys
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun save() {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("weekly", t.weekly)
                put("days", JSONArray(t.days))
                put("done", t.done)
                put("doneKeys", JSONArray(t.doneKeys.toList()))
            })
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    // ---------- العرض ----------
    private fun render() {
        val on = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        val off = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
        btnGeneral.backgroundTintList = if (showWeekly) off else on
        btnWeekly.backgroundTintList = if (showWeekly) on else off

        listBox.removeAllViews()
        val list = tasks.filter { it.weekly == showWeekly }

        if (list.isEmpty()) {
            val empty = TextView(this)
            empty.text = if (showWeekly) "مفيش مهام أسبوعية لسه.\nاضغط \"إضافة مهمة\" وحدد الأيام."
            else "مفيش مهام لسه.\nاضغط \"إضافة مهمة\" وابدأ."
            empty.gravity = Gravity.CENTER
            empty.textSize = 15f
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            empty.setPadding(0, dp(40), 0, 0)
            listBox.addView(empty)
            return
        }

        if (!showWeekly) {
            list.sortedBy { it.done }.forEach { listBox.addView(taskRow(it, null, "")) }
        } else {
            val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val weekStart = weekStartKey()
            for (d in dayOrder) {
                val dayTasks = list.filter { d in it.days }
                if (dayTasks.isEmpty()) continue
                val h = TextView(this)
                h.text = (dayNames[d] ?: "") + if (d == todayDay) "  (اليوم)" else ""
                h.textSize = 17f
                h.setTypeface(null, Typeface.BOLD)
                h.setTextColor(
                    if (d == todayDay) Color.parseColor("#2E7D32")
                    else ContextCompat.getColor(this, R.color.text_dark)
                )
                h.setPadding(dp(4), dp(14), dp(4), dp(4))
                listBox.addView(h)
                dayTasks.forEach { listBox.addView(taskRow(it, d, weekStart)) }
            }
        }
    }

    private fun taskRow(t: Task, day: Int?, weekStart: String): View {
        val isDone = if (day == null) t.done else t.doneKeys.contains("${day}_$weekStart")

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundColor(Color.WHITE)
        row.setPadding(dp(8), dp(6), dp(8), dp(6))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(6)
        row.layoutParams = lp

        val cb = CheckBox(this)
        cb.text = t.title
        cb.textSize = 16f
        cb.setTextColor(
            if (isDone) Color.parseColor("#9E9E9E")
            else ContextCompat.getColor(this, R.color.text_dark)
        )
        cb.paintFlags = if (isDone) cb.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else cb.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        cb.isChecked = isDone
        cb.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        cb.setOnCheckedChangeListener { _, checked ->
            if (day == null) {
                t.done = checked
            } else {
                val k = "${day}_$weekStart"
                if (checked) t.doneKeys.add(k) else t.doneKeys.remove(k)
            }
            save()
            render()
        }
        row.addView(cb)

        val edit = TextView(this)
        edit.text = "✏️"
        edit.textSize = 20f
        edit.setPadding(dp(10), dp(6), dp(10), dp(6))
        edit.setOnClickListener { showTaskDialog(t) }
        row.addView(edit)

        val del = TextView(this)
        del.text = "🗑️"
        del.textSize = 20f
        del.setPadding(dp(10), dp(6), dp(10), dp(6))
        del.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("حذف المهمة")
                .setMessage("متأكد إنك عايز تحذف \"${t.title}\"؟")
                .setPositiveButton("احذف") { _, _ ->
                    tasks.remove(t)
                    save()
                    render()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
        row.addView(del)
        return row
    }

    // ---------- إضافة / تعديل ----------
    private fun showTaskDialog(existing: Task?) {
        val weekly = existing?.weekly ?: showWeekly

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(10), dp(20), dp(0))

        val input = EditText(this)
        input.hint = "اكتب المهمة"
        input.setText(existing?.title ?: "")
        input.setSelection(input.text.length)
        box.addView(input)

        val dayChecks = mutableMapOf<Int, CheckBox>()
        if (weekly) {
            val lbl = TextView(this)
            lbl.text = "اختر الأيام:"
            lbl.setPadding(0, dp(12), 0, dp(4))
            box.addView(lbl)
            for (d in dayOrder) {
                val c = CheckBox(this)
                c.text = dayNames[d]
                c.isChecked = existing?.days?.contains(d) ?: false
                dayChecks[d] = c
                box.addView(c)
            }
        }

        val scroll = android.widget.ScrollView(this)
        scroll.addView(box)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "مهمة جديدة" else "تعديل المهمة")
            .setView(scroll)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = input.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "اكتب المهمة الأول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val days = dayChecks.filter { it.value.isChecked }.keys.toMutableList()
            if (weekly && days.isEmpty()) {
                Toast.makeText(this, "اختر يوم واحد على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (existing == null) {
                tasks.add(Task(UUID.randomUUID().toString(), title, weekly, days, false, mutableSetOf()))
            } else {
                existing.title = title
                if (weekly) {
                    existing.days = days
                    existing.doneKeys.clear()
                }
            }
            save()
            render()
            dialog.dismiss()
        }
    }
}
