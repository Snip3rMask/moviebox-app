package msr.pistream.app.ui

import msr.pistream.app.R
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import msr.pistream.app.data.MovieBoxRepository
import msr.pistream.app.data.model.Subject
import msr.pistream.app.ui.adapter.PosterAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val repo = MovieBoxRepository()

    private data class Row(val title: String, val categoryType: String)

    private val rows = listOf(
        Row("Trending", "4516404531735022304"),
        Row("Trending in Cinema", "5692654647815587592"),
        Row("Bollywood", "414907768299210008"),
        Row("South Indian", "3859721901924910512"),
        Row("Hollywood", "8019599703232971616"),
        Row("Top Series This Week", "4741626294545400336"),
        Row("Anime", "8434602210994128512"),
        Row("Korean Drama", "7878715743607948784"),
        Row("Turkish Drama", "5177200225164885656"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val searchInput = findViewById<EditText>(R.id.searchInput)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.searchBtn)
            .setOnClickListener {
                startSearch(searchInput.text.toString())
            }
        searchInput.setOnEditorActionListener { _, _, _ ->
            startSearch(searchInput.text.toString())
            true
        }

        val container = findViewById<LinearLayout>(R.id.rowsContainer)
        lifecycleScope.launch {
            try {
                repo.ensureLogin()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.message ?: "Login failed", Toast.LENGTH_LONG).show()
                return@launch
            }
            for (row in rows) {
                addRow(container, row.title, row.categoryType)
            }
        }
    }

    private fun startSearch(query: String) {
        if (query.isBlank()) return
        startActivity(
            Intent(this, SearchActivity::class.java).putExtra("query", query.trim())
        )
    }

    private fun addRow(container: LinearLayout, title: String, categoryType: String) {
        val row = layoutInflater.inflate(R.layout.item_row, container, false)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        val list = row.findViewById<RecyclerView>(R.id.rowList)
        list.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        container.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lifecycleScope.launch {
            try {
                val items = repo.homeRow(categoryType)
                if (items.isEmpty()) {
                    container.removeView(row)
                    return@launch
                }
                list.adapter = PosterAdapter(items, { openDetail(it) }, itemWidthDp = 130)
            } catch (e: Exception) {
                container.removeView(row)
            }
        }
    }

    private fun openDetail(subject: Subject) {
        startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra("subjectId", subject.subjectId)
        )
    }
}
