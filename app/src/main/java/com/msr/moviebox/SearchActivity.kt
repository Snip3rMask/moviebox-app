package com.msr.moviebox

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.msr.moviebox.data.MovieBoxApi
import com.msr.moviebox.ui.PosterAdapter
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var resultsList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        resultsList = findViewById(R.id.resultsList)
        resultsList.layoutManager = GridLayoutManager(this, 3)

        val input = findViewById<EditText>(R.id.searchInput)
        val initial = intent.getStringExtra("query")
        if (!initial.isNullOrBlank()) {
            input.setText(initial)
            doSearch(initial)
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.searchBtn)
            .setOnClickListener { doSearch(input.text.toString()) }
        input.setOnEditorActionListener { _, _, _ ->
            doSearch(input.text.toString())
            true
        }
    }

    private fun doSearch(query: String) {
        if (query.isBlank()) return
        lifecycleScope.launch {
            try {
                val items = MovieBoxApi.search(query)
                resultsList.adapter = PosterAdapter(items, {
                    startActivity(
                        Intent(this@SearchActivity, DetailActivity::class.java)
                            .putExtra("subjectId", it.subjectId)
                    )
                })
            } catch (e: Exception) {
                resultsList.adapter = null
            }
        }
    }
}
