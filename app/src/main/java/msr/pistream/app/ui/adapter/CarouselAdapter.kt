package msr.pistream.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import msr.pistream.app.R
import msr.pistream.shared.data.model.Subject

/** Hero carousel item adapter for the home screen (ViewPager2). */
class CarouselAdapter(
    private val items: List<Subject>,
    private val onClick: (Subject) -> Unit
) : RecyclerView.Adapter<CarouselAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.carouselPoster)
        val title: TextView = v.findViewById(R.id.carouselTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carousel, parent, false)
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
