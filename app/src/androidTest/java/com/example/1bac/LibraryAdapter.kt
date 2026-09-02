package com.example.bac1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LibraryAdapter(
    private var items: MutableList<LibraryItem>,
    private val onItemClick: (LibraryItem) -> Unit,
    private val onDeleteClick: (LibraryItem) -> Unit
) : RecyclerView.Adapter<LibraryAdapter.LibraryViewHolder>() {

    class LibraryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemIcon: ImageView = view.findViewById(R.id.itemIcon)
        val itemName: TextView = view.findViewById(R.id.itemName)
        val itemSubject: TextView = view.findViewById(R.id.itemSubject)
        val deleteIcon: ImageView = view.findViewById(R.id.deleteIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library, parent, false)
        return LibraryViewHolder(view)
    }

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        val item = items[position]
        holder.itemName.text = item.fileName
        holder.itemSubject.text = item.subject

        val iconRes = when (item.fileType) {
            "image" -> android.R.drawable.ic_menu_gallery
            "pdf" -> android.R.drawable.ic_menu_agenda
            "video" -> android.R.drawable.ic_media_play
            "audio" -> android.R.drawable.ic_btn_speak_now
            "note" -> android.R.drawable.ic_menu_edit
            else -> android.R.drawable.ic_menu_gallery
        }
        holder.itemIcon.setImageResource(iconRes)

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.deleteIcon.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<LibraryItem>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }
}
