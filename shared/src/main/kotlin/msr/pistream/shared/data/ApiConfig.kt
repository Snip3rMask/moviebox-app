package msr.pistream.shared.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** API constants, device fingerprint and the HMAC signing key. */
object ApiConfig {

    const val BASE_URL = "https://apig.inmoviebox.com"

    /** Ping endpoint that returns the anonymous JWT in the `x-user` header. */
    const val ANONYMOUS_LOGIN_PATH =
        "/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1"

    /**
     * HMAC key — the plugin constant, double base64-decoded.
     * Precomputed bytes (30 bytes) so the shared module has no Base64 dependency.
     */
    val SIGNING_KEY: ByteArray = byteArrayOf(
        -17, -88, -111, -105, 78, -20, -45, 20, -115, -10, 58, -90, 17, 96, 45,
        -17, -47, 1, 37, -101, -91, 33, 2, 44, 87, -82, 5, 102, -67, -114
    )

    const val USER_AGENT =
        "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; " +
            "sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)"

    val DEVICE_ID: String = Random.nextBytes(16).joinToString("") { "%02x".format(it) }

    val CLIENT_INFO: String = buildJsonObject {
        put("package_name", "com.community.mbox.in")
        put("version_name", "3.0.03.0529.03")
        put("version_code", 50020042)
        put("os", "android")
        put("os_version", "16")
        put("device_id", DEVICE_ID)
        put("install_store", "ps")
        put("gaid", "d7578036d13336cc")
        put("brand", "google")
        put("model", "sdk_gphone64_x86_64")
        put("system_language", "en")
        put("net", "NETWORK_WIFI")
        put("region", "IN")
        put("timezone", "Asia/Calcutta")
        put("sp_code", "")
    }.toString()
}
