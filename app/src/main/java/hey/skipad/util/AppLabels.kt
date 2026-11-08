package hey.skipad.util

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a package name to the app's display label. The log page is rendered
 * from a LazyColumn, so the result is cached: PackageManager lookups on every
 * recomposition would stutter while scrolling.
 */
private val labelCache = ConcurrentHashMap<String, String>()

fun appLabel(context: Context, packageName: String): String {
    if (packageName.isBlank()) return "未知应用"
    labelCache[packageName]?.let { return it }
    val label = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
    labelCache[packageName] = label
    return label
}
