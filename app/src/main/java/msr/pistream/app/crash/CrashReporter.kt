package msr.pistream.app.crash

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves every uncaught crash as a JSON report in internal storage
 * (crash_logs/) with full device / app / screen / tap context.
 */
object CrashReporter {

    private const val MAX_REPORTS = 50

    /** Foreground activity at crash time (updated by the Application). */
    @Volatile
    var currentActivity: String? = null

    /** Last touched view description (updated by the Application). */
    @Volatile
    var lastTouch: String? = null

    private fun reportsDir(context: Context): File = File(context.filesDir, "crash_logs")

    fun save(context: Context, thread: Thread, throwable: Throwable): File? {
        return try {
            val dir = reportsDir(context).apply { mkdirs() }
            val file = File(dir, "crash_${System.currentTimeMillis()}.json")
            file.writeText(buildReport(context, thread, throwable))
            dir.listFiles()
                ?.sortedByDescending { it.name }
                ?.drop(MAX_REPORTS)
                ?.forEach { it.delete() }
            file
        } catch (_: Exception) {
            null
        }
    }

    fun list(context: Context): List<File> =
        reportsDir(context).listFiles()?.sortedByDescending { it.name } ?: emptyList()

    fun read(file: File): String = file.readText()

    fun delete(file: File): Boolean = file.delete()

    fun deleteAll(context: Context): Boolean {
        val files = list(context)
        if (files.isEmpty()) return true
        return files.all { it.delete() }
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val stack = Log.getStackTraceString(throwable)
        val topFrame = throwable.stackTrace.firstOrNull()
        val location = topFrame?.let {
            "${it.className}.${it.methodName}(${it.fileName ?: "?"}:${it.lineNumber})"
        } ?: "unknown"

        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put(
                "timestamp_iso",
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            put("exception_type", throwable.javaClass.name)
            put("exception_message", throwable.message ?: "")
            put("thread", thread.name)
            put("location", location)
            put("activity", currentActivity ?: "")
            put("last_user_action", lastTouch ?: "")
            put("stack_trace", stack)
            put("app", JSONObject().apply {
                put("package", context.packageName)
                put("version_name", packageInfo?.versionName ?: "")
                put("version_code", packageInfo?.versionCode ?: 0)
            })
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("product", Build.PRODUCT)
                put("board", Build.BOARD)
            })
            put("android", JSONObject().apply {
                put("release", Build.VERSION.RELEASE)
                put("sdk_int", Build.VERSION.SDK_INT)
                put("codename", Build.VERSION.CODENAME)
                put("security_patch", Build.VERSION.SECURITY_PATCH)
            })
            put("memory", JSONObject().apply {
                val rt = Runtime.getRuntime()
                put("max_heap_bytes", rt.maxMemory())
                put("free_heap_bytes", rt.freeMemory())
                put("total_heap_bytes", rt.totalMemory())
            })
        }
        return json.toString(2)
    }
}
