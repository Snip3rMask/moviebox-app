package msr.pistream.app.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import msr.pistream.app.R
import msr.pistream.shared.data.MovieBoxRepository
import java.util.Locale

class PlayerActivity : AppCompatActivity() {

    private val repo = MovieBoxRepository()

    private var player: ExoPlayer? = null
    private var switchJob: Job? = null
    private var settingsDialog: BottomSheetDialog? = null

    private var se = 0
    private var ep = 0
    private var currentDubId: String? = null
    private val dubIds = ArrayList<String>()
    private val dubNames = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra("url") ?: run {
            finish()
            return
        }
        val cookie = intent.getStringExtra("cookie").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        se = intent.getIntExtra("se", 0)
        ep = intent.getIntExtra("ep", 0)
        currentDubId = intent.getStringExtra("subjectId")
        intent.getStringArrayListExtra("dubIds")?.let { dubIds.addAll(it) }
        intent.getStringArrayListExtra("dubNames")?.let { dubNames.addAll(it) }

        val playerView = findViewById<PlayerView>(R.id.playerView)
        findViewById<TextView>(R.id.playerTitle).text = title
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.settingsBtn).setOnClickListener { showSettings() }
        playerView.setControllerVisibilityListener(
            object : PlayerView.ControllerVisibilityListener {
                override fun onControllerVisibilityChange(visibility: Int) {
                    findViewById<View>(R.id.topBar).isVisible = visibility == View.VISIBLE
                }
            }
        )

        setupPlayer(url, cookie)
    }

    // ---- player lifecycle ----

    private fun setupPlayer(url: String, cookie: String) {
        val playerView = findViewById<PlayerView>(R.id.playerView)
        playerView.player = null
        player?.release()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (cookie.isNotEmpty()) {
                    setDefaultRequestProperties(mapOf("Cookie" to cookie))
                }
            }

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(this, dataSourceFactory))
            )
            .build()
        player = p
        playerView.player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    error.errorCodeName + ": " + (error.cause?.message ?: error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    private fun reloadWithDub(dubId: String) {
        if (dubId == currentDubId) return
        switchJob?.cancel()
        switchJob = lifecycleScope.launch {
            try {
                val stream = repo.streamFor(dubId, se, ep)
                if (stream == null) {
                    Toast.makeText(this@PlayerActivity, R.string.no_streams, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val pos = player?.currentPosition ?: 0L
                currentDubId = dubId
                setupPlayer(stream.url, stream.signCookie ?: "")
                player?.seekTo(pos)
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, R.string.failed_to_load, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- settings sheet ----

    private fun showSettings() {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.sheet_playback)
        dialog.findViewById<View>(R.id.sheetCloseBtn)?.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            rebuildSettings(dialog)
        }
        settingsDialog = dialog
        dialog.show()
    }

    private fun rebuildSettings(dialog: BottomSheetDialog) {
        val content = dialog.findViewById<LinearLayout>(R.id.sheetContent) ?: return
        content.removeAllViews()
        val tracks = player?.currentTracks?.groups.orEmpty()

        // ---- Quality ----
        addSectionTitle(content, getString(R.string.quality))
        val videoGroups = tracks.filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        if (videoGroups.isEmpty()) {
            addNote(content, getString(R.string.no_quality))
        } else {
            val group = videoGroups.first()
            val params = player?.trackSelectionParameters
            val override = params?.overrides?.get(group.mediaTrackGroup)
            val fixed = override != null && override.tracks.isNotEmpty()
            if (group.mediaTrackGroup.length > 1) {
                addRow(content, getString(R.string.auto), !fixed) {
                    setQuality(group, null)
                    rebuildSettings(dialog)
                }
            }
            for (i in 0 until group.mediaTrackGroup.length) {
                val f = group.mediaTrackGroup.getFormat(i)
                val selected = if (fixed) override!!.tracks.contains(i) else i == selectedTrackIndex(group)
                addRow(content, qualityLabel(f), selected) {
                    setQuality(group, i)
                    rebuildSettings(dialog)
                }
            }
        }

        // ---- Audio (embedded tracks) ----
        addSectionTitle(content, getString(R.string.audio))
        val audioGroups = tracks.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (audioGroups.isEmpty()) {
            addNote(content, getString(R.string.no_audio))
        } else {
            for (g in audioGroups) {
                for (i in 0 until g.mediaTrackGroup.length) {
                    val f = g.mediaTrackGroup.getFormat(i)
                    addRow(content, languageName(f), i == selectedTrackIndex(g)) {
                        setAudio(g, i)
                        rebuildSettings(dialog)
                    }
                }
            }
        }

        // ---- Subtitles ----
        addSectionTitle(content, getString(R.string.subtitles))
        val textGroups = tracks.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        if (textGroups.isEmpty()) {
            addNote(content, getString(R.string.no_subtitles))
        } else {
            val anySelected = textGroups.any { it.isSelected }
            addRow(content, getString(R.string.off), !anySelected) {
                setSubtitles(null)
                rebuildSettings(dialog)
            }
            for (g in textGroups) {
                for (i in 0 until g.mediaTrackGroup.length) {
                    val f = g.mediaTrackGroup.getFormat(i)
                    addRow(content, languageName(f), i == selectedTrackIndex(g)) {
                        setSubtitles(TrackSelectionOverride(g.mediaTrackGroup, ImmutableList.of(i)))
                        rebuildSettings(dialog)
                    }
                }
            }
        }

        // ---- Audio (dub switch) ----
        if (dubIds.size > 1) {
            addSectionTitle(content, getString(R.string.dub))
            for (i in dubIds.indices) {
                addRow(content, dubNames[i], dubIds[i] == currentDubId) {
                    if (dubIds[i] != currentDubId) {
                        dialog.dismiss()
                        reloadWithDub(dubIds[i])
                    }
                }
            }
        }
    }

    // ---- track selection ----

    private fun selectedTrackIndex(g: Tracks.Group): Int {
        for (i in 0 until g.length) {
            if (g.isTrackSelected(i)) return i
        }
        return -1
    }

    private fun setQuality(g: Tracks.Group, index: Int?) {
        val p = player ?: return
        val b = p.trackSelectionParameters.buildUpon()
        b.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (index != null) {
            b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ImmutableList.of(index)))
        }
        p.trackSelectionParameters = b.build()
    }

    private fun setAudio(g: Tracks.Group, index: Int) {
        val p = player ?: return
        val b = p.trackSelectionParameters.buildUpon()
        b.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ImmutableList.of(index)))
        p.trackSelectionParameters = b.build()
    }

    private fun setSubtitles(override: TrackSelectionOverride?) {
        val p = player ?: return
        val b = p.trackSelectionParameters.buildUpon()
        b.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (override != null) {
            b.setOverrideForType(override)
        }
        p.trackSelectionParameters = b.build()
    }

    // ---- labels ----

    private fun qualityLabel(f: Format): String {
        return when {
            f.height > 0 -> "${f.height}p"
            !f.label.isNullOrBlank() -> f.label!!
            else -> getString(R.string.no_quality)
        }
    }

    private fun languageName(f: Format): String {
        val parts = mutableListOf<String>()
        if (!f.language.isNullOrBlank()) {
            val display = Locale(f.language).getDisplayLanguage(Locale.ENGLISH)
            parts.add(if (display.isNotBlank()) display else f.language!!.uppercase())
        }
        if (!f.label.isNullOrBlank()) parts.add(f.label!!)
        return parts.joinToString(" • ").ifBlank { getString(R.string.no_quality) }
    }

    // ---- sheet UI builders ----

    private fun addSectionTitle(parent: LinearLayout, title: String) {
        parent.addView(
            TextView(this).apply {
                text = title
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(4))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun addNote(parent: LinearLayout, text: String) {
        parent.addView(
            TextView(this).apply {
                this.text = text
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(0, dp(6), 0, dp(2))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun addRow(parent: LinearLayout, label: String, selected: Boolean, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            setBackgroundResource(android.R.attr.selectableItemBackground)
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(
            TextView(this).apply {
                text = label
                setTextColor(getColor(if (selected) R.color.accent else R.color.text_primary))
                textSize = 14f
                if (selected) setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                isVisible = selected
            },
            LinearLayout.LayoutParams(dp(18), dp(18))
        )
        parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---- activity lifecycle ----

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        switchJob?.cancel()
        settingsDialog?.dismiss()
        settingsDialog = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
