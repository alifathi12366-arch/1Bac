package com.example.bac1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

data class Lesson(
    val name: String,
    val page: Int
)

class LessonAdapter(
    private val lessons: MutableList<Lesson>,
    private val prefsKeyPrefix: String,
    private val prefs: android.content.SharedPreferences,
    private val onProgressChanged: () -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    class LessonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val lessonName: TextView = view.findViewById(R.id.lessonName)
        val lessonPage: TextView = view.findViewById(R.id.lessonPage)
        val lessonCheckbox: CheckBox = view.findViewById(R.id.lessonCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lesson, parent, false)
        return LessonViewHolder(view)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        val lesson = lessons[position]
        holder.lessonName.text = lesson.name
        holder.lessonPage.text = "صفحة ${lesson.page}"

        val key = "$prefsKeyPrefix-$position"
        holder.lessonCheckbox.setOnCheckedChangeListener(null)
        holder.lessonCheckbox.isChecked = prefs.getBoolean(key, false)

        holder.lessonCheckbox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
            onProgressChanged()
        }
    }

    override fun getItemCount(): Int = lessons.size
}
