package com.example.bac1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LeaderboardAdapter(
    private val students: List<Pair<String, Int>>
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rankText: TextView = view.findViewById(R.id.rankText)
        val crownText: TextView = view.findViewById(R.id.crownText)
        val nameText: TextView = view.findViewById(R.id.nameText)
        val scoreText: TextView = view.findViewById(R.id.scoreText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, points) = students[position]
        val rank = position + 1

        holder.rankText.text = rank.toString()
        holder.nameText.text = name
        holder.scoreText.text = "$points نقطة"

        if (rank == 1) {
            holder.crownText.visibility = View.VISIBLE
            holder.crownText.text = "👑"
            holder.nameText.text = "$name (بطل الشهر)"
        } else {
            holder.crownText.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = students.size
}
