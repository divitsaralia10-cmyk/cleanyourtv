package com.cleanyourtv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import java.util.Date
import java.util.Locale

@Composable
fun LauncherApp() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }

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

    // A home screen should not exit on Back.
    BackHandler { }

    LauncherScreen(
        apps = apps,
        onLaunch = { app -> runCatching { context.startActivity(app.intent) } },
    )
}

@Composable
fun LauncherScreen(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    var focusedPkg by remember { mutableStateOf<String?>(null) }
    val focused = apps.firstOrNull { it.packageName == focusedPkg } ?: apps.firstOrNull()

    // The background glow shifts colour with whichever app is focused.
    val accentTarget = focused?.let { accentFor(it.packageName) } ?: Color(0xFF2A3A6A)
    val accent by animateColorAsState(accentTarget, tween(800), label = "accent")

    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftValue",
    )

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            delay(100)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(size.width * (0.2f + 0.6f * drift), size.height * 0.15f),
                        radius = size.width * 0.75f,
                    )
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            }
    ) {
        Column(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 48.dp)) {
            TopBar()
            Spacer(Modifier.weight(1f))
            Hero(focused)
            Spacer(Modifier.height(24.dp))
            AppRow(
                apps = apps,
                firstFocus = firstFocus,
                onFocus = { focusedPkg = it.packageName },
                onLaunch = onLaunch,
            )
        }
    }
}

private fun accentFor(packageName: String): Color {
    val hue = Math.floorMod(packageName.hashCode(), 360).toFloat()
    return Color.hsv(hue, 0.60f, 0.55f)
}

@Composable
private fun TopBar() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            "CleanTV",
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            BasicText(time, style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light))
            BasicText(date, style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp))
        }
    }
}

@Composable
private fun Hero(app: AppEntry?) {
    Column(Modifier.padding(horizontal = 56.dp).heightIn(min = 120.dp)) {
        BasicText(
            app?.label ?: "Loading apps",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            "Press OK to open",
            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp),
        )
    }
}

@Composable
private fun AppRow(
    apps: List<AppEntry>,
    firstFocus: FocusRequester,
    onFocus: (AppEntry) -> Unit,
    onLaunch: (AppEntry) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppCard(
                app = app,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onFocus = { onFocus(app) },
                onClick = { onLaunch(app) },
            )
        }
    }
}

@Composable
private fun AppCard(app: AppEntry, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "cardScale",
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
                .size(width = 240.dp, height = 135.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = if (focused) 0.22f else 0.10f))
                .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = app.image,
                contentDescription = app.label,
                contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                modifier = if (app.isBanner) Modifier.fillMaxSize() else Modifier.size(84.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(240.dp),
            style = TextStyle(
                color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
