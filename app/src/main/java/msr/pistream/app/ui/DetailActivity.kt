package msr.pistream.app.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import msr.pistream.app.R
import msr.pistream.app.ui.adapter.CastAdapter
import msr.pistream.app.ui.adapter.EpisodeAdapter
import msr.pistream.app.ui.adapter.StillAdapter
import msr.pistream.shared.data.MovieBoxRepository
import msr.pistream.shared.data.model.Episode
import msr.pistream.shared.data.model.SeasonInfo
import msr.pistream.shared.data.model.Subject

class DetailActivity : AppCompatActivity() {

    private val repo = MovieBoxRepository()

    private var subject: Subject? = null
    private var selectedDubId: String? = null
    private var seasons: List<SeasonInfo> = emptyList()
    private var episodes: List<Episode> = emptyList()
    private var descExpanded = false

    private lateinit var backdrop: ImageView
    private lateinit var titleView: TextView
    private lateinit var akaView: TextView
    private lateinit var metaChips: ChipGroup
    private lateinit var statsLine: TextView
    private lateinit var playBtn: MaterialButton
    private lateinit var trailerBtn: MaterialButton
    private lateinit var descView: TextView
    private lateinit var crewLine: TextView
    private lateinit var castLabel: TextView
    private lateinit var castList: RecyclerView
    private lateinit var dubsLabel: TextView
    private lateinit var dubChips: ChipGroup
    private lateinit var episodesLabel: TextView
    private lateinit var seasonChips: ChipGroup
    private lateinit var episodesList: RecyclerView
    private lateinit var photosLabel: TextView
    private lateinit var stillsList: RecyclerView
    private lateinit var loading: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        // Full-bleed hero behind the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = getColor(R.color.bg)

        backdrop = findViewById(R.id.backdrop)
        titleView = findViewById(R.id.title)
        akaView = findViewById(R.id.aka)
        metaChips = findViewById(R.id.metaChips)
        statsLine = findViewById(R.id.statsLine)
        playBtn = findViewById(R.id.playBtn)
        trailerBtn = findViewById(R.id.trailerBtn)
        descView = findViewById(R.id.description)
        crewLine = findViewById(R.id.crewLine)
        castLabel = findViewById(R.id.castLabel)
        castList = findViewById(R.id.castList)
        dubsLabel = findViewById(R.id.dubsLabel)
        dubChips = findViewById(R.id.dubChips)
        episodesLabel = findViewById(R.id.episodesLabel)
        seasonChips = findViewById(R.id.seasonChips)
        episodesList = findViewById(R.id.episodesList)
        photosLabel = findViewById(R.id.photosLabel)
        stillsList = findViewById(R.id.stillsList)
        loading = findViewById(R.id.loading)

        castList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        stillsList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        episodesList.layoutManager = GridLayoutManager(this, 2)

        findViewById<ImageView>(R.id.backBtn).setOnClickListener { finish() }
        applyBackInsets(findViewById(R.id.backBtn))
        applyContentInsets(findViewById(R.id.content))

        playBtn.setOnClickListener { playSelected() }

        lifecycleScope.launch {
            try {
                val d = repo.detail(intent.getStringExtra("subjectId") ?: return@launch)
                    ?: return@launch
                subject = d
                render(d)
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, R.string.failed_to_load, Toast.LENGTH_LONG).show()
            } finally {
                loading.isVisible = false
            }
        }
    }

    private fun applyBackInsets(btn: View) {
        ViewCompat.setOnApplyWindowInsetsListener(btn) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val lp = (v.layoutParams as ViewGroup.MarginLayoutParams)
            lp.topMargin = top + (8 * resources.displayMetrics.density).toInt()
            v.layoutParams = lp
            insets
        }
    }

    private fun applyContentInsets(content: View) {
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + bottom)
            insets
        }
    }

    private fun render(d: Subject) {
        val hero = d.trailer?.cover?.url?.takeIf { it.isNotBlank() } ?: d.coverUrl
        backdrop.load(hero) {
            crossfade(true)
            placeholder(R.color.surface)
        }
        titleView.text = d.title
        if (d.aka.isNotBlank()) {
            akaView.text = d.aka
            akaView.isVisible = true
        }
        setupMeta(d)
        setupDescription(d)
        setupCrew(d)
        setupCast(d)
        setupDubs(d)
        setupStills(d)
        setupTrailer(d)
        if (d.subjectType == 2) {
            loadSeasons(d)
        }
    }

    // ---- meta tags ----

    private fun setupMeta(d: Subject) {
        metaChips.removeAllViews()
        if (d.imdbRating.isNotBlank()) addTag(metaChips, "IMDb ${d.imdbRating}", star = true)
        d.releaseDate.take(4).takeIf { it.isNotBlank() }?.let { addTag(metaChips, it) }
        formatDuration(d)?.let { addTag(metaChips, it) }
        d.genre.takeIf { it.isNotBlank() }?.let { addTag(metaChips, it) }
        d.countryName.takeIf { it.isNotBlank() }?.let { addTag(metaChips, it) }
        d.language.takeIf { it.isNotBlank() }?.let { addTag(metaChips, it) }
        d.subtitles.takeIf { it.isNotBlank() }?.let { addTag(metaChips, "Subtitles: $it") }
        d.contentRating.takeIf { it.isNotBlank() }?.let { addTag(metaChips, it) }
        if (d.isCam) addTag(metaChips, getString(R.string.cam), danger = true)

        if (d.viewers > 0) {
            statsLine.text = getString(R.string.viewers_count, formatCount(d.viewers))
            statsLine.isVisible = true
        }
    }

    private fun addTag(
        group: ChipGroup,
        text: String,
        star: Boolean = false,
        danger: Boolean = false
    ) {
        val density = resources.displayMetrics.density
        val chip = Chip(this)
        chip.text = text
        chip.isClickable = false
        chip.isCheckable = false
        chip.chipBackgroundColor = ColorStateList.valueOf(getColor(R.color.surface_alt))
        chip.chipStrokeWidth = 0f
        chip.setTextColor(getColor(if (danger) R.color.danger else R.color.text_primary))
        chip.textSize = 12f
        chip.chipMinHeight = 28 * density
        chip.chipCornerRadius = 8 * density
        chip.setEnsureMinTouchTargetSize(false)
        if (star) {
            chip.chipIcon = ContextCompat.getDrawable(this, R.drawable.ic_star)
            chip.chipIconSize = (14 * density)
            chip.chipIconTint = ColorStateList.valueOf(getColor(R.color.accent))
            chip.chipStartPadding = (6 * density)
        }
        group.addView(chip)
    }

    private fun formatDuration(d: Subject): String? {
        if (d.duration.isNotBlank()) return d.duration
        if (d.durationSeconds > 0) {
            val h = d.durationSeconds / 3600
            val m = (d.durationSeconds % 3600) / 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m}m"
            }
        }
        return null
    }

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    // ---- description ----

    private fun setupDescription(d: Subject) {
        if (d.description.isBlank()) {
            descView.isVisible = false
            return
        }
        descView.isVisible = true
        descExpanded = false
        val longText = d.description.length > 180
        descView.maxLines = if (longText) 3 else Int.MAX_VALUE
        if (longText) descView.ellipsize = TextUtils.TruncateAt.END
        descView.text = d.description + if (longText) "\n${getString(R.string.show_more)}" else ""
        if (longText) {
            descView.setOnClickListener {
                descExpanded = !descExpanded
                descView.maxLines = if (descExpanded) Int.MAX_VALUE else 3
                descView.ellipsize = if (descExpanded) null else TextUtils.TruncateAt.END
                descView.text = d.description + if (descExpanded) "\n${getString(R.string.show_less)}" else "\n${getString(R.string.show_more)}"
            }
        }
    }

    // ---- crew + cast ----

    private fun setupCrew(d: Subject) {
        val directors = LinkedHashSet<String>()
        val writers = LinkedHashSet<String>()
        for (s in d.staffList) {
            if (s.name.isBlank()) continue
            when (s.staffType) {
                2 -> directors.add(s.name)
                3 -> writers.add(s.name)
            }
        }
        val parts = mutableListOf<String>()
        if (directors.isNotEmpty()) parts.add(getString(R.string.director) + ": " + directors.joinToString(", "))
        if (writers.isNotEmpty()) parts.add(getString(R.string.writer) + ": " + writers.joinToString(", "))
        if (parts.isEmpty()) return
        crewLine.text = parts.joinToString("  •  ")
        crewLine.isVisible = true
    }

    private fun setupCast(d: Subject) {
        val seen = LinkedHashSet<String>()
        val actors = d.staffList.filter {
            it.staffType == 1 && it.name.isNotBlank() && seen.add(it.staffId)
        }
        if (actors.isEmpty()) return
        castLabel.isVisible = true
        castList.isVisible = true
        castList.adapter = CastAdapter(actors)
    }

    // ---- dubs ----

    private fun setupDubs(d: Subject) {
        if (d.dubs.size <= 1) return
        dubsLabel.isVisible = true
        dubChips.isVisible = true
        dubChips.isSingleSelection = true
        dubChips.removeAllViews()
        for (dub in d.dubs) {
            dubChips.addView(choiceChip(dub.lanName) { if (it.isChecked) selectedDubId = dub.subjectId })
        }
        val originalIndex = d.dubs.indexOfFirst { it.original }
        if (originalIndex >= 0) {
            (dubChips.getChildAt(originalIndex) as? Chip)?.isChecked = true
        }
    }

    private fun choiceChip(text: String, onClick: (Chip) -> Unit): Chip {
        val density = resources.displayMetrics.density
        return Chip(this).apply {
            this.text = text
            isCheckable = true
            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(getColor(R.color.primary), getColor(R.color.surface_alt))
            )
            chipStrokeWidth = 0f
            setTextColor(getColor(R.color.text_primary))
            chipMinHeight = 36 * density
            chipCornerRadius = 10 * density
            setEnsureMinTouchTargetSize(false)
            setOnClickListener { onClick(this) }
        }
    }

    // ---- episodes ----

    private fun loadSeasons(d: Subject) {
        lifecycleScope.launch {
            try {
                val seasonList = repo.seasons(d.subjectId)
                if (seasonList.isEmpty()) return@launch
                seasons = seasonList
                seasonChips.removeAllViews()
                seasonChips.isSingleSelection = true
                seasonChips.isVisible = seasonList.size > 1
                for (s in seasonList) {
                    seasonChips.addView(choiceChip(getString(R.string.season) + " " + s.se) {
                        if (it.isChecked) loadEpisodes(d, s.se)
                    })
                }
                (seasonChips.getChildAt(0) as? Chip)?.isChecked = true
                loadEpisodes(d, seasonList.first().se)
            } catch (e: Exception) {
                episodesLabel.isVisible = false
            }
        }
    }

    private fun loadEpisodes(d: Subject, se: Int) {
        val s = seasons.firstOrNull { it.se == se } ?: return
        episodes = (1..s.maxEp).map { n ->
            Episode(
                subjectId = d.subjectId,
                se = se,
                ep = n,
                label = getString(R.string.season) + " $se  •  " + getString(R.string.episode) + " $n"
            )
        }
        episodesLabel.isVisible = true
        episodesList.isVisible = true
        episodesList.adapter = EpisodeAdapter(episodes) { playEpisode(it) }
    }

    // ---- stills + trailer ----

    private fun setupStills(d: Subject) {
        val stills = d.stills?.filter { it.url.isNotBlank() }.orEmpty()
        if (stills.isEmpty()) return
        photosLabel.isVisible = true
        stillsList.isVisible = true
        stillsList.adapter = StillAdapter(stills)
    }

    private fun setupTrailer(d: Subject) {
        val url = d.trailer?.videoAddress?.url?.takeIf { it.isNotBlank() } ?: return
        trailerBtn.isVisible = true
        trailerBtn.setOnClickListener {
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra("url", url)
                    .putExtra("cookie", "")
                    .putExtra("title", d.title + " — Trailer")
            )
        }
    }

    // ---- play ----

    private fun playSelected() {
        val d = subject ?: return
        val sid = selectedDubId ?: d.subjectId
        if (d.subjectType == 2) {
            val first = episodes.firstOrNull() ?: return
            playEpisode(first)
        } else {
            play(sid, 0, 0)
        }
    }

    private fun playEpisode(ep: Episode) {
        play(ep.subjectId, ep.se, ep.ep)
    }

    private fun play(subjectId: String, se: Int, ep: Int) {
        val d = subject ?: return
        lifecycleScope.launch {
            try {
                val resolved = repo.resolveStream(subjectId, se, ep, d.dubs) ?: run {
                    Toast.makeText(this@DetailActivity, R.string.no_streams, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val chosen = resolved.stream
                val detailPath = d.detailUrl.trimEnd('/').substringAfterLast('/')
                val idName = LinkedHashMap<String, String>()
                d.dubs.forEach { idName[it.subjectId] = it.lanName }
                idName.putIfAbsent(subjectId, "Default")
                startActivity(
                    Intent(this@DetailActivity, PlayerActivity::class.java)
                        .putExtra("url", chosen.url)
                        .putExtra("cookie", chosen.signCookie ?: "")
                        .putExtra("title", d.title)
                        .putExtra("subjectId", subjectId)
                        .putExtra("dubIds", ArrayList(idName.keys))
                        .putExtra("dubNames", ArrayList(idName.values))
                        .putExtra("se", se)
                        .putExtra("ep", ep)
                        .putExtra("streamOwnerId", resolved.subjectId)
                        .putExtra("streamId", chosen.id)
                        .putExtra("streamFormat", chosen.format)
                        .putExtra("detailPath", detailPath)
                )
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, R.string.failed_to_load, Toast.LENGTH_LONG).show()
            }
        }
    }
}
