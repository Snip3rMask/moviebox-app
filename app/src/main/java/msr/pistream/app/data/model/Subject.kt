package msr.pistream.app.data.model

data class Subject(
    val subjectId: String,
    val subjectType: Int,
    val title: String,
    val description: String,
    val releaseDate: String,
    val duration: String,
    val genre: String,
    val coverUrl: String?,
    val countryName: String,
    val imdbRating: String,
    val hasResource: Boolean,
    val language: String,
    val isCam: Boolean,
    val subtitles: String,
    val dubs: List<Dub> = emptyList(),
)
