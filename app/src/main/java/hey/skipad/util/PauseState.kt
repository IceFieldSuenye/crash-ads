package hey.skipad.util

import android.content.Context

/**
 * Manual pause switch driven by the ongoing notification action.
 * Toggle to pause, toggle again to resume — no auto-expiry.
 */
object PauseState {

    private const val PREF = "pause_state"
    private const val KEY_PAUSED = "paused"

    fun isPaused(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_PAUSED, false)

    /** Toggles pause and returns the new paused state. */
    fun toggle(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val next = !prefs.getBoolean(KEY_PAUSED, false)
        prefs.edit().putBoolean(KEY_PAUSED, next).apply()
        return next
    }

    fun set(context: Context, paused: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PAUSED, paused).apply()
    }
}
