package com.cleanyourtv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Packages that are never shown on the home screen. Add package names here to hide apps. */
val HIDDEN_PACKAGES: Set<String> = emptySet()

data class AppEntry(
    val label: String,
    val packageName: String,
    val intent: Intent,
    val image: ImageBitmap,
    val isBanner: Boolean,
)

/** Loads every launchable app (TV apps first-class, phone-style apps as a fallback). */
fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    val resolved: List<ResolveInfo> =
        pm.queryIntentActivities(tvIntent, 0) + pm.queryIntentActivities(phoneIntent, 0)

    return resolved
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .filter { it.activityInfo.packageName !in HIDDEN_PACKAGES }
        .mapNotNull { info ->
            runCatching {
                val ai = info.activityInfo
                val banner: Drawable? = ai.loadBanner(pm)
                val drawable: Drawable = banner ?: info.loadIcon(pm)
                val bitmap = if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(192, 192)
                AppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    intent = Intent(Intent.ACTION_MAIN)
                        .setClassName(ai.packageName, ai.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                    image = bitmap.asImageBitmap(),
                    isBanner = banner != null,
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}
