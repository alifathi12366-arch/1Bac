package com.example.bac1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TeacherContentAdapter(
    private val items: List<TeacherContentItem>
) : RecyclerView.Adapter<TeacherContentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeLabel: TextView = view.findViewById(R.id.contentTypeLabel)
        val contentText: TextView = view.findViewById(R.id.contentText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher_content, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.typeLabel.text = if (item.type == "message") "✉️ رسالة" else item.type
        holder.contentText.text = item.text
    }

    override fun getItemCount(): Int = items.size
}
