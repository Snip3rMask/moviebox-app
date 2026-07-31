package msr.pistream.shared.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonInfo(
    val se: Int = 0,
    val maxEp: Int = 0
)
