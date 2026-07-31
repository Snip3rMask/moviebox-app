package msr.pistream.shared.data.model

import kotlinx.serialization.Serializable

/** A cast/crew member returned by the subject detail endpoint. */
@Serializable
data class Staff(
    val staffId: String = "",
    /** 1 = actor, 2 = director, 3 = writer. */
    val staffType: Int = 0,
    val name: String = "",
    /** Actor role name, or "Director"/"Writer" for crew. */
    val character: String = "",
    val avatarUrl: String = ""
)
