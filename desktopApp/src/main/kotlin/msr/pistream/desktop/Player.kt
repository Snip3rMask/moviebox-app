package msr.pistream.desktop

import java.io.File

/**
 * Launches the bundled mpv.exe (Windows) as a separate playback window.
 * The MPD stream is played directly by mpv; the CloudFront signed cookie
 * is passed as an HTTP header.
 */
object Player {

    fun play(url: String, cookie: String?, title: String?) {
        val mpv = findMpv()
            ?: throw IllegalStateException(
                "mpv player not found. Put mpv.exe (with its DLLs) into " +
                    "desktopApp/src/main/resources/mpv/ and rebuild."
            )
        val cmd = mutableListOf(mpv)
        if (!cookie.isNullOrBlank()) {
            cmd += "--http-header-fields=Cookie: $cookie"
        }
        cmd += "--force-window=yes"
        if (!title.isNullOrBlank()) cmd += "--title=$title"
        cmd += url
        ProcessBuilder(cmd).start()
    }

    private fun findMpv(): String? {
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        val candidates = mutableListOf<String>()
        if (!resourcesDir.isNullOrBlank()) {
            candidates += File(resourcesDir, "mpv/mpv.exe").path
        }
        candidates += File("src/main/resources/mpv/mpv.exe").path
        candidates += "mpv.exe"
        return candidates.firstOrNull { File(it).exists() }
    }
}
