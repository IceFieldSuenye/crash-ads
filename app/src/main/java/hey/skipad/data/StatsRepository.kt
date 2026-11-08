package hey.skipad.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SkipStats(
    val today: Int = 0,
    val total: Int = 0,
)

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "hey_skipad_stats")

object StatsRepository {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context =
        appContext ?: error("StatsRepository.init() must be called from Application.onCreate()")

    private object Keys {
        val total = intPreferencesKey("total")
        val today = intPreferencesKey("today")
        val todayKey = stringPreferencesKey("today_key")
    }

    val stats: Flow<SkipStats>
        get() = requireContext().statsStore.data.map { prefs ->
            val key = todayKey()
            val today = if ((prefs[Keys.todayKey] ?: "") == key) (prefs[Keys.today] ?: 0) else 0
            SkipStats(today = today, total = prefs[Keys.total] ?: 0)
        }

    suspend fun bump() {
        val ctx = requireContext()
        val key = todayKey()
        ctx.statsStore.edit { prefs ->
            prefs[Keys.total] = (prefs[Keys.total] ?: 0) + 1
            val base = if ((prefs[Keys.todayKey] ?: "") == key) (prefs[Keys.today] ?: 0) else 0
            prefs[Keys.today] = base + 1
            prefs[Keys.todayKey] = key
        }
    }

    /**
     * Clears both counters. Called when the user wipes the log: leaving the
     * "今日 / 累计" numbers behind made the counter look stuck.
     */
    suspend fun reset() {
        val ctx = requireContext()
        ctx.statsStore.edit { prefs ->
            prefs[Keys.total] = 0
            prefs[Keys.today] = 0
            prefs[Keys.todayKey] = todayKey()
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
