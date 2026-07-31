package msr.pistream.app.data.model

data class PlayStream(
    val format: String,
    val url: String,
    val resolutions: String,
    val signCookie: String?,
    val id: String,
)
