package com.cleanyourtv.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf

/** Bumped every time the launcher is opened, so the UI can show the greeting screen again. */
object AppState {
    val resumeTick = mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp() }
    }

    override fun onStart() {
        super.onStart()
        AppState.resumeTick.intValue++
    }

    // Pressing Home while the launcher is already in front arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppState.resumeTick.intValue++
    }
}
