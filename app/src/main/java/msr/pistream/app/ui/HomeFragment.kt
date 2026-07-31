package msr.pistream.app.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import msr.pistream.app.R
import msr.pistream.app.data.MovieBoxRepository
import msr.pistream.app.data.model.Subject
import msr.pistream.app.ui.adapter.CarouselAdapter
import msr.pistream.app.ui.adapter.PosterAdapter

class HomeFragment : Fragment() {

    private data class Row(val title: String, val categoryType: String)

    private val repo = MovieBoxRepository()

    private val rows = listOf(
        Row("Trending", "4516404531735022304"),
        Row("Trending in Cinema", "5692654647815587592"),
        Row("Bollywood", "414907768299210008"),
        Row("South Indian", "3859721901924910512"),
        Row("Hollywood", "8019599703232971616"),
        Row("Top Series This Week", "4741626294545400336"),
        Row("Anime", "8434602210994128512"),
        Row("Korean Drama", "7878715743607948784"),
        Row("Turkish Drama", "5177200225164885656"),
    )

    private lateinit var searchInput: EditText
    private lateinit var carousel: ViewPager2
    private lateinit var carouselSection: LinearLayout
    private lateinit var dotsIndicator: LinearLayout
    private lateinit var rowsContainer: LinearLayout
    private var dots: List<View> = emptyList()
    private var carouselCount = 0
    private var carouselJob: Job? = null

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updateDots(position)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_home, container, false)
        searchInput = v.findViewById(R.id.searchInput)
        carousel = v.findViewById(R.id.carousel)
        carouselSection = v.findViewById(R.id.carouselSection)
        dotsIndicator = v.findViewById(R.id.dotsIndicator)
        rowsContainer = v.findViewById(R.id.rowsContainer)

        // Let the outer NestedScrollView handle vertical scrolling.
        (carousel.getChildAt(0) as? RecyclerView)?.isNestedScrollingEnabled = false
        carousel.registerOnPageChangeCallback(pageCallback)

        v.findViewById<MaterialButton>(R.id.searchBtn).setOnClickListener { startSearch() }
        searchInput.setOnEditorActionListener { _, _, _ ->
            startSearch()
            true
        }
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            try {
                repo.ensureLogin()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Login failed", Toast.LENGTH_LONG).show()
                return@launch
            }
            loadCarousel()
            for (row in rows) addRow(row.title, row.categoryType)
        }
    }

    override fun onDestroyView() {
        carouselJob?.cancel()
        carouselJob = null
        carousel.unregisterOnPageChangeCallback(pageCallback)
        super.onDestroyView()
    }

    private fun startSearch() {
        val q = searchInput.text.toString().trim()
        if (q.isBlank()) return
        startActivity(
            Intent(requireContext(), SearchActivity::class.java).putExtra("query", q)
        )
    }

    private suspend fun loadCarousel() {
        try {
            val items = repo.homeRow(CATEGORY_TRENDING).take(10)
            if (items.isEmpty()) {
                carouselSection.isVisible = false
                return
            }
            carouselCount = items.size
            carousel.adapter = CarouselAdapter(items) { openDetail(it) }
            setupDots(items.size)
            startAutoScroll()
        } catch (e: Exception) {
            carouselSection.isVisible = false
        }
    }

    private fun setupDots(count: Int) {
        dotsIndicator.removeAllViews()
        val density = resources.displayMetrics.density
        dots = List(count) { i ->
            val dot = View(requireContext())
            val size = ((if (i == 0) 10 else 8) * density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            dot.layoutParams = lp
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    if (i == 0) resources.getColor(R.color.accent, null)
                    else resources.getColor(R.color.text_secondary, null)
                )
            }
            dotsIndicator.addView(dot)
            dot
        }
    }

    private fun updateDots(position: Int) {
        if (dots.isEmpty()) return
        val density = resources.displayMetrics.density
        dots.forEachIndexed { i, dot ->
            val size = ((if (i == position) 10 else 8) * density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            dot.layoutParams = lp
            (dot.background as? GradientDrawable)?.setColor(
                if (i == position) resources.getColor(R.color.accent, null)
                else resources.getColor(R.color.text_secondary, null)
            )
        }
    }

    private fun startAutoScroll() {
        carouselJob?.cancel()
        carouselJob = lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                if (carouselCount > 1 && carousel.adapter != null) {
                    carousel.setCurrentItem((carousel.currentItem + 1) % carouselCount, true)
                }
            }
        }
    }

    private fun addRow(title: String, categoryType: String) {
        val row = layoutInflater.inflate(R.layout.item_row, rowsContainer, false)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        val list = row.findViewById<RecyclerView>(R.id.rowList)
        list.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rowsContainer.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lifecycleScope.launch {
            try {
                val items = repo.homeRow(categoryType)
                if (items.isEmpty()) {
                    rowsContainer.removeView(row)
                    return@launch
                }
                list.adapter = PosterAdapter(items, { openDetail(it) }, itemWidthDp = 130)
            } catch (e: Exception) {
                rowsContainer.removeView(row)
            }
        }
    }

    private fun openDetail(subject: Subject) {
        startActivity(
            Intent(requireContext(), DetailActivity::class.java)
                .putExtra("subjectId", subject.subjectId)
        )
    }

    companion object {
        private const val CATEGORY_TRENDING = "4516404531735022304"
    }
}
