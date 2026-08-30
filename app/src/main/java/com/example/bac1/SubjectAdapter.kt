package com.example.bac1

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubjectAdapter(private val subjects: List<Subject>) :
    RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

    class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.subjectIcon)
        val name: TextView = itemView.findViewById(R.id.subjectName)
        val desc: TextView = itemView.findViewById(R.id.subjectDesc)
        val iconBox: LinearLayout = itemView.findViewById(R.id.iconBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjects[position]
        holder.name.text = subject.name
        holder.desc.text = subject.description
        holder.icon.setImageResource(subject.iconRes)
        holder.iconBox.setBackgroundColor(Color.parseColor(subject.colorHex))
    }

    override fun getItemCount(): Int = subjects.size
}