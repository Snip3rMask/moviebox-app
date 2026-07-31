package com.msr.moviebox.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.msr.moviebox.R
import com.msr.moviebox.data.Episode
import com.msr.moviebox.data.Subject

class PosterAdapter(
    private val items: List<Subject>,
    private val onClick: (Subject) -> Unit,
    private val itemWidthDp: Int = 0
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.poster)
        val title: TextView = v.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poster, parent, false)
        if (itemWidthDp > 0) {
            val px = (itemWidthDp * parent.resources.displayMetrics.density).toInt()
            v.layoutParams = ViewGroup.LayoutParams(px, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.poster.load(item.coverUrl) {
            crossfade(true)
            placeholder(R.color.surface)
        }
        holder.title.text = item.title
        holder.itemView.setOnClickListener { onClick(item) }
    }
}

class EpisodeAdapter(
    private val items: List<Episode>,
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.episodeName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
