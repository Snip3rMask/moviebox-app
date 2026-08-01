package msr.pistream.shared.data

import msr.pistream.shared.data.model.Dub
import msr.pistream.shared.data.model.Episode
import msr.pistream.shared.data.model.PlayStream
import msr.pistream.shared.data.model.SeasonInfo
import msr.pistream.shared.data.model.Subject
import msr.pistream.shared.data.model.SubjectCaption

/**
 * High-level access to MovieBox data.
 *
 * Hides the signed HTTP details ([MovieBoxApi]) and adds the stream fallback
 * logic: when the requested subject has no streams, every dub id is tried.
 */
class MovieBoxRepository(
    private val api: MovieBoxApi = MovieBoxApi
) {

    /** A playable stream plus the subject id that actually owns it. */
    data class ResolvedStream(val stream: PlayStream, val subjectId: String)

    suspend fun ensureLogin() {
        if (api.token == null) api.loginAnonymous()
    }

    suspend fun search(keyword: String, page: Int = 1): List<Subject> {
        ensureLogin()
        return api.search(keyword, page)
    }

    suspend fun homeRow(categoryType: String, page: Int = 1): List<Subject> {
        ensureLogin()
        return api.homeRow(categoryType, page)
    }

    suspend fun detail(subjectId: String): Subject? {
        ensureLogin()
        return api.detail(subjectId)
    }

    suspend fun seasons(subjectId: String): List<SeasonInfo> {
        ensureLogin()
        return api.seasons(subjectId)
    }

    /**
     * Returns the first playable stream for [subjectId] at the given
     * season/episode. Movies use se=0&ep=0, series use the real values.
     * Falls back to every dub id when the requested one has no streams.
     */
    suspend fun playStream(
        subjectId: String,
        se: Int,
        ep: Int,
        dubs: List<Dub> = emptyList()
    ): PlayStream? = resolveStream(subjectId, se, ep, dubs)?.stream

    /**
     * Returns the first playable stream for [subjectId] at the given
     * season/episode, together with the subject id that owns it. Movies use
     * se=0&ep=0, series use the real values. Falls back to every dub id when
     * the requested one has no streams.
     */
    suspend fun resolveStream(
        subjectId: String,
        se: Int,
        ep: Int,
        dubs: List<Dub> = emptyList()
    ): ResolvedStream? {
        ensureLogin()
        val candidates = LinkedHashSet<String>()
        candidates.add(subjectId)
        dubs.forEach { candidates.add(it.subjectId) }
        for (id in candidates) {
            val streams = api.playInfo(id, se, ep)
            if (streams.isNotEmpty()) return ResolvedStream(streams.first(), id)
        }
        return null
    }

    /** Separate subtitle tracks for a stream (web player caption endpoint). */
    suspend fun captions(
        subjectId: String,
        streamId: String,
        format: String,
        detailPath: String
    ): List<SubjectCaption> {
        ensureLogin()
        return api.captions(subjectId, streamId, format, detailPath)
    }

    /**
     * Returns the first playable stream for a specific subject (used for
     * switching dub/audio inside the player without touching other dubs).
     */
    suspend fun streamFor(subjectId: String, se: Int, ep: Int): PlayStream? {
        ensureLogin()
        return api.playInfo(subjectId, se, ep).firstOrNull()
    }

    /** Builds the episode list for a series from its season info. */
    fun buildEpisodes(
        subjectId: String,
        seasons: List<SeasonInfo>,
        label: (se: Int, ep: Int) -> String = { se, ep -> "Season $se  •  Episode $ep" }
    ): List<Episode> {
        val eps = ArrayList<Episode>()
        for (s in seasons) {
            for (n in 1..s.maxEp) {
                eps.add(
                    Episode(
                        subjectId = subjectId,
                        se = s.se,
                        ep = n,
                        label = label(s.se, n)
                    )
                )
            }
        }
        return eps
    }
}
