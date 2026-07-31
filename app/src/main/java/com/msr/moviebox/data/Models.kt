package com.msr.moviebox.data

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

data class Dub(
    val subjectId: String,
    val lanName: String,
    val original: Boolean,
)

data class SeasonInfo(
    val se: Int,
    val maxEp: Int,
)

data class PlayStream(
    val format: String,
    val url: String,
    val resolutions: String,
    val signCookie: String?,
    val id: String,
)

data class Episode(
    val subjectId: String,
    val se: Int,
    val ep: Int,
    val label: String,
)
