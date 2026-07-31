package msr.pistream.shared.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayStream(
    val format: String = "",
    val url: String = "",
    val resolutions: String = "",
    val signCookie: String? = null,
    val id: String = ""
)
