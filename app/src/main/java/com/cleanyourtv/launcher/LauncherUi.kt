package com.cleanyourtv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Screen { Home, Arcade, Movies, Scores, AllApps }

@Composable
fun LauncherApp() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var weather by remember { mutableStateOf<WeatherState?>(null) }
    var homeScores by remember { mutableStateOf<List<MatchScore>>(emptyList()) }
    var now by remember { mutableStateOf(Date()) }
    var hour by remember { mutableFloatStateOf(currentHour()) }
    var ambient by remember { mutableStateOf(true) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var game by remember { mutableStateOf<GameId?>(null) }
    var homeFocus by remember { mutableStateOf("apps") }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Every time the launcher opens or Home is pressed: back to the welcome screen, with home reset.
    LaunchedEffect(AppState.resumeTick.intValue) {
        ambient = true
        screen = Screen.Home
        game = null
        homeFocus = "apps"
        lastInput = System.currentTimeMillis()
    }

    // Refresh the app list whenever something is installed, removed or changed.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                reloadTick++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(reloadTick) {
        apps = withContext(Dispatchers.Default) { loadApps(context) }
    }

    // Weather: refresh every 30 minutes, retry after a minute if it fails.
    LaunchedEffect(Unit) {
        while (true) {
            val w = fetchWeather()
            if (w != null) {
                weather = w
                delay(30 * 60 * 1000L)
            } else {
                delay(60_000L)
            }
        }
    }

    // Scores preview on the home screen. Cricket has a small free quota, so it refreshes rarely.
    LaunchedEffect(Unit) {
        while (true) {
            val c = LiveScores.cricket(context, 30 * 60_000L).matches
            val f = LiveScores.football(3 * 60_000L).matches
            homeScores = LiveScores.sorted(c + f)
            delay(3 * 60_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            hour = currentHour()
            delay(10_000)
        }
    }

    // After a while with no key presses on the home screen, show the full-screen ambient clock.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            val idleFor = System.currentTimeMillis() - lastInput
            if (!ambient && screen == Screen.Home && game == null && idleFor > Config.IDLE_SECONDS * 1000L) {
                ambient = true
            }
        }
    }

    val driftState = rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftValue",
    )
    // 0 = welcome screen covers everything, 1 = welcome screen has slid away and home is visible.
    val reveal by animateFloatAsState(
        if (ambient) 0f else 1f,
        tween(650, easing = FastOutSlowInEasing),
        label = "reveal",
    )

    // A home screen should not exit on Back (other screens handle Back themselves).
    BackHandler { if (ambient) ambient = false }

    val launch: (AppEntry) -> Unit = { app -> runCatching { context.startActivity(app.intent) } }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                lastInput = System.currentTimeMillis()
                if (ambient) {
                    ambient = false
                    true
                } else {
                    false
                }
            }
    ) {
        SkyScene(hour = hour, drift = { driftState.value }, modifier = Modifier.fillMaxSize())
        // Soft vignette so text stays readable on bright skies.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.35f))
                    )
                )
        )

        when {
            game != null -> {
                when (game) {
                    GameId.Snake -> SnakeGame(onExit = { game = null })
                    GameId.Game2048 -> Game2048(onExit = { game = null })
                    GameId.Cricket -> CricketGame(onExit = { game = null })
                    GameId.Breakout -> BreakoutGame(onExit = { game = null })
                    GameId.Flappy -> FlappyGame(onExit = { game = null })
                    GameId.Pong -> PongGame(onExit = { game = null })
                    GameId.Tetris -> TetrisGame(onExit = { game = null })
                    GameId.Memory -> MemoryGame(onExit = { game = null })
                    else -> {}
                }
            }
            screen == Screen.Arcade -> ArcadePicker(
                onPick = { game = it },
                onBack = {
                    screen = Screen.Home
                    homeFocus = "arcade"
                },
            )
            screen == Screen.Movies -> MoviesScreen(
                onBack = {
                    screen = Screen.Home
                    homeFocus = "movies"
                },
            )
            screen == Screen.Scores -> ScoresScreen(
                onBack = {
                    screen = Screen.Home
                    homeFocus = "scores"
                },
            )
            screen == Screen.AllApps -> AllAppsScreen(
                apps = apps,
                onLaunch = launch,
                onBack = {
                    screen = Screen.Home
                    homeFocus = "apps"
                },
            )
            else -> Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = reveal
                    translationY = (1f - reveal) * 48f
                }
            ) {
                HomeScreen(
                    now = now,
                    hour = hour,
                    weather = weather,
                    apps = apps,
                    scores = homeScores,
                    focusOn = homeFocus,
                    onOpen = { target -> screen = target },
                    onSettings = {
                        homeFocus = "settings"
                        openSettings(context)
                    },
                    onLaunch = launch,
                )
            }
        }

        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -size.height * reveal }) {
            WelcomeScreen(now = now, hour = hour, weather = weather)
        }
    }
}

/** Opens the TV's system settings, trying the generic action first and TV settings apps as fallbacks. */
private fun openSettings(context: Context) {
    val pm = context.packageManager
    val candidates = listOf(
        Intent(Settings.ACTION_SETTINGS),
        pm.getLaunchIntentForPackage("com.android.tv.settings"),
        pm.getLaunchIntentForPackage("com.sony.dtv.settings"),
    )
    for (intent in candidates) {
        if (intent == null) continue
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun currentHour(): Float {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
}

internal fun greeting(hour: Float): String = when {
    hour >= 5f && hour < 12f -> "Good morning"
    hour >= 12f && hour < 17f -> "Good afternoon"
    hour >= 17f && hour < 21f -> "Good evening"
    else -> "Good night"
}

internal fun fmt(date: Date, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(date)

@Composable
private fun HomeScreen(
    now: Date,
    hour: Float,
    weather: WeatherState?,
    apps: List<AppEntry>,
    scores: List<MatchScore>,
    focusOn: String,
    onOpen: (Screen) -> Unit,
    onSettings: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
) {
    val appsFocus = remember { FocusRequester() }
    val scoresFocus = remember { FocusRequester() }
    val moviesFocus = remember { FocusRequester() }
    val arcadeFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }

    LaunchedEffect(focusOn, apps.isNotEmpty()) {
        delay(150)
        val target = when (focusOn) {
            "scores" -> scoresFocus
            "movies" -> moviesFocus
            "arcade" -> arcadeFocus
            "settings" -> settingsFocus
            else -> if (apps.isNotEmpty()) appsFocus else null
        }
        if (target != null) runCatching { target.requestFocus() }
    }

    val favorites = remember(apps) {
        Config.FAVORITE_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    }
    val dockApps = if (favorites.isEmpty()) apps.take(8) else favorites

    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ClockCard(now, hour, weather, Modifier.weight(1.2f).fillMaxHeight())
            ScoresCard(
                matches = scores,
                modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(scoresFocus),
                onClick = { onOpen(Screen.Scores) },
            )
        }
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShortcutTile("Movies", "Browse by category", Modifier.weight(1f).fillMaxHeight().focusRequester(moviesFocus)) {
                onOpen(Screen.Movies)
            }
            ShortcutTile("Arcade", "8 games  ·  Cricket 3D", Modifier.weight(1f).fillMaxHeight().focusRequester(arcadeFocus)) {
                onOpen(Screen.Arcade)
            }
            ShortcutTile("Settings", "Google TV settings", Modifier.weight(1f).fillMaxHeight().focusRequester(settingsFocus)) {
                onSettings()
            }
        }
        Dock(dockApps, appsFocus, onLaunch, onAllApps = { onOpen(Screen.AllApps) })
    }
}

@Composable
internal fun Pill(text: String) {
    BasicText(
        text,
        modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
        style = TextStyle(color = Color.White, fontSize = 16.sp),
    )
}

@Composable
private fun ClockCard(now: Date, hour: Float, weather: WeatherState?, modifier: Modifier) {
    val line = buildString {
        append(fmt(now, "EEEE, d MMMM"))
        if (weather != null) append("  ·  ${weather.tempC}° ${weather.label}")
        append("  ·  ${Config.CITY}")
    }
    Column(
        modifier.glass(RoundedCornerShape(32.dp)).padding(horizontal = 26.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            "${greeting(hour)}, ${Config.USER_NAME}",
            style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 20.sp, fontWeight = FontWeight.Medium),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                fmt(now, "h:mm"),
                style = TextStyle(color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                fmt(now, "a"),
                modifier = Modifier.padding(bottom = 14.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 26.sp, fontWeight = FontWeight.Medium),
            )
        }
        BasicText(
            line,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp),
        )
    }
}

@Composable
private fun ScoresCard(matches: List<MatchScore>, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "scoresScale",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glass(RoundedCornerShape(32.dp), highlighted = focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "LIVE SCORES",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "OK for all",
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp),
            )
        }
        if (matches.isEmpty()) {
            BasicText(
                "No matches right now",
                style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp),
            )
        }
        matches.take(3).forEach { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (m.live) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF4D5E)))
                }
                BasicText(
                    m.summary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                )
                if (!m.live && m.shortWhen().isNotBlank()) {
                    BasicText(
                        m.shortWhen(),
                        maxLines = 1,
                        style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "shortcutScale",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glass(RoundedCornerShape(26.dp), highlighted = focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            subtitle,
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp),
        )
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    firstFocus: FocusRequester,
    onLaunch: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(170.dp).glass(RoundedCornerShape(36.dp))) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppTile(
                    app = app,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onLaunch(app) },
                )
            }
            item { AllAppsTile(onClick = onAllApps) }
        }
    }
}

@Composable
internal fun AppTile(app: AppEntry, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "tileScale",
    )
    val shape = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(
            modifier = modifier
                .size(width = 176.dp, height = 99.dp)
                .glass(shape, highlighted = focused)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = app.image,
                contentDescription = app.label,
                contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                modifier = if (app.isBanner) Modifier.fillMaxSize() else Modifier.size(64.dp),
            )
            if (focused) {
                Box(Modifier.fillMaxSize().border(3.dp, Color.White, shape))
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(176.dp),
            style = TextStyle(
                color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun AllAppsTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "allAppsScale",
    )
    val shape = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(
            modifier = Modifier
                .size(width = 176.dp, height = 99.dp)
                .glass(shape, highlighted = focused)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.85f))
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            "All apps",
            modifier = Modifier.width(176.dp),
            style = TextStyle(
                color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
