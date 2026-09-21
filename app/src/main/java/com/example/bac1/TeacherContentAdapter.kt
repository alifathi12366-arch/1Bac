package com.example.bac1

import android.content.Intent
import android.net.Uri
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
        val linkText: TextView = view.findViewById(R.id.contentLinkText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher_content, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.typeLabel.text = if (item.type == "message") "✉️ رسالة" else item.type
        holder.contentText.text = item.text

        if (item.youtubeUrl.isNotBlank()) {
            holder.linkText.visibility = View.VISIBLE
            holder.linkText.text = "▶️ فتح رابط الدرس / يوتيوب"
            holder.linkText.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.youtubeUrl))
                holder.itemView.context.startActivity(intent)
            }
        } else {
            holder.linkText.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}
