package msr.pistream.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import msr.pistream.app.R
import msr.pistream.shared.data.model.Staff

/** Horizontal cast row (avatar + name + role) for the details page. */
class CastAdapter(
    private val items: List<Staff>
) : RecyclerView.Adapter<CastAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.avatar)
        val initial: TextView = v.findViewById(R.id.initial)
        val name: TextView = v.findViewById(R.id.name)
        val character: TextView = v.findViewById(R.id.character)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cast, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.character.text = item.character
        val url = item.avatarUrl.takeIf { it.isNotBlank() }
        holder.initial.text = item.name.firstOrNull()?.uppercase() ?: ""
        holder.avatar.visibility = if (url == null) View.GONE else View.VISIBLE
        holder.initial.visibility = if (url == null) View.VISIBLE else View.GONE
        if (url != null) {
            holder.avatar.load(url) {
                crossfade(true)
                placeholder(R.color.surface_alt)
            }
        }
    }
}
