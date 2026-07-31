package msr.pistream.app.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import msr.pistream.app.R
import msr.pistream.shared.data.MovieBoxRepository
import msr.pistream.shared.data.model.Subject
import msr.pistream.app.ui.adapter.CarouselAdapter
import msr.pistream.app.ui.adapter.PosterAdapter
import java.util.concurrent.atomic.AtomicInteger

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

    private lateinit var carousel: ViewPager2
    private lateinit var dotsIndicator: LinearLayout
    private lateinit var rowsContainer: LinearLayout
    private lateinit var skeletonContainer: LinearLayout
    private var dots: List<View> = emptyList()
    private var carouselCount = 0
    private var carouselJob: Job? = null
    private var skeletonAnimator: ValueAnimator? = null

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
        carousel = v.findViewById(R.id.carousel)
        dotsIndicator = v.findViewById(R.id.dotsIndicator)
        rowsContainer = v.findViewById(R.id.rowsContainer)
        skeletonContainer = v.findViewById(R.id.skeletonContainer)

        // Let the outer NestedScrollView handle vertical scrolling.
        (carousel.getChildAt(0) as? RecyclerView)?.isNestedScrollingEnabled = false
        carousel.registerOnPageChangeCallback(pageCallback)

        // Push the app bar below the status bar (content stays edge-to-edge).
        val topBar = v.findViewById<View>(R.id.topBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val density = resources.displayMetrics.density
            view.setPadding(
                view.paddingLeft,
                top + (8 * density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        v.findViewById<ImageButton>(R.id.searchButton).setOnClickListener { openSearch() }
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showSkeleton()
        lifecycleScope.launch {
            try {
                repo.ensureLogin()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Login failed", Toast.LENGTH_LONG).show()
                hideSkeleton()
                return@launch
            }
            val remaining = AtomicInteger(rows.size + 1)
            fun done() {
                if (remaining.decrementAndGet() == 0) hideSkeleton()
            }
            lifecycleScope.launch {
                try { loadCarousel() } finally { done() }
            }
            for (row in rows) {
                lifecycleScope.launch {
                    try { addRow(row.title, row.categoryType) } finally { done() }
                }
            }
        }
    }

    override fun onDestroyView() {
        carouselJob?.cancel()
        carouselJob = null
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        carousel.unregisterOnPageChangeCallback(pageCallback)
        super.onDestroyView()
    }

    private fun openSearch() {
        startActivity(Intent(requireContext(), SearchActivity::class.java))
    }

    private suspend fun loadCarousel() {
        try {
            val items = repo.homeRow(CATEGORY_TRENDING).take(10)
            if (items.isEmpty()) {
                carousel.isVisible = false
                return
            }
            carouselCount = items.size
            carousel.adapter = CarouselAdapter(items) { openDetail(it) }
            setupDots(items.size)
            startAutoScroll()
        } catch (e: Exception) {
            carousel.isVisible = false
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

    private suspend fun addRow(title: String, categoryType: String) {
        val row = layoutInflater.inflate(R.layout.item_row, rowsContainer, false)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        val list = row.findViewById<RecyclerView>(R.id.rowList)
        list.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rowsContainer.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        try {
            val items = repo.homeRow(categoryType)
            if (items.isEmpty()) {
                rowsContainer.removeView(row)
                return
            }
            list.adapter = PosterAdapter(items, { openDetail(it) }, itemWidthDp = 130)
        } catch (e: Exception) {
            rowsContainer.removeView(row)
        }
    }

    private fun showSkeleton() {
        skeletonContainer.isVisible = true
        skeletonAnimator?.cancel()
        skeletonAnimator = ValueAnimator.ofFloat(0.4f, 1f, 0.4f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { skeletonContainer.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun hideSkeleton() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        skeletonContainer.alpha = 1f
        skeletonContainer.isVisible = false
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
