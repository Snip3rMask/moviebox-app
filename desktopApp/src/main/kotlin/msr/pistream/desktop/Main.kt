package msr.pistream.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import javax.imageio.ImageIO
import msr.pistream.shared.data.MovieBoxRepository
import msr.pistream.shared.data.model.Episode
import msr.pistream.shared.data.model.Subject

private val Dark = Color(0xFF0F141E)
private val Surface = Color(0xFF1A2230)
private val Accent = Color(0xFF7C4DFF)
private val SecondaryText = Color(0xFF9AA5B5)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pi-Stream",
        state = rememberWindowState(width = 1150.dp, height = 720.dp)
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                background = Dark,
                surface = Surface
            )
        ) {
            App()
        }
    }
}

private sealed class Screen {
    object Home : Screen()
    data class Detail(val subjectId: String) : Screen()
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var playerError by remember { mutableStateOf<String?>(null) }

    when (val s = screen) {
        Screen.Home -> HomeScreen(onOpen = { screen = Screen.Detail(it.subjectId) })
        is Screen.Detail -> DetailScreen(
            subjectId = s.subjectId,
            onBack = { screen = Screen.Home },
            onPlay = { url, cookie, title ->
                try {
                    Player.play(url, cookie, title)
                } catch (e: Exception) {
                    playerError = e.message ?: "Failed to start mpv"
                }
            }
        )
    }

    playerError?.let { msg ->
        AlertDialog(
            onDismissRequest = { playerError = null },
            title = { Text("Playback error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { playerError = null }) { Text("OK") }
            }
        )
    }
}

private val categories = listOf(
    "Trending" to "4516404531735022304",
    "Bollywood" to "414907768299210008",
    "South Indian" to "3859721901924910512",
    "Hollywood" to "8019599703232971616",
    "Top Series This Week" to "4741626294545400336",
    "Anime" to "8434602210994128512",
    "Korean Drama" to "7878715743607948784",
    "Turkish Drama" to "5177200225164885656"
)

/** Lightweight poster loader (ImageIO -> ImageBitmap) with a tiny in-memory cache. */
private val imageCache = mutableMapOf<String, ImageBitmap>()

@Composable
private fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = null
        if (url.isNullOrBlank()) return@LaunchedEffect
        imageCache[url]?.let { bitmap = it; return@LaunchedEffect }
        bitmap = withContext(Dispatchers.IO) {
            runCatching { ImageIO.read(URI(url).toURL()).toComposeImageBitmap() }
                .getOrNull()
                ?.also { imageCache[url] = it }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(Surface))
    }
}

@Composable
private fun HomeScreen(onOpen: (Subject) -> Unit) {
    val repo = remember { MovieBoxRepository() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Subject>>(emptyList()) }
    var rows by remember { mutableStateOf<List<Pair<String, List<Subject>>>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            repo.ensureLogin()
            rows = categories.map { (title, id) -> title to repo.homeRow(id) }
        } catch (_: Exception) {
            rows = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pi-Stream", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search movies & series…") },
                singleLine = true
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    if (query.isBlank()) return@Button
                    scope.launch {
                        searching = true
                        try {
                            results = repo.search(query)
                        } finally {
                            searching = false
                        }
                    }
                }
            ) { Text(if (searching) "Searching…" else "Search") }
        }

        if (results.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SectionTitle("Results") }
                items(results, key = { it.subjectId }) { PosterCard(it, onOpen) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                for ((title, items) in rows) {
                    item { SectionTitle(title) }
                    item {
                        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            items(items, key = { it.subjectId }) { PosterCard(it, onOpen) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun PosterCard(subject: Subject, onClick: (Subject) -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .padding(6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable { onClick(subject) }
    ) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(Surface)) {
            RemoteImage(subject.coverUrl, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Text(
            subject.title,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 2,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun DetailScreen(
    subjectId: String,
    onBack: () -> Unit,
    onPlay: (url: String, cookie: String?, title: String?) -> Unit
) {
    val repo = remember { MovieBoxRepository() }
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf<Subject?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(subjectId) {
        try {
            val d = repo.detail(subjectId)
            subject = d
            if (d != null && d.subjectType == 2) {
                episodes = repo.buildEpisodes(subjectId, repo.seasons(subjectId))
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TextButton(onClick = onBack) { Text("← Back", color = Color.White) }

        val d = subject
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Loading…", color = Color.White)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                RemoteImage(
                    d.coverUrl,
                    Modifier.width(180.dp).height(260.dp).clip(RoundedCornerShape(12.dp)),
                    ContentScale.Crop
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(meta(d), color = SecondaryText)
                    if (d.imdbRating.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("IMDb ${d.imdbRating}", color = Accent)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(d.description, color = SecondaryText, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val stream = if (d.subjectType == 2) {
                                        val first = episodes.firstOrNull()
                                        if (first == null) null
                                        else repo.playStream(first.subjectId, first.se, first.ep, d.dubs)
                                    } else {
                                        repo.playStream(d.subjectId, 0, 0, d.dubs)
                                    }
                                    if (stream != null) onPlay(stream.url, stream.signCookie, d.title)
                                    else error = "No streams available for this title"
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text(if (busy) "Loading…" else "Play") }
                }
            }

            if (episodes.isNotEmpty()) {
                SectionTitle("Episodes")
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(episodes, key = { "${it.se}-${it.ep}" }) { ep ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .clickable {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val stream = repo.playStream(ep.subjectId, ep.se, ep.ep, d.dubs)
                                            if (stream != null) onPlay(stream.url, stream.signCookie, d.title)
                                            else error = "No streams available for this title"
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Text(ep.label, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun meta(d: Subject): String = listOf(
    d.releaseDate.take(4).takeIf { it.isNotBlank() },
    d.duration.takeIf { it.isNotBlank() },
    d.genre.takeIf { it.isNotBlank() },
    d.countryName.takeIf { it.isNotBlank() }
).filterNotNull().joinToString("  •  ")
