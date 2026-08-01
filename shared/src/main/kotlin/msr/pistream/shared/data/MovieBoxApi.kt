package msr.pistream.shared.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import msr.pistream.shared.data.model.PlayStream
import msr.pistream.shared.data.model.SeasonInfo
import msr.pistream.shared.data.model.Subject
import msr.pistream.shared.data.model.SubjectCaption
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
        return "$ts|2|${Base64Encoder.encode(raw)}"
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

    private suspend fun rawGet(path: String): Pair<JsonElement, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val url = ApiConfig.BASE_URL + path
            val req = Request.Builder().url(url).apply {
                headers(method = "GET", url = url, body = null).forEach { (k, v) -> addHeader(k, v) }
            }.get().build()
            http.newCall(req).execute().use { resp ->
                val body = runCatching { json.parseToJsonElement(resp.body?.string() ?: "{}") }
                    .getOrElse { JsonObject(emptyMap()) }
                body to resp.headers.toMultimap().mapValues { it.value.joinToString(";") }
            }
        }

    private suspend fun rawPost(path: String, payload: JsonObject): JsonElement =
        withContext(Dispatchers.IO) {
            val url = ApiConfig.BASE_URL + path
            val body = payload.toString()
            val req = Request.Builder().url(url).apply {
                headers(method = "POST", url = url, body = body).forEach { (k, v) -> addHeader(k, v) }
            }.post(body.toRequestBody(null)).build()
            http.newCall(req).execute().use { resp ->
                runCatching { json.parseToJsonElement(resp.body?.string() ?: "{}") }
                    .getOrElse { JsonObject(emptyMap()) }
            }
        }

    // ---- JSON helpers ----

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

    private fun JsonObject?.arr(key: String): JsonArray? = this?.get(key) as? JsonArray

    private fun JsonObject?.str(key: String): String =
        (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""

    private fun subjectsFromArray(arr: JsonElement?): List<Subject> {
        if (arr !is JsonArray) return emptyList()
        val out = LinkedHashMap<String, Subject>()
        for (el in arr) {
            val s = runCatching { json.decodeFromString<Subject>(el.toString()) }.getOrNull() ?: continue
            out[s.subjectId] = s
        }
        return out.values.toList()
    }

    // ---- endpoints ----

    suspend fun loginAnonymous() {
        val (_, hdrs) = rawGet(ApiConfig.ANONYMOUS_LOGIN_PATH)
        val xUser = hdrs["x-user"] ?: error("login failed: no x-user header")
        val t = runCatching { json.parseToJsonElement(xUser).asObject()?.str("token") }
            .getOrNull().orEmpty()
        token = t.takeIf { it.isNotBlank() } ?: error("login failed")
    }

    suspend fun homeRow(categoryType: String, page: Int = 1): List<Subject> {
        val (body, _) = rawGet(
            "/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=$categoryType&page=$page&perPage=15"
        )
        return subjectsFromArray(body.asObject()?.obj("data")?.arr("subjects"))
    }

    suspend fun list(
        channelId: String = "1",
        page: Int = 1,
        classify: String = "All",
        country: String = "All",
        genre: String = "All"
    ): List<Subject> {
        val payload = buildJsonObject {
            put("page", page)
            put("perPage", 15)
            put("channelId", channelId)
            put("classify", classify)
            put("country", country)
            put("year", "All")
            put("genre", genre)
            put("sort", "ForYou")
        }
        val body = rawPost("/wefeed-mobile-bff/subject-api/list", payload)
        return subjectsFromArray(body.asObject()?.obj("data")?.arr("items"))
    }

    suspend fun search(keyword: String, page: Int = 1): List<Subject> {
        val payload = buildJsonObject {
            put("page", page)
            put("perPage", 20)
            put("keyword", keyword)
        }
        val body = rawPost("/wefeed-mobile-bff/subject-api/search/v2", payload)
        val results = body.asObject()?.obj("data")?.arr("results")
        val out = LinkedHashMap<String, Subject>()
        if (results != null) {
            for (r in results) {
                for (s in subjectsFromArray(r.asObject()?.arr("subjects"))) out[s.subjectId] = s
            }
        }
        return out.values.toList()
    }

    suspend fun detail(subjectId: String): Subject? {
        val (body, _) = rawGet("/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId")
        val data = body.asObject()?.obj("data") ?: return null
        return if (data.str("subjectId").isBlank()) null
        else runCatching { json.decodeFromString<Subject>(data.toString()) }.getOrNull()
    }

    suspend fun seasons(subjectId: String): List<SeasonInfo> {
        val (body, _) = rawGet("/wefeed-mobile-bff/subject-api/season-info?subjectId=$subjectId")
        val seasons = body.asObject()?.obj("data")?.arr("seasons") ?: JsonArray(emptyList())
        return seasons.mapNotNull {
            runCatching { json.decodeFromString<SeasonInfo>(it.toString()) }.getOrNull()
        }
    }

    suspend fun playInfo(subjectId: String, se: Int, ep: Int): List<PlayStream> {
        val (body, _) = rawGet(
            "/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$se&ep=$ep"
        )
        val streams = body.asObject()?.obj("data")?.arr("streams") ?: JsonArray(emptyList())
        return streams.mapNotNull {
            runCatching { json.decodeFromString<PlayStream>(it.toString()) }.getOrNull()
        }
    }

    /**
     * Fetches the separate subtitle tracks for a stream from the web player's
     * caption endpoint. Unlike the mobile API this endpoint is not signed; it
     * is called exactly like the moviebox.ph web player does.
     */
    suspend fun captions(
        subjectId: String,
        streamId: String,
        format: String,
        detailPath: String
    ): List<SubjectCaption> = withContext(Dispatchers.IO) {
        val url = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/caption" +
            "?format=$format&id=$streamId&subjectId=$subjectId&detailPath=$detailPath"
        val req = Request.Builder().url(url)
            .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = runCatching { json.parseToJsonElement(resp.body?.string() ?: "{}") }
                .getOrElse { JsonObject(emptyMap()) }
            val captions = body.asObject()?.obj("data")?.arr("captions") ?: JsonArray(emptyList())
            captions.mapNotNull {
                runCatching { json.decodeFromString<SubjectCaption>(it.toString()) }.getOrNull()
            }
        }
    }
}
