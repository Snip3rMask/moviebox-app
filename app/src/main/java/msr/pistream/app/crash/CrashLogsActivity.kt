package msr.pistream.app.crash

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import msr.pistream.app.R
import org.json.JSONObject
import java.io.File

/** Lists saved crash reports with view / share / download / delete actions. */
class CrashLogsActivity : AppCompatActivity() {

    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView
    private var adapter: CrashAdapter? = null
    private var pendingDownload: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_logs)

        listView = findViewById(R.id.crashList)
        emptyView = findViewById(R.id.emptyView)
        listView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.crashBackBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.clearAllBtn).setOnClickListener { confirmClearAll() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val files = CrashReporter.list(this)
        emptyView.isVisible = files.isEmpty()
        listView.isVisible = files.isNotEmpty()
        adapter = CrashAdapter(files) { file ->
            showDetail(file)
        }
        listView.adapter = adapter
        listView.adapter?.notifyDataSetChanged()
    }

    private fun showDetail(file: File) {
        val report = CrashReporter.read(file)
        val dialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_crash_detail)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.color.surface)
        dialog.findViewById<TextView>(R.id.crashDetailText)?.text = report
        dialog.findViewById<View>(R.id.detailShareBtn)?.setOnClickListener {
            dialog.dismiss()
            shareLog(file)
        }
        dialog.findViewById<View>(R.id.detailSaveBtn)?.setOnClickListener {
            dialog.dismiss()
            downloadLog(file)
        }
        dialog.findViewById<View>(R.id.detailDeleteBtn)?.setOnClickListener {
            dialog.dismiss()
            deleteLog(file)
        }
    }

    private fun shareLog(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, getString(R.string.crash_share_text, file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_log)))
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.failed_to_load), Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadLog(file: File) {
        pendingDownload = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, file.name.removeSuffix(".json") + ".txt")
        }
        startActivityForResult(intent, REQ_SAVE_LOG)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SAVE_LOG && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            val file = pendingDownload ?: return
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(CrashReporter.read(file).toByteArray())
                    }
                    Toast.makeText(this, R.string.crash_downloaded, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message ?: getString(R.string.failed_to_load), Toast.LENGTH_LONG).show()
                }
            }
            pendingDownload = null
        }
    }

    private fun deleteLog(file: File) {
        if (CrashReporter.delete(file)) {
            Toast.makeText(this, R.string.crash_deleted, Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    private fun confirmClearAll() {
        if (CrashReporter.list(this).isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all)
            .setMessage(R.string.clear_all_confirm)
            .setPositiveButton(R.string.delete_log) { _, _ ->
                CrashReporter.deleteAll(this)
                Toast.makeText(this, R.string.crash_deleted, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private class CrashAdapter(
        private val files: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<CrashAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.crashTitle)
            val subtitle: TextView = v.findViewById(R.id.crashSubtitle)
            val share: ImageButton = v.findViewById(R.id.actionShare)
            val download: ImageButton = v.findViewById(R.id.actionDownload)
            val delete: ImageButton = v.findViewById(R.id.actionDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_crash_log, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = files[position]
            val data = runCatching { JSONObject(CrashReporter.read(file)) }.getOrNull()
            val exception = data?.optString("exception_type") ?: "Unknown"
            val message = data?.optString("exception_message") ?: ""
            val time = data?.optString("timestamp_iso") ?: ""
            val version = data?.optJSONObject("app")?.optString("version_name") ?: ""
            val device = data?.optJSONObject("device")?.optString("model") ?: ""
            val activity = data?.optString("activity") ?: ""

            holder.title.text = exception + if (message.isNotBlank()) ": $message" else ""
            holder.subtitle.text = listOf(time, "v$version", device, activity)
                .filter { it.isNotBlank() }
                .joinToString("  •  ")

            holder.itemView.setOnClickListener { onClick(file) }
            holder.share.setOnClickListener { (holder.itemView.context as? CrashLogsActivity)?.shareLog(file) }
            holder.download.setOnClickListener { (holder.itemView.context as? CrashLogsActivity)?.downloadLog(file) }
            holder.delete.setOnClickListener { (holder.itemView.context as? CrashLogsActivity)?.deleteLog(file) }
        }
    }

    companion object {
        private const val REQ_SAVE_LOG = 1001
    }
}
