package hey.skipad.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import hey.skipad.BuildConfig
import hey.skipad.data.AppSettings
import hey.skipad.data.LogStore
import hey.skipad.data.RewardMode
import hey.skipad.data.SettingsRepository
import hey.skipad.data.SkipOutcome
import hey.skipad.data.StatsRepository
import hey.skipad.util.NotificationHelper
import hey.skipad.util.OverlayUtils
import hey.skipad.util.PauseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AdSkipService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var settings = AppSettings()

    /** Effective skip labels, rebuilt whenever settings change. */
    @Volatile
    private var activeKeywords: Set<String> = AdDetector.defaultSkipWords

    /** Class name of the current top activity (from window-state events). */
    @Volatile
    private var currentActivityClass: String = ""

    @Volatile
    private var todayCount = 0

    private var pendingPackage: String? = null
    private var lastClickAt = 0L
    private var lastSignature: String? = null
    private var lastSignatureAt = 0L

    private var forceSkipOnce = false
    private var rewardWaitPackage: String? = null
    private var rewardWaitSince = 0L
    private var lastPromptPackage: String? = null
    private var lastPromptAt = 0L
    private var rewardBall: RewardBall? = null
    private var lastDumpAt = 0L
    private var lastDumpKey: String? = null
    private var lastTraceAt = 0L

    private val ocr: ScreenshotOcr by lazy { ScreenshotOcr(this) }
    @Volatile
    private var launchWindowUntil = 0L
    private var lastForegroundPackage: String? = null
    private var adLabelTapPkg: String? = null
    private var adLabelTapCount = 0
    private var lastAdLabelTapAt = 0L
    private var lastVolumePkg: String? = null
    private var lastVolumeTapAt = 0L
    private var lastSwipeAt = 0L

    /** Hard cooldown: while active, no node is tapped at all. */
    private var suppressClickUntil = 0L

    /**
     * Anti machine-gun guard: the same rule on the same package may only fire
     * a few times per window, so a page that ignores our tap cannot be spammed
     * (Bilibili-style menus used to receive three taps in one second).
     */
    private val ruleTapHistory = HashMap<String, MutableList<Long>>()
    private var lastRuleBlockAt = 0L
    private var lastGuardLogAt = 0L

    /** Interactive ads (swipe / shake / open a shop) are backed out of. */
    private var interactiveQuitAttempts = 0
    private var interactiveQuitWindowStart = 0L
    private var lastInteractiveQuitAt = 0L
    private var interactiveAdHits = 0
    private var interactivePrompt: InteractivePrompt? = null
    private var interactivePromptPkg: String? = null
    private var interactivePromptAt = 0L

    /** Answers are remembered per ad sheet, not per app. */
    private var interactiveSkippedSignature: String? = null
    private var interactiveDeclinedSignature: String? = null

    /** Exit-confirmation taps are limited so a stuck dialog cannot loop. */
    private var exitConfirmTaps = 0
    private var lastExitConfirmAt = 0L

    /** Package currently showing a rewarded video (reward protection active). */
    private var rewardContextPkg: String? = null
    private var rewardPopupTapAt = 0L
    private var rewardPopupTapCount = 0

    private var imePackages: Set<String> = emptySet()
    private var imePackagesAt = 0L

    /** Forced-browse ads are abandoned: back out, then click "确定退出". */
    private var lastForceQuitAt = 0L
    private var forceQuitUntil = 0L
    private var forceQuitAttempts = 0
    private var forceQuitWindowStart = 0L

    /** Last time the "继续看视频" link was tapped (WebView overlay de-dupe). */
    private var lastContinueAt = 0L

    private val ocrPollRunnable: Runnable = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() < launchWindowUntil) {
                pendingPackage?.let { maybeOcrTap(it) }
                handler.postDelayed(this, OCR_POLL_MS)
            }
        }
    }

    private val processRunnable = Runnable {
        val pkg = pendingPackage ?: return@Runnable
        processWindow(pkg)
    }

    private val rescanRunnableA = Runnable { pendingPackage?.let(::processWindow) }
    private val rescanRunnableB = Runnable { pendingPackage?.let(::processWindow) }
    private val postClickScan = Runnable {
        pendingPackage?.let(::processWindow)
    }
    private val promptTimeoutRunnable = Runnable {
        dismissRewardBall()
    }

    override fun onServiceConnected() {
        runCatching {
            super.onServiceConnected()
            instance = this
            _running.value = true
            serviceScope.launch {
                SettingsRepository.settings.collect { latest ->
                    settings = latest
                    activeKeywords = (AdDetector.defaultSkipWords - latest.disabledKeywords) +
                        latest.customKeywords
                    if (_running.value) {
                        if (latest.showRunningNotification) {
                            NotificationHelper.notifyRunning(this@AdSkipService)
                        } else {
                            NotificationHelper.cancelRunning(this@AdSkipService)
                        }
                    }
                }
            }
            serviceScope.launch {
                StatsRepository.stats.collect { latest -> todayCount = latest.today }
            }
        }
    }

    /**
     * The ongoing notification drives pause/resume through [PauseReceiver].
     * When the receiver barges in with [ACTION_PAUSE_CHANGED] we drop every
     * queued scan immediately so the switch feels instant.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_CHANGED -> applyPauseState()
            ACTION_TOGGLE_PAUSE -> {
                PauseState.toggle(this)
                applyPauseState()
            }
        }
        if (instance == null) {
            // startService() on an AccessibilityService that is not bound by
            // the system would create a zombie: shut it down again.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun applyPauseState() {
        if (PauseState.isPaused(this)) {
            handler.removeCallbacks(processRunnable)
            handler.removeCallbacks(rescanRunnableA)
            handler.removeCallbacks(rescanRunnableB)
            handler.removeCallbacks(postClickScan)
            handler.removeCallbacks(ocrPollRunnable)
            launchWindowUntil = 0
            dismissRewardBall()
            dismissInteractivePrompt()
            rewardWaitPackage = null
            rewardContextPkg = null
        }
        NotificationHelper.notifyRunning(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _running.value = false
        rewardContextPkg = null
        dismissInteractivePrompt()
        NotificationHelper.cancelRunning(this)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Any exception here would take the whole accessibility service down
        // and leave the user with a "running" switch that silently does nothing.
        runCatching { handleAccessibilityEvent(event) }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event ?: return
        val pkg = source.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == packageName) return
        if (isSystemPackage(pkg)) return
        if (!settings.enabled) return
        if (settings.blacklist.contains(pkg)) return
        if (!isRelevantEvent(source.eventType)) return

        pendingPackage = pkg
        val isWindowChange = source.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (isWindowChange) {
            val className = source.className?.toString().orEmpty()
            if (className.isNotEmpty()) currentActivityClass = className
        }
        if (pkg != lastForegroundPackage) {
            // A new app came to the foreground: run control-tree detection and
            // OCR in parallel for the first 6 seconds.
            lastForegroundPackage = pkg
            launchWindowUntil = SystemClock.elapsedRealtime() + LAUNCH_WINDOW_MS
            handler.removeCallbacks(ocrPollRunnable)
            handler.postDelayed(ocrPollRunnable, OCR_POLL_MS)
        }
        handler.removeCallbacks(processRunnable)
        handler.postDelayed(processRunnable, if (isWindowChange) FAST_DELAY_MS else SLOW_DELAY_MS)

        // Splash / interstitial ads finish loading slightly after the window appears.
        if (isWindowChange) {
            handler.removeCallbacks(rescanRunnableA)
            handler.removeCallbacks(rescanRunnableB)
            handler.postDelayed(rescanRunnableA, 150L)
            handler.postDelayed(rescanRunnableB, 420L)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        dismissRewardBall()
        dismissInteractivePrompt()
        runCatching { ocr.close() }
        NotificationHelper.cancelRunning(this)
        serviceScope.cancel()
        instance = null
        _running.value = false
        super.onDestroy()
    }

    private fun isRelevantEvent(type: Int): Boolean =
        type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED

    /**
     * System UI surfaces (notification shade, status bar, settings, launcher,
     * IME, vendor side panels) must never be scanned: our own notifications
     * live there and a global side bar would otherwise be tapped through.
     * Soft keyboards are matched through InputMethodManager as well, because
     * vendor IMEs such as com.baidu.input do not contain the "inputmethod" token.
     */
    private fun isSystemPackage(pkg: String): Boolean {
        if (pkg.isEmpty()) return true
        if (pkg == "android") return true
        if (SYSTEM_PACKAGE_PREFIXES.any { pkg.startsWith(it) }) return true
        if (SYSTEM_PACKAGE_KEYWORDS.any { pkg.contains(it) }) return true
        return isImePackage(pkg)
    }

    private fun isImePackage(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (imePackages.isEmpty() || now - imePackagesAt > 30_000L) {
            imePackagesAt = now
            imePackages = runCatching {
                val imm = getSystemService(InputMethodManager::class.java)
                if (imm == null) {
                    emptySet()
                } else {
                    (imm.inputMethodList + imm.enabledInputMethodList)
                        .mapNotNull { it.packageName }
                        .toSet()
                }
            }.getOrDefault(emptySet())
        }
        return pkg in imePackages
    }

    /** A soft keyboard window is on top: the tree below it is not an ad. */
    private fun isImeWindowActive(): Boolean = runCatching {
        windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                (window.isActive || window.isFocused)
        }
    }.getOrDefault(false)

    private fun processWindow(pkg: String) {
        runCatching { doProcessWindow(pkg) }
            .onFailure { error ->
                runCatching {
                    LogStore.add(
                        "hey.skipad",
                        "服务异常",
                        error.javaClass.simpleName,
                        SkipOutcome.FAILED,
                    )
                }
            }
    }

    private fun doProcessWindow(pkg: String) {
        if (!settings.enabled) return
        if (PauseState.isPaused(this)) return
        if (isSystemPackage(pkg)) return
        if (settings.blacklist.contains(pkg)) return

        val now = SystemClock.elapsedRealtime()
        if (now < suppressClickUntil) return
        if (now - lastClickAt < settings.cooldownMs) return

        // Soft keyboard on screen: never inspect or tap the tree underneath.
        if (isImeWindowActive()) return

        val root = rootInActiveWindow ?: return
        val rootPackage = root.packageName?.toString().orEmpty()
        val tree = NodeExtractor.extract(root)
        dumpTreeForDebug(pkg, rootPackage, tree.nodes)

        if (rootPackage.isNotEmpty() && rootPackage != pkg) return
        if (isSystemPackage(rootPackage)) return

        val (width, height) = screenSize()

        // "仅开屏跳过": only the short window right after an app comes forward.
        if (settings.splashOnly && SystemClock.elapsedRealtime() > launchWindowUntil) return

        // Full-screen H5 ads can be excluded: some apps open their own WebView
        // content and skipping it would close real pages.
        if (!settings.skipFullscreenWebAds &&
            AdDetector.isFullscreenWebAd(tree.nodes, width, height)
        ) {
            return
        }

        val strongContext = AdDetector.hasStrongAdContext(tree.nodes)

        // 0a) Interactive ads ("上滑浏览 获取奖励", "跳转浏览 15 秒领奖") only
        //     advance when the user swipes, so they are quit outright: no
        //     prompt, no waiting.
        if (AdDetector.isInteractiveAd(tree.nodes)) {
            quitInteractiveAd(pkg, tree, width, height, confirmed = false)
            return
        }

        // 0) Promo sheets that demand a browse / download detour ("去浏览 15 秒",
        //    "去下载 加速 领奖") are abandoned outright: the reward is never
        //    worth performing tasks for.
        if (AdDetector.isForcedBrowseAd(tree.nodes)) {
            val forcedAt = SystemClock.elapsedRealtime()
            if (forcedAt - forceQuitWindowStart > 30_000L) {
                forceQuitWindowStart = forcedAt
                forceQuitAttempts = 0
                lastForceQuitAt = 0L
            }
            if (forcedAt - lastForceQuitAt < 1500L) return
            if (forceQuitAttempts >= 3) return
            forceQuitAttempts++
            lastForceQuitAt = forcedAt

            forceQuitUntil = forcedAt + 8000L
            dismissRewardBall()

            val skip = AdDetector.findSkipButton(tree.nodes)
                ?: AdDetector.findAnyCornerClose(tree.nodes, width, height)
            val skipNode = skip?.let { tree.refs.getOrNull(it.nodeIndex) }
            if (skipNode != null && performSkip(skipNode) != SkipOutcome.FAILED) {
                lastClickAt = SystemClock.elapsedRealtime()
                if (forceQuitAttempts == 1) {
                    LogStore.add(pkg, "强制浏览广告", "跳过退出", SkipOutcome.CLICKED)
                }
                return
            }

            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            if (forceQuitAttempts == 1) {
                LogStore.add(pkg, "强制浏览广告", "直接退出", SkipOutcome.GESTURE)
            }
            return
        }

        // While backing out, answer the confirmation with "确定退出" and never
        // with 继续观看 (that would undo the exit).
        if (SystemClock.elapsedRealtime() < forceQuitUntil) {
            // The ad is already gone (usually we landed back on the app's own
            // home screen): stop backing out, otherwise the home page keeps
            // receiving BACK every second.
            if (!strongContext && AdDetector.findExitConfirm(tree.nodes) == null) {
                forceQuitUntil = 0L
                forceQuitAttempts = 0
                return
            }
            val confirm = AdDetector.findExitConfirm(tree.nodes)
            if (confirm != null && canTapExitConfirm()) {
                val node = tree.refs.getOrNull(confirm.nodeIndex)
                if (node != null && performSkip(node) != SkipOutcome.FAILED) {
                    lastClickAt = SystemClock.elapsedRealtime()
                    forceQuitUntil = 0
                    LogStore.add(pkg, confirm.label, "退出确认", SkipOutcome.CLICKED)
                    return
                }
            }
            val quitAt = SystemClock.elapsedRealtime()
            if (forceQuitAttempts < 3 && quitAt - lastForceQuitAt > 1200L) {
                lastForceQuitAt = quitAt
                forceQuitAttempts++
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } else {
                forceQuitUntil = 0L
                forceQuitAttempts = 0
            }
            return
        }

        // 1) Mute the video ad via its speaker icon.
        if (settings.autoMuteVolume) {
            val volume = AdDetector.findVolumeButton(tree.nodes, width, height, strongContext)
            if (volume != null && canTapVolume(pkg)) {
                val node = tree.refs.getOrNull(volume.nodeIndex)
                if (node != null && performSkip(node) != SkipOutcome.FAILED) {
                    lastVolumeTapAt = SystemClock.elapsedRealtime()
                    LogStore.add(pkg, "音量图标", "静音", SkipOutcome.CLICKED)
                    return
                }
            }
        }

        val config = DetectorConfig(
            matchCountdown = settings.matchCountdown,
            matchViewId = settings.matchViewId,
            matchContentDescription = settings.matchContentDescription,
            matchCloseIcon = true,
            aggressive = settings.aggressive,
        )

        val hit = findSkip(tree, pkg, width, height, config, false, false)
            ?: scanPopupClose(pkg, width, height, config)
        traceDetection(pkg, tree.nodes, hit?.second)

        // Feed ads (e.g. QQ Zone): the "关闭此条广告" sheet is already open.
        val closeThis = AdDetector.findCloseThisAd(tree.nodes)
        if (closeThis != null) {
            forceSkipOnce = false
            launchWindowUntil = 0
            handler.removeCallbacks(ocrPollRunnable)
            clickCandidate(tree, closeThis, pkg)
            return
        }

        if (hit != null) {
            forceSkipOnce = false
            launchWindowUntil = 0
            handler.removeCallbacks(ocrPollRunnable)
            clickCandidate(hit.first, hit.second, pkg)
            return
        }

        // Two-step feed ad: tap the small "广告" pill to open the close sheet.
        if (settings.tapAdLabel) {
            val adLabel = AdDetector.findAdLabel(tree.nodes, width, height)
            if (adLabel != null && canTapAdLabel(pkg)) {
                clickCandidate(tree, adLabel, pkg)
                return
            }
        }

        if (maybeSwipeFeed(pkg, tree)) return

        if (SystemClock.elapsedRealtime() < launchWindowUntil) {
            maybeOcrTap(pkg)
        }
    }

    private fun canTapVolume(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (pkg != lastVolumePkg) {
            lastVolumePkg = pkg
            lastVolumeTapAt = 0L
        }
        if (now - lastVolumeTapAt < 60_000L) return false
        return true
    }

    /**
     * Feed ads (Douyin etc.) have no skip button; swipe up to move past them.
     */
    private fun maybeSwipeFeed(pkg: String, tree: NodeTree): Boolean {
        if (!settings.autoSwipeFeed) return false
        if (pkg != DOUYIN_PACKAGE) return false
        if (SystemClock.elapsedRealtime() - lastSwipeAt < 3000L) return false
        if (!AdDetector.hasAdLabel(tree.nodes)) return false

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * 0.5f, height * 0.72f)
            lineTo(width * 0.5f, height * 0.28f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 280L))
            .build()
        if (!dispatchGesture(gesture, null, null)) return false

        lastSwipeAt = SystemClock.elapsedRealtime()
        LogStore.add(pkg, "信息流广告", "自动上滑", SkipOutcome.GESTURE)
        return true
    }

    private fun canTapAdLabel(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (pkg != adLabelTapPkg) {
            adLabelTapPkg = pkg
            adLabelTapCount = 0
        }
        if (now - lastAdLabelTapAt < 2000L) return false
        if (adLabelTapCount >= 3) return false
        lastAdLabelTapAt = now
        adLabelTapCount++
        return true
    }

    /** Rate-limit closing interrupt popups so a stuck loop cannot machine-gun taps. */
    private fun canDismissRewardPopup(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - rewardPopupTapAt < 2500L) return false
        if (rewardPopupTapCount >= 6) return false
        rewardPopupTapAt = now
        rewardPopupTapCount++
        return true
    }

    private fun maybeOcrTap(pkg: String) {
        if (!settings.ocrEnabled) return
        // Never OCR-skip while a reward is pending: the "跳过" text on a
        // rewarded video would otherwise be tapped and the reward lost.
        if (rewardWaitPackage == pkg || rewardContextPkg == pkg) return
        if (SystemClock.elapsedRealtime() - lastClickAt < settings.cooldownMs) return
        // Only OCR inside a window that already looks like an ad. On an app's
        // own home page the model happily "reads" 跳过 in banners and the
        // resulting tap looked like a random click.
        val context = activeTree(pkg) ?: return
        if (!AdDetector.hasStrongAdContext(context.nodes) &&
            !AdDetector.hasAdLabel(context.nodes)
        ) {
            return
        }
        runCatching {
            ocr.scan { rect ->
                runCatching {
                    if (rect.width() <= 0 || rect.height() <= 0) return@runCatching
                    val path = Path().apply { moveTo(rect.exactCenterX(), rect.exactCenterY()) }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0L, GESTURE_DURATION_MS))
                        .build()
                    if (dispatchGesture(gesture, null, null)) {
                        lastClickAt = SystemClock.elapsedRealtime()
                        launchWindowUntil = 0
                        handler.removeCallbacks(ocrPollRunnable)
                        LogStore.add(pkg, "图片识别 · 跳过", "OCR", SkipOutcome.GESTURE)
                        serviceScope.launch { StatsRepository.bump() }
                        if (settings.showToast) showSkipToast("OCR")
                        if (settings.notifySkip) {
                            NotificationHelper.notifySkip(applicationContext, "图片识别 · 跳过", "OCR", todayCount)
                        }
                    }
                }
            }
        }
    }

    /**
     * Looks for small (non-fullscreen) popup / banner windows that carry an
     * ad label and a close affordance.
     */
    private fun scanPopupClose(
        pkg: String,
        screenWidth: Int,
        screenHeight: Int,
        config: DetectorConfig,
    ): Pair<NodeTree, SkipCandidate>? {
        if (!settings.closePopups) return null
        var best: Pair<NodeTree, SkipCandidate>? = null
        runCatching {
            val screenArea = screenWidth.toLong() * screenHeight
            for (window in windows) {
                val root = window.root ?: continue
                if (root.packageName?.toString() != pkg) continue
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height()
                if (area >= screenArea * 92 / 100) continue
                if (area < screenArea * 4 / 100) continue
                val tree = NodeExtractor.extract(root)
                val candidate = AdDetector.detectPopupClose(tree.nodes, bounds, config) ?: continue
                val current = best
                if (current == null || candidate.score > current.second.score) {
                    best = tree to candidate
                }
            }
        }
        return best
    }

    /**
     * Returns true when this round should NOT click (waiting for a reward).
     */
    private fun rewardGate(pkg: String, nodes: List<NodeSnapshot>): Boolean {
        if (forceSkipOnce) {
            forceSkipOnce = false
            rewardWaitPackage = null
            rewardContextPkg = null
            dismissRewardBall()
            return false
        }

        // The video finished (end card / CTA visible): the reward is safe now,
        // so stop protecting it and let the ad be closed.
        val granted = AdDetector.rewardGranted(nodes) || AdDetector.rewardFinished(nodes)
        if (granted) {
            rewardWaitPackage = null
            rewardContextPkg = null
            dismissRewardBall()
            return false
        }

        val promise = AdDetector.rewardPromise(nodes)
        // Ads that never mention a reward still turn out to be rewarded videos
        // ("再看 7s 可领取奖励" only shows up after a skip). Once the exit sheet
        // proved it, stay in protection mode for this ad.
        val resumed = rewardContextPkg == pkg &&
            rewardWaitPackage == pkg &&
            SystemClock.elapsedRealtime() - rewardWaitSince < 120_000L
        if ((!promise && !resumed) || settings.rewardMode == RewardMode.IMMEDIATE) return false

        if (rewardWaitPackage != pkg) {
            rewardWaitPackage = pkg
            rewardWaitSince = SystemClock.elapsedRealtime()
        }

        if (settings.rewardMode == RewardMode.ASK) {
            if (OverlayUtils.canDrawOverlays(this) &&
                rewardBall?.isShowing() != true && canPromptReward(pkg)
            ) {
                showRewardBall(pkg)
            }
        }

        // The reward is still pending: never close the ad, no matter how long
        // it takes. The old 90 s cap released the click and produced exactly
        // the "暂未获得奖励" screen. Users who want to give up can tap the
        // floating prompt (ASK mode) or pause the service.
        logGuardOnce(pkg, "奖励保护中", "等待奖励发放")
        return true
    }

    /**
     * Current display size in the right orientation. Accessibility services
     * keep their Resources alive across rotations, so [android.util.DisplayMetrics]
     * can still report portrait numbers while the game runs in landscape, which
     * used to break every ratio-based ad heuristic.
     */
    private fun screenSize(): Pair<Int, Int> {
        var width = resources.displayMetrics.widthPixels
        var height = resources.displayMetrics.heightPixels
        if (width <= 0 || height <= 0) {
            width = 1080
            height = 2400
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val manager = getSystemService(WindowManager::class.java)
                val bounds = manager?.maximumWindowMetrics?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    width = bounds.width()
                    height = bounds.height()
                }
            }
        }
        return width to height
    }

    /** Rate-limited informational log so a guard cannot flood the log page. */
    private fun logGuardOnce(pkg: String, label: String, rule: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGuardLogAt < 8_000L) return
        lastGuardLogAt = now
        LogStore.add(pkg, label, rule, SkipOutcome.BLOCKED)
    }

    /** At most two exit-confirmation taps in a 10 s window. */
    private fun canTapExitConfirm(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastExitConfirmAt > 10_000L) exitConfirmTaps = 0
        if (exitConfirmTaps >= 2) return false
        if (now - lastExitConfirmAt < 1_200L) return false
        exitConfirmTaps++
        lastExitConfirmAt = now
        return true
    }

    /**
     * Quits an interactive ad. An explicit "跳过" label or a corner × is
     * preferred over the back key: some SDKs swallow BACK, which is why the
     * ad used to stay on screen after the user confirmed.
     */
    private fun quitInteractiveAd(
        pkg: String,
        tree: NodeTree,
        width: Int,
        height: Int,
        confirmed: Boolean,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInteractiveQuitAt < 1_200L) return
        if (now - interactiveQuitWindowStart > 20_000L) {
            interactiveQuitWindowStart = now
            interactiveQuitAttempts = 0
        }
        if (interactiveQuitAttempts >= 4) return
        interactiveQuitAttempts++
        interactiveAdHits++
        lastInteractiveQuitAt = now
        forceQuitUntil = now + 8_000L
        forceQuitAttempts = 0
        rewardWaitPackage = null
        rewardContextPkg = null
        dismissRewardBall()

        val skip = AdDetector.findSkipButton(tree.nodes)
            ?: AdDetector.findAnyCornerClose(tree.nodes, width, height)
        val skipNode = skip?.let { tree.refs.getOrNull(it.nodeIndex) }
        if (skipNode != null && performSkip(skipNode) != SkipOutcome.FAILED) {
            logGuardOnce(pkg, "互动广告", if (confirmed) "已确认跳过" else "直接退出")
            return
        }

        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        logGuardOnce(pkg, "互动广告", if (confirmed) "已确认跳过" else "直接退出")
    }

    private fun showInteractivePrompt(pkg: String, signature: String) {
        val prompt = InteractivePrompt(
            context = this,
            onSkip = {
                interactiveSkippedSignature = signature
                interactiveDeclinedSignature = null
                interactivePrompt = null
                interactivePromptPkg = null
                handler.post(processRunnable)
            },
            onStay = {
                interactiveDeclinedSignature = signature
                interactivePrompt = null
                interactivePromptPkg = null
            },
        )
        if (prompt.show()) {
            interactivePrompt = prompt
            interactivePromptPkg = pkg
            interactivePromptAt = SystemClock.elapsedRealtime()
            LogStore.add(pkg, "互动广告", "询问是否跳过", SkipOutcome.BLOCKED)
        } else {
            quitInteractiveAd(pkg, activeTree(pkg) ?: return, 0, 0, confirmed = false)
        }
    }

    private fun dismissInteractivePrompt() {
        interactivePrompt?.dismiss()
        interactivePrompt = null
        interactivePromptPkg = null
    }

    private fun activeTree(pkg: String): NodeTree? {
        val root = rootInActiveWindow ?: return null
        val rootPackage = root.packageName?.toString().orEmpty()
        if (rootPackage.isNotEmpty() && rootPackage != pkg) return null
        return NodeExtractor.extract(root)
    }

    private fun findSkip(
        tree: NodeTree,
        pkg: String,
        width: Int,
        height: Int,
        config: DetectorConfig,
        suppressCloseAd: Boolean,
        rewardSettled: Boolean,
    ): Pair<NodeTree, SkipCandidate>? {
        val adContextHint = AdDetector.isRewardActivity(currentActivityClass)
        val primary = AdDetector.detect(
            tree.nodes, width, height, config, suppressCloseAd, rewardSettled,
            activeKeywords, adContextHint,
        )
        if (primary != null) return tree to primary

        for (root in otherWindowRoots(pkg)) {
            val other = NodeExtractor.extract(root)
            val candidate = AdDetector.detect(
                other.nodes, width, height, config, suppressCloseAd, rewardSettled,
                activeKeywords, adContextHint,
            ) ?: continue
            return other to candidate
        }
        return null
    }

    private fun otherWindowRoots(pkg: String): List<AccessibilityNodeInfo> {
        val roots = ArrayList<AccessibilityNodeInfo>(2)
        runCatching {
            val active = rootInActiveWindow
            for (window in windows) {
                if (roots.size >= 2) break
                val root = window.root ?: continue
                if (root == active) continue
                if (root.packageName?.toString() != pkg) continue
                roots.add(root)
            }
        }
        return roots
    }

    /**
     * Taps the "继续观看" affordance of a rewarded video. The link lives inside
     * a WebView, so repeat taps are de-duplicated: pressing it again before the
     * SDK reacts stacks identical taps and flips the ad back to "暂未获得奖励".
     */
    private fun clickContinueWatching(
        tree: NodeTree,
        candidate: SkipCandidate,
        pkg: String,
        rule: String,
    ): Boolean {
        val node = tree.refs.getOrNull(candidate.nodeIndex) ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastContinueAt < 2_000L) return false
        if (performButtonTap(node) == SkipOutcome.FAILED) return false
        lastContinueAt = now
        lastClickAt = now
        forceQuitUntil = 0L
        rewardContextPkg = pkg
        rewardWaitPackage = pkg
        rewardWaitSince = now
        LogStore.add(pkg, candidate.label, rule, SkipOutcome.CLICKED)
        return true
    }

    private fun clickCandidate(tree: NodeTree, candidate: SkipCandidate, pkg: String) {
        val node = tree.refs.getOrNull(candidate.nodeIndex) ?: return
        val signature = pkg + "|" + candidate.rule + "|" + candidate.label + "|" + node.boundsKey()
        val now = SystemClock.elapsedRealtime()
        if (signature == lastSignature && now - lastSignatureAt < SAME_TARGET_WINDOW_MS) return
        if (!ruleAllowed(pkg, candidate.rule)) return

        val outcome = performSkip(node)
        if (outcome == SkipOutcome.FAILED) return

        lastClickAt = now
        lastSignature = signature
        lastSignatureAt = now
        // Hard cooldown after every successful tap: the window itself keeps
        // firing content-change events, and re-scanning immediately used to
        // produce three taps per second on pages that ignore them.
        suppressClickUntil = now + settings.cooldownMs

        LogStore.add(pkg, candidate.label, candidate.rule, outcome)
        serviceScope.launch { StatsRepository.bump() }

        // Re-check after the cooldown: some splash ads ignore the first
        // coordinate tap until their skip button becomes active.
        handler.removeCallbacks(postClickScan)
        handler.postDelayed(postClickScan, settings.cooldownMs + 150L)

        if (settings.showToast) {
            showSkipToast(candidate.rule)
        }
        if (settings.notifySkip) {
            NotificationHelper.notifySkip(
                applicationContext,
                candidate.label,
                candidate.rule,
                todayCount,
            )
        }
    }

    /**
     * Anti machine-gun guard. A page that ignores our tap (or a UI that keeps
     * matching the same rule) must not be clicked over and over: the same rule
     * on the same package is limited to [RULE_MAX_TAPS] taps per
     * [RULE_WINDOW_MS] window.
     */
    private fun ruleAllowed(pkg: String, rule: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (ruleTapHistory.size > 48) {
            ruleTapHistory.entries.removeAll { (_, taps) ->
                taps.isEmpty() || now - taps.last() > RULE_WINDOW_MS
            }
        }
        val key = pkg + "|" + rule
        val taps = ruleTapHistory.getOrPut(key) { ArrayList(4) }
        taps.removeAll { now - it > RULE_WINDOW_MS }
        if (taps.size >= RULE_MAX_TAPS) {
            if (now - lastRuleBlockAt > 8_000L) {
                lastRuleBlockAt = now
                LogStore.add(pkg, rule, "冷却中 · 暂不重复点击", SkipOutcome.BLOCKED)
            }
            return false
        }
        taps.add(now)
        return true
    }

    /** System Toast channel, pinned to the bottom of the screen. */
    private fun showSkipToast(rule: String) {
        val toast = Toast.makeText(applicationContext, "已跳过 · $rule", Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.BOTTOM, 0, (resources.displayMetrics.density * 72).toInt())
        toast.show()
    }

    /**
     * Taps a button inside a dialog. A node click is preferred, then a
     * button-sized clickable ancestor (some SDKs attach the handler to the
     * wrapper of the label instead of the label itself), and only then a
     * coordinate tap.
     */
    private fun performButtonTap(node: AccessibilityNodeInfo): SkipOutcome {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return SkipOutcome.CLICKED
        }
        val metrics = resources.displayMetrics
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 3) {
            val rect = Rect()
            parent.getBoundsInScreen(rect)
            val buttonSized = rect.width() in 1..(metrics.widthPixels * 85 / 100) &&
                rect.height() in 1..(metrics.heightPixels * 22 / 100)
            if (buttonSized && parent.isClickable &&
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return SkipOutcome.CLICKED
            }
            parent = parent.parent
            depth++
        }
        return performSkip(node)
    }

    private fun performSkip(node: AccessibilityNodeInfo): SkipOutcome {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return SkipOutcome.CLICKED
        }

        // Non-clickable nodes (WebView links, custom-drawn buttons) must be
        // tapped by coordinate: clicking an ancestor container often reports
        // success without ever triggering the link.
        if (settings.gestureFallback) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                val path = Path().apply {
                    moveTo(bounds.exactCenterX(), bounds.exactCenterY())
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, GESTURE_DURATION_MS))
                    .build()
                if (dispatchGesture(gesture, null, null)) {
                    return SkipOutcome.GESTURE
                }
            }
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < MAX_PARENT_SEARCH) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return SkipOutcome.CLICKED
            }
            parent = parent.parent
            depth++
        }
        return SkipOutcome.FAILED
    }

    private fun canPromptReward(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastPromptPackage && now - lastPromptAt < PROMPT_COOLDOWN_MS) return false
        lastPromptPackage = pkg
        lastPromptAt = now
        return true
    }

    private fun showRewardBall(pkg: String) {
        if (rewardBall?.isShowing() == true) return
        val ball = RewardBall(this) {
            forceSkipOnce = true
            handler.post(processRunnable)
        }
        if (!ball.show()) {
            rewardWaitPackage = pkg
            rewardWaitSince = SystemClock.elapsedRealtime()
            return
        }
        rewardBall = ball
        handler.removeCallbacks(promptTimeoutRunnable)
        handler.postDelayed(promptTimeoutRunnable, PROMPT_TIMEOUT_MS)
    }

    private fun dismissRewardBall() {
        handler.removeCallbacks(promptTimeoutRunnable)
        rewardBall?.dismiss()
        rewardBall = null
    }

    private fun traceDetection(pkg: String, nodes: List<NodeSnapshot>, candidate: SkipCandidate?) {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        if (now - lastTraceAt < 150L) return
        lastTraceAt = now
        runCatching {
            val dir = getExternalFilesDir(null) ?: return@runCatching
            val file = File(dir, "trace.log")
            val builder = StringBuilder(160)
            builder.append(now % 1000000).append(" pkg=").append(pkg)
                .append(" n=").append(nodes.size)
                .append(" cand=")
                .append(candidate?.let { "${it.rule}#${it.score}(${it.label})" } ?: "-")
            val keys = nodes.asSequence()
                .filter { node ->
                    val text = node.text.trim()
                    text.contains("跳过") || text.contains("广告") || text.contains("廣告") ||
                        (text.matches(Regex("^\\d{1,2}$")) && node.bounds.top < 900 && node.bounds.right > 500)
                }
                .take(8)
                .toList()
            for (key in keys) {
                builder.append(" || ").append(key.text)
                    .append('[').append(key.bounds.left).append(',').append(key.bounds.top).append(']')
                    .append(if (key.clickable) "C" else "-")
                    .append(if (key.visible) "V" else "H")
            }
            builder.append('\n')
            file.appendText(builder.toString())
        }
    }

    private fun dumpTreeForDebug(pkg: String, rootPackage: String, nodes: List<NodeSnapshot>) {        if (!BuildConfig.DEBUG) return
        val key = pkg + "/" + rootPackage
        val now = System.currentTimeMillis()
        if (key == lastDumpKey && now - lastDumpAt < 1000L) return
        lastDumpKey = key
        lastDumpAt = now
        runCatching {
            val dir = getExternalFilesDir(null) ?: return@runCatching
            val file = File(dir, "tree_$now.txt")
            val metrics = resources.displayMetrics
            val builder = StringBuilder(nodes.size * 96)
            builder.append("pkg=").append(pkg)
                .append(" rootPkg=").append(rootPackage)
                .append(" time=").append(now)
                .append(" screen=").append(metrics.widthPixels).append('x').append(metrics.heightPixels)
                .append(" nodes=").append(nodes.size)
                .append('\n')
            for (node in nodes) {
                builder.append(node.index).append('|')
                    .append(node.depth).append('|')
                    .append(node.className.substringAfterLast('.')).append('|')
                    .append(node.text.replace('\n', ' ')).append('|')
                    .append(node.contentDescription.replace('\n', ' ')).append('|')
                    .append(node.viewId.substringAfterLast('/')).append('|')
                    .append(node.bounds.left).append(',').append(node.bounds.top).append(',')
                    .append(node.bounds.right).append(',').append(node.bounds.bottom).append('|')
                    .append(if (node.clickable) "click" else "-").append('|')
                    .append(if (node.visible) "vis" else "invis").append('\n')
            }
            file.writeText(builder.toString())
        }
    }

    private fun AccessibilityNodeInfo.boundsKey(): String {
        val rect = Rect()
        getBoundsInScreen(rect)
        return "${rect.left},${rect.top},${rect.right},${rect.bottom}"
    }

    companion object {
        private const val FAST_DELAY_MS = 0L
        private const val SLOW_DELAY_MS = 40L
        private const val SAME_TARGET_WINDOW_MS = 2500L
        private const val MAX_PARENT_SEARCH = 8
        private const val GESTURE_DURATION_MS = 40L

        /** Same rule / same package: at most [RULE_MAX_TAPS] taps per window. */
        private const val RULE_WINDOW_MS = 10_000L
        private const val RULE_MAX_TAPS = 4

        /** After this many interactive ads the floating prompt asks once. */
        private const val INTERACTIVE_PROMPT_AFTER = 1

        private const val REWARD_EXIT_COOLDOWN_MS = 5_000L
        private const val PROMPT_COOLDOWN_MS = 30_000L
        private const val PROMPT_TIMEOUT_MS = 45_000L
        private const val LAUNCH_WINDOW_MS = 6_000L
        private const val OCR_POLL_MS = 500L
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"

        /**
         * System surfaces that sometimes float above the current app and would
         * be mistaken for an ad. Covers the side bars / game overlays of the
         * major Chinese vendors plus AOSP.
         */
        private val SYSTEM_PACKAGE_PREFIXES = listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.shell",
            "com.android.providers",
            "com.vivo.card",
            "com.vivo.smartshot",
            "com.vivo.upslide",
            "com.vivo.timerwidget",
            "com.vivo.gamecube",
            "com.vivo.frameworkui",
            "com.vivo.assistant",
            "com.vivo.vtouch",
            "com.vivo.screenrecorder",
            "com.vivo.dream",
            "com.vivo.abe",
            "com.iqoo.card",
            "com.miui.contentextension",
            "com.miui.securitycenter",
            "com.miui.freeform",
            "com.miui.notification",
            "com.miui.packageinstaller",
            "com.miui.powerkeeper",
            "com.coloros.safecenter",
            "com.coloros.smartsidebar",
            "com.coloros.notificationmanager",
            "com.coloros.assistantscreen",
            "com.oplus.safecenter",
            "com.oplus.gameassist",
            "com.huawei.systemmanager",
            "com.huawei.gameassistant",
            "com.hihonor.gameassistant",
            "com.sec.android.app.cocktailbarservice",
        )

        /**
         * Vendor-agnostic keywords for system UI surfaces. Deliberately avoids
         * media/app names, so "小米视频" or "网易云音乐" ads are still skipped.
         */
        private val SYSTEM_PACKAGE_KEYWORDS = listOf(
            "systemui",
            "launcher",
            "inputmethod",
            "input_method",
            "keyboard",
            ".ime",
            "packageinstaller",
            "permissioncontroller",
            "gameassistant",
            "gamebooster",
            "gamespace",
            "gamecube",
            "gameassist",
            "sidebar",
            "assistantscreen",
            "cockpit",
            "sidepanel",
            "freeform",
            "multiwindow",
            "smartshot",
            "screenrecorder",
            "upslide",
            "timerwidget",
            "frameworkui",
            "notificationmanager",
            "powerkeeper",
            "systemmanager",
            "contentextension",
            "cocktailbarservice",
            "cornerhubble",
        )

        const val ACTION_PAUSE_CHANGED = "hey.skipad.action.PAUSE_CHANGED"
        const val ACTION_TOGGLE_PAUSE = "hey.skipad.action.TOGGLE_PAUSE"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        @Volatile
        var instance: AdSkipService? = null
            private set
    }
}
