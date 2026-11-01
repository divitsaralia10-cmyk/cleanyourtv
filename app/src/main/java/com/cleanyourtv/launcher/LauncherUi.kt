package com.cleanyourtv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
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

@Composable
fun LauncherApp() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var weather by remember { mutableStateOf<WeatherState?>(null) }
    var now by remember { mutableStateOf(Date()) }
    var hour by remember { mutableFloatStateOf(currentHour()) }
    var ambient by remember { mutableStateOf(false) }
    var arcadeOpen by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<GameId?>(null) }
    var returnedFromArcade by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }

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

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            hour = currentHour()
            delay(10_000)
        }
    }

    // After a while with no key presses, show the full-screen ambient clock.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            val idleFor = System.currentTimeMillis() - lastInput
            if (!ambient && !arcadeOpen && game == null && idleFor > Config.IDLE_SECONDS * 1000L) {
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
    val homeAlpha by animateFloatAsState(if (ambient) 0f else 1f, tween(600), label = "homeAlpha")
    val ambientAlpha by animateFloatAsState(if (ambient) 1f else 0f, tween(800), label = "ambientAlpha")

    // A home screen should not exit on Back (games and the arcade handle Back themselves).
    BackHandler { if (ambient) ambient = false }

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
                    else -> {}
                }
            }
            arcadeOpen -> ArcadePicker(
                onPick = { game = it },
                onBack = {
                    arcadeOpen = false
                    returnedFromArcade = true
                },
            )
            else -> Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha }) {
                HomeScreen(
                    now = now,
                    hour = hour,
                    weather = weather,
                    apps = apps,
                    initialArcadeFocus = returnedFromArcade,
                    onOpenArcade = { arcadeOpen = true },
                    onLaunch = { app -> runCatching { context.startActivity(app.intent) } },
                )
            }
        }

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ambientAlpha }) {
            AmbientScreen(now = now, hour = hour, weather = weather)
        }
    }
}

private fun currentHour(): Float {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
}

private fun greeting(hour: Float): String = when {
    hour >= 5f && hour < 12f -> "Good morning"
    hour >= 12f && hour < 17f -> "Good afternoon"
    hour >= 17f && hour < 21f -> "Good evening"
    else -> "Good night"
}

private fun fmt(date: Date, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(date)

@Composable
private fun HomeScreen(
    now: Date,
    hour: Float,
    weather: WeatherState?,
    apps: List<AppEntry>,
    initialArcadeFocus: Boolean,
    onOpenArcade: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val arcadeFocus = remember { FocusRequester() }

    LaunchedEffect(apps.isNotEmpty()) {
        delay(120)
        val target = if (initialArcadeFocus) arcadeFocus else if (apps.isNotEmpty()) firstFocus else null
        if (target != null) runCatching { target.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ClockCard(now, hour, weather, Modifier.weight(1.45f).fillMaxHeight())
            ArcadeCard(Modifier.weight(1f).fillMaxHeight().focusRequester(arcadeFocus), onOpenArcade)
        }
        Dock(apps, firstFocus, onLaunch)
    }
}

@Composable
private fun Pill(text: String) {
    BasicText(
        text,
        modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
        style = TextStyle(color = Color.White, fontSize = 16.sp),
    )
}

@Composable
private fun ClockCard(now: Date, hour: Float, weather: WeatherState?, modifier: Modifier) {
    Column(
        modifier.glass(RoundedCornerShape(32.dp)).padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            BasicText(
                "${greeting(hour)}, ${Config.USER_NAME}",
                style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 20.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    fmt(now, "h:mm"),
                    style = TextStyle(color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    fmt(now, "a"),
                    modifier = Modifier.padding(bottom = 18.dp),
                    style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 28.sp, fontWeight = FontWeight.Medium),
                )
            }
            BasicText(
                fmt(now, "EEEE, d MMMM"),
                style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (weather != null) {
                Pill("${weather.tempC}° ${weather.label}")
                Pill(Config.CITY)
                Pill(if (hour < 12f) "Sunrise ${weather.sunrise}" else "Sunset ${weather.sunset}")
            } else {
                Pill(Config.CITY)
            }
        }
    }
}

@Composable
private fun ArcadeCard(modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "arcadeScale",
    )
    val snakeBest = remember { Scores.get(context, "snake") }
    val best2048 = remember { Scores.get(context, "2048") }

    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glass(RoundedCornerShape(32.dp), highlighted = focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            BasicText(
                "ARCADE",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Play",
                style = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                "Snake  ·  2048",
                style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 7) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF7CFFB2).copy(alpha = 0.25f + 0.1f * i))
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFFF6B8A)))
        }
        BasicText(
            "Best  Snake $snakeBest  ·  2048 $best2048",
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp),
        )
    }
}

@Composable
private fun Dock(apps: List<AppEntry>, firstFocus: FocusRequester, onLaunch: (AppEntry) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 170.dp).glass(RoundedCornerShape(36.dp))) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppTile(
                    app = app,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onLaunch(app) },
                )
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, modifier: Modifier, onClick: () -> Unit) {
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
                .size(width = 200.dp, height = 113.dp)
                .glass(shape, highlighted = focused)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = app.image,
                contentDescription = app.label,
                contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                modifier = if (app.isBanner) Modifier.fillMaxSize() else Modifier.size(72.dp),
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
            modifier = Modifier.width(200.dp),
            style = TextStyle(
                color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun AmbientScreen(now: Date, hour: Float, weather: WeatherState?) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                "${greeting(hour)}, ${Config.USER_NAME}",
                style = TextStyle(color = Color.White.copy(alpha = 0.95f), fontSize = 24.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                fmt(now, "EEEE, d MMMM"),
                style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Bottom) {
            BasicText(
                fmt(now, "h:mm"),
                style = TextStyle(color = Color.White, fontSize = 150.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                fmt(now, "a"),
                modifier = Modifier.padding(bottom = 30.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 44.sp, fontWeight = FontWeight.Medium),
            )
        }
        if (weather != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Pill("${weather.tempC}° ${weather.label}  ·  ${Config.CITY}")
                Pill(if (hour < 12f) "Sunrise ${weather.sunrise}" else "Sunset ${weather.sunset}")
            }
        }
        BasicText(
            "Press any button",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp),
        )
    }
}
