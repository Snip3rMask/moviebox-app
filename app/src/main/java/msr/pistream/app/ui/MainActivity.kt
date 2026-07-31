package msr.pistream.app.ui

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationBarView
import msr.pistream.app.R

/**
 * Main shell hosting navigation (Home / Downloads / Settings).
 * Portrait phones get a bottom bar; landscape phones and Android TV /
 * Fire TV get a left-side rail (via layout resource qualifiers).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var nav: NavigationBarView
    private var currentTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: the home carousel draws behind the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface)

        // layout-land / layout-television variants automatically give a
        // left rail on landscape phones and on Android TV / Fire TV.
        setContentView(R.layout.activity_main)
        nav = findViewById(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showTab(TAG_HOME)
                R.id.nav_downloads -> showTab(TAG_DOWNLOADS)
                R.id.nav_settings -> showTab(TAG_SETTINGS)
            }
            true
        }

        if (savedInstanceState == null) {
            showTab(TAG_HOME)
        } else {
            currentTag = savedInstanceState.getString(KEY_TAB) ?: TAG_HOME
            nav.selectedItemId = when (currentTag) {
                TAG_DOWNLOADS -> R.id.nav_downloads
                TAG_SETTINGS -> R.id.nav_settings
                else -> R.id.nav_home
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, currentTag)
    }

    private fun showTab(tag: String) {
        if (tag == currentTag) return
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        currentTag?.let { fm.findFragmentByTag(it)?.let { f -> ft.hide(f) } }
        val f = fm.findFragmentByTag(tag)
        if (f == null) {
            val newF = when (tag) {
                TAG_DOWNLOADS -> DownloadsFragment()
                TAG_SETTINGS -> SettingsFragment()
                else -> HomeFragment()
            }
            ft.add(R.id.fragmentContainer, newF, tag)
        } else {
            ft.show(f)
        }
        currentTag = tag
        ft.commit()
    }

    companion object {
        private const val TAG_HOME = "home"
        private const val TAG_DOWNLOADS = "downloads"
        private const val TAG_SETTINGS = "settings"
        private const val KEY_TAB = "tab"
    }
}
