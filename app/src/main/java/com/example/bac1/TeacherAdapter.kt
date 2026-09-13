package com.example.bac1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TeacherAdapter(
    private val teachers: List<TeacherItem>
) : RecyclerView.Adapter<TeacherAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.teacherItemName)
        val subjectText: TextView = view.findViewById(R.id.teacherItemSubject)
        val phoneText: TextView = view.findViewById(R.id.teacherItemPhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val teacher = teachers[position]
        holder.nameText.text = teacher.name
        holder.subjectText.text = "مادة: ${teacher.subject}"
        holder.phoneText.text = "📱 ${teacher.phone}"
    }

    override fun getItemCount(): Int = teachers.size
}
