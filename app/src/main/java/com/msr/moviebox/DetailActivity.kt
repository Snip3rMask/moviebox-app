package com.msr.moviebox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.msr.moviebox.data.Dub
import com.msr.moviebox.data.Episode
import com.msr.moviebox.data.MovieBoxApi
import com.msr.moviebox.data.Subject
import com.msr.moviebox.ui.EpisodeAdapter
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private var subjectId: String? = null
    private var subject: Subject? = null
    private var selectedDubId: String? = null
    private var episodes: List<Episode> = emptyList()

    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var ratingView: TextView
    private lateinit var descView: TextView
    private lateinit var playBtn: com.google.android.material.button.MaterialButton
    private lateinit var dubChips: ChipGroup
    private lateinit var dubsLabel: TextView
    private lateinit var episodesLabel: TextView
    private lateinit var episodesList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        subjectId = intent.getStringExtra("subjectId")
        titleView = findViewById(R.id.title)
        metaView = findViewById(R.id.meta)
        ratingView = findViewById(R.id.rating)
        descView = findViewById(R.id.description)
        playBtn = findViewById(R.id.playBtn)
        dubChips = findViewById(R.id.dubChips)
        dubsLabel = findViewById(R.id.dubsLabel)
        episodesLabel = findViewById(R.id.episodesLabel)
        episodesList = findViewById(R.id.episodesList)
        episodesList.layoutManager = LinearLayoutManager(this)

        playBtn.setOnClickListener { playSelected() }

        lifecycleScope.launch {
            try {
                val d = MovieBoxApi.detail(subjectId ?: return@launch) ?: return@launch
                subject = d
                render(d)
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, getString(R.string.failed_to_load), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render(d: Subject) {
        findViewById<ImageView>(R.id.poster).load(d.coverUrl) {
            crossfade(true)
            placeholder(R.color.surface)
        }
        titleView.text = d.title
        val meta = listOf(
            d.releaseDate.take(4).takeIf { it.isNotBlank() },
            d.duration.takeIf { it.isNotBlank() },
            d.genre.takeIf { it.isNotBlank() },
            d.countryName.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString("  •  ")
        metaView.text = meta
        ratingView.text = d.imdbRating.takeIf { it.isNotBlank() }?.let { "IMDb $it" } ?: ""
        ratingView.isVisible = d.imdbRating.isNotBlank()
        descView.text = d.description
        descView.isVisible = d.description.isNotBlank()

        val isSeries = d.subjectType == 2
        playBtn.text = getString(R.string.play)

        // dubs
        if (d.dubs.size > 1) {
            dubsLabel.isVisible = true
            for (dub in d.dubs) addDubChip(dub)
        } else {
            dubsLabel.isVisible = false
        }

        if (isSeries) {
            episodesLabel.isVisible = true
            loadSeasons(d)
        } else {
            episodesLabel.isVisible = false
        }
    }

    private fun addDubChip(dub: Dub) {
        val chip = Chip(this)
        chip.text = dub.lanName
        chip.isCheckable = true
        dubChips.addView(chip)
        chip.setOnClickListener {
            if (chip.isChecked) {
                selectedDubId = dub.subjectId
            }
        }
    }

    private fun loadSeasons(d: Subject) {
        lifecycleScope.launch {
            try {
                val seasons = MovieBoxApi.seasons(d.subjectId)
                val eps = ArrayList<Episode>()
                for (s in seasons) {
                    for (ep in 1..s.maxEp) {
                        eps.add(
                            Episode(
                                subjectId = d.subjectId,
                                se = s.se,
                                ep = ep,
                                label = getString(R.string.season) + " ${s.se}  •  " +
                                    getString(R.string.episode) + " $ep"
                            )
                        )
                    }
                }
                episodes = eps
                episodesList.adapter = EpisodeAdapter(eps) { playEpisode(it) }
            } catch (e: Exception) {
                episodesLabel.isVisible = false
            }
        }
    }

    private fun playSelected() {
        val d = subject ?: return
        val sid = selectedDubId ?: d.subjectId
        if (d.subjectType == 2) {
            val first = episodes.firstOrNull() ?: return
            playEpisode(first)
        } else {
            play(sid, 1, 1)
        }
    }

    private fun playEpisode(ep: Episode) {
        play(ep.subjectId, ep.se, ep.ep)
    }

    private fun play(subjectId: String, se: Int, ep: Int) {
        lifecycleScope.launch {
            try {
                val streams = MovieBoxApi.playInfo(subjectId, se, ep)
                val stream = streams.firstOrNull()
                if (stream == null) {
                    Toast.makeText(this@DetailActivity, R.string.no_streams, Toast.LENGTH_LONG).show()
                    return@launch
                }
                startActivity(
                    Intent(this@DetailActivity, PlayerActivity::class.java)
                        .putExtra("url", stream.url)
                        .putExtra("cookie", stream.signCookie ?: "")
                        .putExtra("title", subject?.title ?: "")
                )
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, getString(R.string.failed_to_load), Toast.LENGTH_LONG).show()
            }
        }
    }
}
