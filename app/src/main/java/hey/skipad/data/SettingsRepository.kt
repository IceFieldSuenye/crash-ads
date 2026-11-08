package hey.skipad.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "hey_skipad_settings")

object SettingsRepository {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context =
        appContext ?: error("SettingsRepository.init() must be called from Application.onCreate()")

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val splashOnly = booleanPreferencesKey("splash_only")
        val skipFullscreenWebAds = booleanPreferencesKey("skip_fullscreen_web_ads")
        val countdown = booleanPreferencesKey("match_countdown")
        val viewId = booleanPreferencesKey("match_view_id")
        val contentDescription = booleanPreferencesKey("match_content_description")
        val gesture = booleanPreferencesKey("gesture_fallback")
        val aggressive = booleanPreferencesKey("aggressive")
        val toast = booleanPreferencesKey("show_toast")
        val cooldown = intPreferencesKey("cooldown_ms")
        val blacklist = stringSetPreferencesKey("blacklist")
        val customKeywords = stringSetPreferencesKey("custom_keywords")
        val disabledKeywords = stringSetPreferencesKey("disabled_keywords")
        val rewardMode = stringPreferencesKey("reward_mode")
        val closePopups = booleanPreferencesKey("close_popups")
        val notifySkip = booleanPreferencesKey("notify_skip")
        val ocrEnabled = booleanPreferencesKey("ocr_enabled")
        val tapAdLabel = booleanPreferencesKey("tap_ad_label")
        val showRunningNotification = booleanPreferencesKey("show_running_notification")
        val autoMuteVolume = booleanPreferencesKey("auto_mute_volume")
        val autoSwipeFeed = booleanPreferencesKey("auto_swipe_feed")
    }

    val settings: Flow<AppSettings>
        get() = requireContext().settingsStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val ctx = requireContext()
        val next = transform(ctx.settingsStore.data.first().toSettings())
        ctx.settingsStore.edit { prefs ->
            prefs[Keys.enabled] = next.enabled
            prefs[Keys.splashOnly] = next.splashOnly
            prefs[Keys.skipFullscreenWebAds] = next.skipFullscreenWebAds
            prefs[Keys.countdown] = next.matchCountdown
            prefs[Keys.viewId] = next.matchViewId
            prefs[Keys.contentDescription] = next.matchContentDescription
            prefs[Keys.gesture] = next.gestureFallback
            prefs[Keys.aggressive] = next.aggressive
            prefs[Keys.toast] = next.showToast
            prefs[Keys.cooldown] = next.cooldownMs
            prefs[Keys.blacklist] = next.blacklist
            prefs[Keys.customKeywords] = next.customKeywords
            prefs[Keys.disabledKeywords] = next.disabledKeywords
            prefs[Keys.rewardMode] = next.rewardMode.name
            prefs[Keys.closePopups] = next.closePopups
            prefs[Keys.notifySkip] = next.notifySkip
            prefs[Keys.ocrEnabled] = next.ocrEnabled
            prefs[Keys.tapAdLabel] = next.tapAdLabel
            prefs[Keys.showRunningNotification] = next.showRunningNotification
            prefs[Keys.autoMuteVolume] = next.autoMuteVolume
            prefs[Keys.autoSwipeFeed] = next.autoSwipeFeed
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        enabled = this[Keys.enabled] ?: true,
        splashOnly = this[Keys.splashOnly] ?: false,
        skipFullscreenWebAds = this[Keys.skipFullscreenWebAds] ?: true,
        matchCountdown = this[Keys.countdown] ?: true,
        matchViewId = this[Keys.viewId] ?: true,
        matchContentDescription = this[Keys.contentDescription] ?: true,
        gestureFallback = this[Keys.gesture] ?: true,
        aggressive = this[Keys.aggressive] ?: false,
        showToast = this[Keys.toast] ?: true,
        cooldownMs = this[Keys.cooldown] ?: 1200,
        blacklist = this[Keys.blacklist] ?: emptySet(),
        customKeywords = this[Keys.customKeywords] ?: emptySet(),
        disabledKeywords = this[Keys.disabledKeywords] ?: emptySet(),
        rewardMode = this[Keys.rewardMode]
            ?.let { raw -> RewardMode.entries.firstOrNull { it.name == raw } }
            ?: RewardMode.ASK,
        closePopups = this[Keys.closePopups] ?: true,
        notifySkip = this[Keys.notifySkip] ?: true,
        ocrEnabled = this[Keys.ocrEnabled] ?: true,
        tapAdLabel = this[Keys.tapAdLabel] ?: true,
        showRunningNotification = this[Keys.showRunningNotification] ?: true,
        autoMuteVolume = this[Keys.autoMuteVolume] ?: true,
        autoSwipeFeed = this[Keys.autoSwipeFeed] ?: true,
    )
}
