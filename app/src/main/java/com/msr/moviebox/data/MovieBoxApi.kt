package com.msr.moviebox.data

import android.util.Base64
import com.msr.moviebox.data.model.Dub
import com.msr.moviebox.data.model.PlayStream
import com.msr.moviebox.data.model.SeasonInfo
import com.msr.moviebox.data.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signed HTTP client for the MovieBox mobile API.
 *
 * Auth: anonymous JWT returned in the `x-user` response header of a ping request.
 * Every request is signed with x-client-token + x-tr-signature (HmacMD5 over a
 * canonical string). See [ApiConfig] for the constants.
 */
object MovieBoxApi {

    @Volatile
    var token: String? = null
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun md5Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun xClientToken(): String {
        val ts = System.currentTimeMillis().toString()
        return "$ts,${md5Hex(ts.reversed().toByteArray())}"
    }

    private fun canonicalUrl(url: String): String {
        val uri = URI(url)
        val pairs = (uri.rawQuery ?: "")
            .split("&")
            .filter { it.isNotEmpty() }
            .map { it.split("=", limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } } }
            .sortedWith(compareBy({ it.first }, { it.second }))
        val query = if (pairs.isEmpty()) "" else pairs.joinToString("&") { "${it.first}=${it.second}" }
        return uri.path + if (query.isEmpty()) "" else "?$query"
    }

    private fun signature(method: String, url: String, body: String?): String {
        val ts = System.currentTimeMillis().toString()
        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val bodyHash = bodyBytes?.let { md5Hex(it.copyOfRange(0, minOf(it.size, 102400))) } ?: ""
        val bodyLen = bodyBytes?.size?.toString() ?: ""
        val canonical = listOf(
            method.uppercase(),
            "application/json",
            "application/json",
            bodyLen,
            ts,
            bodyHash,
            canonicalUrl(url)
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(ApiConfig.SIGNING_KEY, "HmacMD5"))
        val raw = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        return "$ts|2|${Base64.encodeToString(raw, Base64.NO_WRAP)}"
    }

    private fun headers(method: String, url: String, body: String?): Map<String, String> {
        val h = linkedMapOf<String, String>()
        h["user-agent"] = ApiConfig.USER_AGENT
        h["accept"] = "application/json"
        h["content-type"] = "application/json"
        h["connection"] = "keep-alive"
        h["x-client-token"] = xClientToken()
        h["x-tr-signature"] = signature(method, url, body)
        h["x-client-info"] = ApiConfig.CLIENT_INFO
        h["x-client-status"] = "0"
        if (method == "POST") h["x-play-mode"] = "2"
        token?.let { h["Authorization"] = "Bearer $it" }
        return h
    }

    private suspend fun rawGet(path: String): Pair<JSONObject, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val url = ApiConfig.BASE_URL + path
            val req = Request.Builder().url(url).apply {
                headers(method = "GET", url = url, body = null).forEach { (k, v) -> addHeader(k, v) }
            }.get().build()
            http.newCall(req).execute().use { resp ->
                val body = JSONObject(resp.body?.string() ?: "{}")
                body to resp.headers.toMultimap().mapValues { it.value.joinToString(";") }
            }
        }

    private suspend fun rawPost(path: String, json: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url = ApiConfig.BASE_URL + path
            val payload = json.toString()
            val req = Request.Builder().url(url).apply {
                headers(method = "POST", url = url, body = payload).forEach { (k, v) -> addHeader(k, v) }
            }.post(payload.toRequestBody(null)).build()
            http.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string() ?: "{}")
            }
        }

    private fun JSONObject.optIntSafe(key: String): Int =
        try { optInt(key) } catch (_: Exception) { 0 }

    private fun parseSubject(o: JSONObject): Subject {
        val cover = o.optJSONObject("cover")
        val dubsArr = o.optJSONArray("dubs")
        val dubs = ArrayList<Dub>()
        if (dubsArr != null) {
            for (i in 0 until dubsArr.length()) {
                val d = dubsArr.optJSONObject(i) ?: continue
                dubs.add(
                    Dub(
                        subjectId = d.optString("subjectId"),
                        lanName = d.optString("lanName"),
                        original = d.optBoolean("original", false)
                    )
                )
            }
        }
        return Subject(
            subjectId = o.optString("subjectId"),
            subjectType = o.optIntSafe("subjectType"),
            title = o.optString("title"),
            description = o.optString("description"),
            releaseDate = o.optString("releaseDate"),
            duration = o.optString("duration"),
            genre = o.optString("genre"),
            coverUrl = cover?.optString("url")?.takeIf { it.isNotBlank() },
            countryName = o.optString("countryName"),
            imdbRating = o.optString("imdbRatingValue"),
            hasResource = o.optBoolean("hasResource", false),
            language = o.optString("language"),
            isCam = o.optBoolean("isCam", false),
            subtitles = o.optString("subtitles"),
            dubs = dubs
        )
    }

    private fun subjectsFromArray(arr: JSONArray?): List<Subject> {
        if (arr == null) return emptyList()
        val out = LinkedHashMap<String, Subject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val s = parseSubject(o)
            out[s.subjectId] = s
        }
        return out.values.toList()
    }

    suspend fun loginAnonymous() {
        val (_, hdrs) = rawGet(ApiConfig.ANONYMOUS_LOGIN_PATH)
        val xUser = hdrs["x-user"] ?: error("login failed: no x-user header")
        token = JSONObject(xUser).optString("token").takeIf { it.isNotBlank() } ?: error("login failed")
    }

    suspend fun homeRow(categoryType: String, page: Int = 1): List<Subject> {
        val (json, _) = rawGet(
            "/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=$categoryType&page=$page&perPage=15"
        )
        return subjectsFromArray(json.optJSONObject("data")?.optJSONArray("subjects"))
    }

    suspend fun list(channelId: String = "1", page: Int = 1, classify: String = "All",
                     country: String = "All", genre: String = "All"): List<Subject> {
        val body = JSONObject().apply {
            put("page", page); put("perPage", 15); put("channelId", channelId)
            put("classify", classify); put("country", country)
            put("year", "All"); put("genre", genre); put("sort", "ForYou")
        }
        val json = rawPost("/wefeed-mobile-bff/subject-api/list", body)
        return subjectsFromArray(json.optJSONObject("data")?.optJSONArray("items"))
    }

    suspend fun search(keyword: String, page: Int = 1): List<Subject> {
        val body = JSONObject().apply {
            put("page", page); put("perPage", 20); put("keyword", keyword)
        }
        val json = rawPost("/wefeed-mobile-bff/subject-api/search/v2", body)
        val results = json.optJSONObject("data")?.optJSONArray("results") ?: JSONArray()
        val out = LinkedHashMap<String, Subject>()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            for (s in subjectsFromArray(r.optJSONArray("subjects"))) out[s.subjectId] = s
        }
        return out.values.toList()
    }

    suspend fun detail(subjectId: String): Subject? {
        val (json, _) = rawGet("/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId")
        val data = json.optJSONObject("data") ?: return null
        return if (data.isNull("subjectId")) null else parseSubject(data)
    }

    suspend fun seasons(subjectId: String): List<SeasonInfo> {
        val (json, _) = rawGet("/wefeed-mobile-bff/subject-api/season-info?subjectId=$subjectId")
        val seasons = json.optJSONObject("data")?.optJSONArray("seasons") ?: JSONArray()
        val out = ArrayList<SeasonInfo>()
        for (i in 0 until seasons.length()) {
            val s = seasons.optJSONObject(i) ?: continue
            out.add(SeasonInfo(se = s.optIntSafe("se"), maxEp = s.optIntSafe("maxEp")))
        }
        return out
    }

    suspend fun playInfo(subjectId: String, se: Int, ep: Int): List<PlayStream> {
        val (json, _) = rawGet(
            "/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$se&ep=$ep"
        )
        val streams = json.optJSONObject("data")?.optJSONArray("streams") ?: JSONArray()
        val out = ArrayList<PlayStream>()
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            out.add(
                PlayStream(
                    format = s.optString("format"),
                    url = s.optString("url"),
                    resolutions = s.optString("resolutions"),
                    signCookie = s.optString("signCookie").takeIf { it.isNotBlank() },
                    id = s.optString("id")
                )
            )
        }
        return out
    }
}
