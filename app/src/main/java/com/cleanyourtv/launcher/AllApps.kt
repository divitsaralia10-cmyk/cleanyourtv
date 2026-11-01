package com.cleanyourtv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AllAppsScreen(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler { onBack() }

    LaunchedEffect(apps.isNotEmpty()) {
        delay(150)
        runCatching { first.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 26.dp)) {
        BasicText(
            "All apps",
            style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(196.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            itemsIndexed(apps, key = { _, a -> a.packageName }) { index, app ->
                AppTile(
                    app = app,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onLaunch(app) },
                )
            }
        }
    }
}
