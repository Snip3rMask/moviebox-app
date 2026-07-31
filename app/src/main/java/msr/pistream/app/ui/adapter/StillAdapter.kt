package msr.pistream.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import msr.pistream.app.R
import msr.pistream.shared.data.model.Cover

/** Horizontal stills/screenshot row for the details page. */
class StillAdapter(
    private val items: List<Cover>
) : RecyclerView.Adapter<StillAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val still: ImageView = v.findViewById(R.id.still)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_still, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        if (item.url.isNotBlank()) {
            holder.still.load(item.url) {
                crossfade(true)
                placeholder(R.color.surface)
            }
        }
    }
}
