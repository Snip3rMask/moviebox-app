package msr.pistream.shared.data.model

/** A single playable episode (built locally from season info). */
data class Episode(
    val subjectId: String,
    val se: Int,
    val ep: Int,
    val label: String
)
