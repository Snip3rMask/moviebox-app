package msr.pistream.shared.data

/**
 * Minimal standard Base64 encoder (RFC 4648 with padding).
 * Pure Kotlin so the shared module works on Android and desktop JVM.
 */
internal object Base64Encoder {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else -1
            sb.append(ALPHABET[b0 ushr 2])
            sb.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
            if (b1 >= 0) {
                sb.append(ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)])
            } else {
                sb.append('=')
            }
            if (b2 >= 0) sb.append(ALPHABET[b2 and 0x3F]) else sb.append('=')
            i += 3
        }
        return sb.toString()
    }
}
