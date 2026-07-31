package msr.pistream.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Cover(val url: String = "")

@Serializable
data class Subject(
    val subjectId: String = "",
    val subjectType: Int = 0,
    val title: String = "",
    val description: String = "",
    val releaseDate: String = "",
    val duration: String = "",
    val genre: String = "",
    val cover: Cover? = null,
    val countryName: String = "",
    @SerialName("imdbRatingValue") val imdbRating: String = "",
    val hasResource: Boolean = false,
    val language: String = "",
    val isCam: Boolean = false,
    val subtitles: String = "",
    val dubs: List<Dub> = emptyList()
) {
    @Transient
    val coverUrl: String? get() = cover?.url?.takeIf { it.isNotBlank() }
}
