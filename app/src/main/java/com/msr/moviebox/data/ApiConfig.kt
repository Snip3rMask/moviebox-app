package com.msr.moviebox.data

import android.util.Base64
import org.json.JSONObject
import kotlin.random.Random

/** API constants, device fingerprint and the HMAC signing key. */
object ApiConfig {

    const val BASE_URL = "https://apig.inmoviebox.com"

    /** Ping endpoint that returns the anonymous JWT in the `x-user` header. */
    const val ANONYMOUS_LOGIN_PATH =
        "/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1"

    /** HMAC key — double base64-decoded plugin constant. */
    val SIGNING_KEY: ByteArray = Base64.decode(
        Base64.decode(
            "NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==",
            Base64.DEFAULT
        ),
        Base64.DEFAULT
    )

    const val USER_AGENT =
        "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; " +
            "sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)"

    val DEVICE_ID: String = Random.nextBytes(16).joinToString("") { "%02x".format(it) }

    val CLIENT_INFO: String = JSONObject().apply {
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
