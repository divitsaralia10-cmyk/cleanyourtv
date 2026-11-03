package com.cleanyourtv.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date

/**
 * The full-screen welcome shown every time the launcher opens (and after a stretch of inactivity):
 * greeting, big clock, date, temperature and weather. Any key press slides it away to the home screen.
 */
@Composable
fun WelcomeScreen(now: Date, hour: Float, weather: WeatherState?) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                "${greeting(hour)}, ${Config.USER_NAME}",
                style = TextStyle(color = Color.White.copy(alpha = 0.95f), fontSize = 28.sp, fontWeight = FontWeight.Medium),
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
                style = TextStyle(color = Color.White, fontSize = 140.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                fmt(now, "a"),
                modifier = Modifier.padding(bottom = 26.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 40.sp, fontWeight = FontWeight.Medium),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (weather != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    BasicText(
                        "${weather.tempC}°",
                        style = TextStyle(color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold),
                    )
                    Column {
                        BasicText(
                            weather.label,
                            style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                        )
                        BasicText(
                            Config.CITY,
                            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp),
                        )
                    }
                    Pill(if (hour < 12f) "Sunrise ${weather.sunrise}" else "Sunset ${weather.sunset}")
                }
            } else {
                Pill(Config.CITY)
            }
            BasicText(
                "Press OK to continue",
                style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 18.sp),
            )
        }
    }
}
