package com.cleanyourtv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Pill-shaped focusable chip, shared by the Movies and Scores screens. */
@Composable
internal fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    BasicText(
        label,
        modifier = modifier
            .glass(RoundedCornerShape(50), highlighted = focused || selected)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        style = TextStyle(
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        ),
    )
}

/** Full-screen explanation shown when a free API key has not been set yet. */
@Composable
internal fun SetupMessage(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(16.dp))
        BasicText(
            body,
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp),
        )
        Spacer(Modifier.height(20.dp))
        BasicText(
            "Press Back to return",
            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp),
        )
    }
}

@Composable
fun MoviesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val omdbKey = remember { Keys.omdb(context) }
    val watchKey = remember { Keys.watchmode(context) }
    BackHandler { onBack() }

    if (omdbKey.isBlank()) {
        SetupMessage(
            "Movies need a free OMDb key",
            "On your PC run:\n.\\scripts\\set-keys.ps1 -Omdb YOUR_KEY\n" +
                "For where-to-watch info also add:  -Watchmode YOUR_KEY",
        )
    } else {
        MoviesContent(omdbKey, watchKey)
    }
}

@Composable
private fun MoviesContent(omdbKey: String, watchKey: String) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(MOVIE_CATEGORIES.first()) }
    var movies by remember { mutableStateOf<List<Movie>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }
    var avail by remember { mutableStateOf<Availability?>(null) }
    var availFailed by remember { mutableStateOf(false) }
    val firstChip = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstChip.requestFocus() }
    }

    // Wait briefly so scrolling past categories does not fire a request for each one.
    LaunchedEffect(selected) {
        delay(350)
        movies = null
        failed = false
        focusedMovie = null
        val list = Movies.list(context, omdbKey, selected)
        if (list == null) {
            failed = true
        } else {
            movies = list
            focusedMovie = list.firstOrNull()
        }
    }

    LaunchedEffect(focusedMovie?.imdbId) {
        avail = null
        availFailed = false
        val m = focusedMovie
        if (m != null && watchKey.isNotBlank()) {
            delay(300)
            val a = Movies.availability(watchKey, m.imdbId)
            if (a == null) availFailed = true else avail = a
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            "Movies",
            style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(MOVIE_CATEGORIES, key = { _, c -> c.name }) { index, cat ->
                Chip(
                    label = cat.name,
                    selected = cat == selected,
                    modifier = if (index == 0) Modifier.focusRequester(firstChip) else Modifier,
                    onFocus = { selected = cat },
                    onClick = { selected = cat },
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(206.dp)) {
            val list = movies
            when {
                failed -> BasicText(
                    "Couldn't load movies. Check Wi-Fi or the OMDb key.",
                    style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp),
                )
                list == null -> BasicText(
                    "Loading…",
                    style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp),
                )
                else -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(list, key = { it.imdbId }) { m ->
                        PosterCard(m, onFocus = { focusedMovie = m })
                    }
                }
            }
        }

        DetailPanel(
            movie = focusedMovie,
            avail = avail,
            availFailed = availFailed,
            hasWatchKey = watchKey.isNotBlank(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PosterCard(movie: Movie, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "posterScale",
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(width = 120.dp, height = 180.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { }
    ) {
        if (movie.poster != null) {
            AsyncImage(
                model = movie.poster,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BasicText(
                movie.title,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
                style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (movie.imdb != null) {
            BasicText(
                "★ ${movie.imdb}",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                style = TextStyle(color = Color(0xFFFFD66B), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (focused) {
            Box(Modifier.fillMaxSize().border(3.dp, Color.White, shape))
        }
    }
}

@Composable
private fun DetailPanel(
    movie: Movie?,
    avail: Availability?,
    availFailed: Boolean,
    hasWatchKey: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (movie == null) {
            BasicText(
                "Pick a category, then a movie",
                style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp),
            )
            return@Column
        }

        val title = if (movie.year.isNotBlank()) "${movie.title} (${movie.year})" else movie.title
        BasicText(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )

        val ratings = buildString {
            append(if (movie.imdb != null) "★ ${movie.imdb} IMDb" else "No IMDb rating")
            if (movie.runtime.isNotBlank()) append("    ${movie.runtime}")
            if (movie.genres.isNotBlank()) append("    ${movie.genres.replace(", ", " · ")}")
        }
        BasicText(
            ratings,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color(0xFFFFD66B), fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )

        val infoStyle = TextStyle(color = Color.White, fontSize = 17.sp)
        val dimStyle = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp)
        when {
            !hasWatchKey -> BasicText("Add a Watchmode key to see where to watch", style = dimStyle)
            availFailed -> BasicText("Where-to-watch info unavailable right now", style = dimStyle)
            avail == null -> BasicText("Checking where to watch…", style = dimStyle)
            else -> {
                if (avail.stream.isEmpty() && avail.rent.isEmpty() && avail.buy.isEmpty()) {
                    BasicText("Not listed on any app in India", style = dimStyle)
                }
                if (avail.stream.isNotEmpty()) {
                    BasicText(
                        "Stream: ${avail.stream.joinToString(", ")}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = infoStyle,
                    )
                }
                if (avail.rent.isNotEmpty()) {
                    BasicText(
                        "Rent: ${avail.rent.joinToString(", ")}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = infoStyle,
                    )
                }
                if (avail.buy.isNotEmpty()) {
                    BasicText(
                        "Buy: ${avail.buy.joinToString(", ")}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = infoStyle,
                    )
                }
            }
        }
        BasicText(
            movie.plot,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp),
        )
    }
}
