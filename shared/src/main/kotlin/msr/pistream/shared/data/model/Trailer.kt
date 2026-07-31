package msr.pistream.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrailerVideo(
    val videoId: String = "",
    val definition: String = "",
    val url: String = "",
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0,
    val fps: Int = 0,
    val bitrate: Long = 0,
    val type: Int = 0
)

/** Trailer object from the detail endpoint (keys are capitalized upstream). */
@Serializable
data class Trailer(
    @SerialName("VideoAddress") val videoAddress: TrailerVideo? = null,
    val cover: Cover? = null
)
