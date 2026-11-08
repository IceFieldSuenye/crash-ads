package hey.skipad.data

/** How to treat rewarded ads (watch-to-earn: game items, coins, extra lives...). */
enum class RewardMode {
    /** Skip immediately, even if the ad grants a reward. */
    IMMEDIATE,

    /** Wait until a "reward granted" screen appears, then close it. */
    WAIT_REWARD,

    /** Show a floating prompt asking whether to skip or wait. */
    ASK,
}

/**
 * Immutable snapshot of every user-facing preference.
 * Stored with DataStore; read by both the UI and the accessibility service.
 */
data class AppSettings(
    val enabled: Boolean = true,
    val matchCountdown: Boolean = true,
    val matchViewId: Boolean = true,
    val matchContentDescription: Boolean = true,
    val gestureFallback: Boolean = true,
    val aggressive: Boolean = false,
    val showToast: Boolean = true,
    val cooldownMs: Int = 1200,
    val blacklist: Set<String> = emptySet(),
    val rewardMode: RewardMode = RewardMode.ASK,
    val closePopups: Boolean = true,
    val notifySkip: Boolean = true,
    val ocrEnabled: Boolean = true,
    val tapAdLabel: Boolean = true,
    val showRunningNotification: Boolean = true,
    val autoMuteVolume: Boolean = true,
    val autoSwipeFeed: Boolean = true,
)
