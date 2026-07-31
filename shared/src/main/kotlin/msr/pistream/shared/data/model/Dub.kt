package msr.pistream.shared.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Dub(
    val subjectId: String = "",
    val lanName: String = "",
    val original: Boolean = false
)
