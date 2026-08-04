package msr.pistream.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import msr.pistream.app.R
import msr.pistream.shared.data.model.Episode

/** Episode list for the player's "up next" section with a selected highlight. */
class PlayerEpisodeAdapter(
    private val items: List<Episode>,
    private val isSelected: (Episode) -> Boolean,
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<PlayerEpisodeAdapter.VH>() {

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
        val selected = isSelected(item)
        holder.name.setTextColor(
            holder.itemView.context.getColor(if (selected) R.color.accent else R.color.text_primary)
        )
        holder.itemView.background = ContextCompat.getDrawable(
            holder.itemView.context,
            if (selected) R.drawable.bg_episode_selected else R.drawable.bg_episode_card
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
