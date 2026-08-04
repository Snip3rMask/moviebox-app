package msr.pistream.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val stillsJson = Json { ignoreUnknownKeys = true }

@Serializable
data class Cover(val url: String = "")

@Serializable
data class Subject(
    val subjectId: String = "",
    @SerialName("detailUrl") val detailUrl: String = "",
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
    val aka: String = "",
    val durationSeconds: Long = 0,
    val contentRating: String = "",
    val viewers: Long = 0,
    val haveSeenCount: Long = 0,
    val wantToSeeCount: Long = 0,
    val staffList: List<Staff> = emptyList(),
    val trailer: Trailer? = null,
    val stills: JsonElement? = null,
    val dubs: List<Dub> = emptyList()
) {
    @Transient
    val coverUrl: String? get() = cover?.url?.takeIf { it.isNotBlank() }

    /**
     * MovieBox returns `stills` either as a list of images or as a single
     * image object depending on the title, so the raw element is kept and
     * normalized here.
     */
    @Transient
    val stillsList: List<Cover>
        get() = when (val s = stills) {
            null -> emptyList()
            is JsonArray -> s.mapNotNull { el ->
                runCatching { stillsJson.decodeFromString<Cover>(el.toString()) }.getOrNull()
            }
            is JsonObject -> listOfNotNull(
                runCatching { stillsJson.decodeFromString<Cover>(s.toString()) }.getOrNull()
            )
            else -> emptyList()
        }
}
