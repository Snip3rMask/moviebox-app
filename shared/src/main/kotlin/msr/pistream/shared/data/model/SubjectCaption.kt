package msr.pistream.shared.data.model

import kotlinx.serialization.Serializable

/**
 * A separate subtitle track for a stream, served by the web player's caption
 * endpoint. Subtitles are NOT embedded in the DASH/HLS streams; they are
 * loaded as standalone SRT files and sideloaded into the player.
 */
@Serializable
data class SubjectCaption(
    val id: String = "",
    val lan: String = "",
    val lanName: String = "",
    val url: String = "",
    val size: String = "",
    val delay: Int = 0
)
