package hey.skipad.service

import android.graphics.Rect

/**
 * Heuristic ad-skip detector.
 *
 * Guard rail: positional heuristics (countdown numerals, text-less close icons,
 * "关闭") are only evaluated when the window actually looks like an ad
 * (a skip label, an ad-SDK view id, or a small "广告" label). On ordinary app
 * home screens none of those exist, so unrelated icons such as the "消息"
 * button are never touched.
 */
data class SkipCandidate(
    val nodeIndex: Int,
    val score: Int,
    val rule: String,
    val label: String,
)

data class DetectorConfig(
    val matchCountdown: Boolean = true,
    val matchViewId: Boolean = true,
    val matchContentDescription: Boolean = true,
    val matchCloseIcon: Boolean = true,
    val aggressive: Boolean = false,
)

/**
 * The "关闭广告后将无法获得奖励，是否继续？" confirmation. [resume] is the
 * "继续观看" button that cancels the exit, so the reward is not lost.
 */
data class RewardExitPrompt(
    val resume: SkipCandidate?,
    val message: String,
)

object AdDetector {

    private const val MIN_NODE_SIZE_PX = 12
    private const val MAX_ICON_SIZE_PX = 140
    private const val AD_LABEL_MAX_WIDTH_PX = 140

    private val skipWords = setOf(
        "跳过", "跳過", "略过", "略過",
        "跳过广告", "跳過廣告", "跳过此广告", "跳过所有广告",
        "跳过开屏广告", "跳過開屏廣告", "跳过开屏", "跳過開屏",
        "关闭广告", "關閉廣告", "关闭此广告", "关闭广告页",
        "skip", "skipad", "skipads", "skip ad", "skip ads", "skip advertisement",
        "close ad", "closead", "close ads",
    )

    /** Texts that close the ad itself; forbidden while a reward is pending. */
    private val closeAdWords = listOf(
        "关闭广告", "關閉廣告", "关闭此广告", "关闭广告页",
        "close ad", "close ads",
    )

    private val closeWords = setOf("关闭", "關閉", "close", "×", "✕", "╳", "✖")

    private val rewardGrantedWords = listOf(
        "已获得奖励", "奖励已发放", "奖励已到账", "奖励已领取", "已领取",
        "领取成功", "成功领取", "恭喜获得", "获得奖励", "获得道具", "已获得",
    )

    /**
     * Phrases that mean "a reward is waiting at the end of this video".
     * Deliberately narrow: generic words such as "看广告" or "看广告得" appear
     * on normal pages ("看广告得时长") and used to raise false positives.
     */
    private val rewardPromiseWords = listOf(
        "领取奖励", "領取獎勵", "获取奖励", "獲取獎勵", "获得奖励", "獲得獎勵",
        "奖励已发放", "奖励已到账", "双倍奖励", "雙倍獎勵",
        "看完广告", "看完廣告", "看完全部", "观看完整", "觀看完整", "观看完毕",
        "秒后获取", "秒後獲取", "秒后领取", "秒後領取",
        "后获取奖励", "后领取奖励", "看广告领", "看视频领",
        "观看广告得", "观看得奖励", "看广告得奖励",
        "后可领奖", "后可獲得", "后可获得", "后可领取", "后可領取",
        "可立即领奖", "立即领奖", "即可領獎", "即可领奖", "提前领奖",
        "免看此广告", "免看廣告",
    )

    /**
     * The "暂未获得奖励 / 继续观看" sheet: the ad was closed before its reward
     * window ended. 继续观看 restores the countdown, so it must beat every
     * close heuristic (see [findRewardMissResume]).
     */
    private val rewardMissWords = listOf(
        "暂未获得奖励", "暫未獲得獎勵", "未获得奖励", "未獲得獎勵",
        "暂无奖励", "暫無獎勵", "奖励未到账", "獎勵未到賬", "奖励未发放",
        "未完成观看", "觀看未完成", "观看未完成", "观看时长不足",
        "任务未完成", "任務未完成", "未达领奖条件",
    )

    /** Words that identify a "you are about to lose the reward" dialog. */
    private val rewardExitPattern = Regex(
        """(放弃|放棄|失去|无法获得|無法獲得|不能获得|拿不到|无法领取|無法領取|将无法|將無法|确定要?关闭|確認關閉|确认要?退出|退出广告)""",
    )

    private val rewardWords = listOf("奖励", "獎勵", "道具", "金币", "金幣", "红包", "紅包", "礼包")

    /** Call-to-action labels shown on the end card of a finished ad. */
    private val ctaWords = listOf(
        "查看详情", "立即下载", "立即安装", "马上安装", "了解更多",
        "立即购买", "点击查看", "去逛逛", "打开应用", "一键下载",
        "去看看", "去下载", "立即体验", "免费下载", "一键安装", "下载安装",
    )

    /** Phrases only used by "leave the app and browse to shorten the ad" sheets. */
    private val forcedBrowseWords = listOf(
        "减广告时长", "缩短广告", "提前领奖", "提前领取", "提前获得",
        "去浏览", "浏览10秒", "强制浏览", "浏览领奖", "浏览就领",
    )

    private val exitConfirmWords = setOf(
        "确定", "确认", "退出", "确定退出", "确认退出", "狠心退出", "狠心离开",
        "残忍离开", "放弃奖励", "放弃", "直接退出",
    )

    /** Safe ways to dismiss an interrupt popup without losing the reward. */
    private val interruptCloseWords = setOf(
        "关闭", "關閉", "×", "✕", "╳", "✖", "close",
        "稍后", "稍後", "以后再说", "以後再說", "我知道了", "知道了",
        "取消", "暂不", "暫不", "不感兴趣", "不感興趣", "下次再说",
    )

    private val pureNumberPattern = Regex("""^(\d{1,2})$""")
    private val numberWithUnitPattern = Regex("""^(\d{1,2})(s|秒|s秒)$""")
    private val skipPhrasePattern = Regex("""跳过|跳過|skip""", RegexOption.IGNORE_CASE)

    private val viewIdPattern = Regex(
        """(skip|skip_?ad|skip_?button|ad_?skip|splash_?skip|close_?ad|ad_?close|gdt_|tt_?ad|ksad_|pangle_|mintegral_|sigmob_|bytedance_?ad|unified_?ad|interstitial_?ad)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Resource-id prefixes owned by ad SDKs. They appear on every screen the
     * SDK renders (including the end card), so they are a reliable "this is an
     * ad window" signal even when no "跳过 / 广告" text is present.
     */
    private val sdkIdPattern = Regex(
        """(anythink|myoffer|ksad_|gdt_|pangle|mintegral|sigmob|bytedance_?ad|unified_?ad|topon|tanx_|beizi|mobads|bidding_?ad)""",
        RegexOption.IGNORE_CASE,
    )

    private val closeIdPattern = Regex(
        """(close_?ad|ad_?close|close_?btn|close_?button|close_?icon|btn_?close|iv_?close|img_?close|dismiss|cancel_?ad|interstitial_?close|countdown_?close|_close_?id)""",
        RegexOption.IGNORE_CASE,
    )

    private val volumeIdPattern = Regex(
        """(voice|volume|sound_?switch|voice_?switch|sound_?icon|mute_?icon|audio_?icon|speaker)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "关闭此条广告" only. "不感兴趣" used to be part of this pattern, but it is
     * also the label of every feed app's own overflow menu (Bilibili video
     * cards, for one), so it closed ordinary content instead of an ad.
     */
    private val closeThisAdPattern = Regex(
        """关闭(此|该|这)条?广告|屏蔽(此|该)广告|关闭广告""",
    )

    private val resumeWords = listOf("继续观看", "继续播放", "继续欣赏", "继续看视频", "继续观看视频")

    private val volumeWords = listOf("音量", "声音", "静音", "volume", "sound", "mute", "扩音")

    /**
     * Mute button on video ads. Two strategies:
     *  1. explicit text / description / view id,
     *  2. inside a detected ad window, a small text-less icon in a bottom
     *     corner (how most domestic SDKs draw the speaker).
     */
    fun findVolumeButton(
        nodes: List<NodeSnapshot>,
        screenWidth: Int,
        screenHeight: Int,
        strongContext: Boolean,
    ): SkipCandidate? {
        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text + " " + node.contentDescription)
            val viaId = volumeIdPattern.containsMatchIn(node.viewId)
            if (text.isEmpty() && !viaId) continue
            if (!viaId && volumeWords.none { text.contains(it) }) continue
            if (node.bounds.width() > 220 || node.bounds.height() > 220) continue
            if (node.bounds.width() < MIN_NODE_SIZE_PX) continue
            val clickable = node.clickable || ancestorClickable(nodes, node)
            if (!clickable) continue
            return SkipCandidate(node.index, 60, "音量图标", "静音")
        }

        if (!strongContext || screenWidth <= 0 || screenHeight <= 0) return null

        // Icon-only speaker drawn in a corner of a full-screen video ad.
        // Domestic SDKs place it flush against the edge (top-right, bottom-left
        // or bottom-right) as a bare ImageView/TextView with no description.
        for (node in nodes) {
            if (!node.enabled) continue
            if (node.text.isNotBlank() || node.contentDescription.isNotBlank()) continue
            if (!node.className.contains("Image") && !node.className.contains("TextView")) continue
            val bounds = node.bounds
            val w = bounds.width()
            val h = bounds.height()
            if (w < screenWidth * 0.03f || w > screenWidth * 0.10f) continue
            if (h < screenHeight * 0.012f || h > screenHeight * 0.05f) continue
            val nearSide = bounds.right > screenWidth * 0.90f || bounds.left < screenWidth * 0.10f
            if (!nearSide) continue
            val topOrBottom = bounds.top < screenHeight * 0.22f || bounds.top > screenHeight * 0.78f
            if (!topOrBottom) continue
            val clickable = node.clickable || ancestorClickable(nodes, node)
            if (!clickable) continue
            return SkipCandidate(node.index, 40, "音量图标·角标", "静音")
        }
        return null
    }

    /** A small "广告" label is present (feed ads such as Douyin). */
    fun hasAdLabel(nodes: List<NodeSnapshot>): Boolean =
        nodes.any { node ->
            val text = normalize(node.text)
            (text == "广告" || text == "廣告") &&
                node.bounds.width() in 1..220 &&
                node.bounds.width() >= MIN_NODE_SIZE_PX
        }

    /**
     * The "要放弃领取奖励吗？" confirmation shown after an accidental skip,
     * or a "关闭广告将无法获得奖励" sheet. Tapping 继续观看 cancels the exit.
     */
    fun findRewardExit(nodes: List<NodeSnapshot>): RewardExitPrompt? {
        var message: String? = null
        for (node in nodes) {
            val text = node.text.trim()
            if (text.isEmpty() || text.length > 60) continue
            if (rewardWords.none { text.contains(it) }) continue
            if (!rewardExitPattern.containsMatchIn(text)) continue
            message = text
            break
        }
        if (message == null) return null

        var resume: SkipCandidate? = null
        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text.isEmpty()) continue
            val matched = resumeWords.any { text.contains(it) } ||
                (text.length <= 8 && (text == "取消" || text == "暫不" || text == "暂不"))
            if (!matched) continue
            val clickable = node.clickable || ancestorClickable(nodes, node)
            if (!clickable) continue
            resume = SkipCandidate(node.index, 100, "继续观看·撤回", node.text.trim().take(24))
            break
        }
        return RewardExitPrompt(resume, message.take(40))
    }

    /**
     * "暂未获得奖励 / 继续观看": the ad was closed before its reward window
     * ended, and the SDK offers a way back. Tapping 继续观看 restores the
     * countdown, so this must run before any close-icon heuristic.
     */
    fun findRewardMissResume(nodes: List<NodeSnapshot>): SkipCandidate? {
        val miss = nodes.any { node ->
            val text = normalize(node.text)
            text.length in 1..40 && rewardMissWords.any { text.contains(it) }
        }
        if (!miss) return null

        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text.isEmpty() || text.length > 12) continue
            if (resumeWords.none { text.contains(it) }) continue
            if (node.bounds.width() <= 0 || node.bounds.height() <= 0) continue
            return SkipCandidate(node.index, 110, "继续观看·恢复奖励", node.text.trim().take(24))
        }
        return null
    }

    /**
     * A popup that interrupts a rewarded video (download prompt, "打开应用",
     * survey sheet...). Closing it lets the video finish so the reward still
     * lands. Only explicit close affordances are used — "跳过" is never tapped
     * here and "关闭广告" is rejected, because both forfeit the reward.
     *
     * The caller must only use this inside a rewarded-ad context.
     *
     * A close affordance pinned to a screen corner is NOT a popup close
     * button: it is the ad's own "关闭" (top-right on most SDKs). Tapping it
     * ended the rewarded video and produced the "暂未获得奖励" loop.
     */
    fun findInterruptPopup(
        nodes: List<NodeSnapshot>,
        screenWidth: Int,
        screenHeight: Int,
    ): SkipCandidate? {
        for (node in nodes) {
            if (!node.enabled) continue
            val raw = node.text.trim()
            val description = normalize(node.contentDescription)
            val text = normalize(raw)
            if (text.isEmpty() && description.isEmpty()) continue
            if (text.length > 16) continue
            if (text.contains("广告") || text.contains("廣告")) continue
            if (text.contains("跳过") || text.contains("跳過")) continue

            val isCloseText = text in interruptCloseWords
            val isCloseDesc = description.isNotEmpty() &&
                (description.contains("关闭") || description.contains("關閉") || description.contains("close")) &&
                !description.contains("广告") && !description.contains("廣告")
            if (!isCloseText && !isCloseDesc) continue

            val bounds = node.bounds
            if (bounds.width() < MIN_NODE_SIZE_PX || bounds.height() < MIN_NODE_SIZE_PX) continue
            if (bounds.width() > 720 || bounds.height() > 480) continue
            if (isScreenCorner(bounds, screenWidth, screenHeight)) continue
            val clickable = node.clickable || ancestorClickable(nodes, node)
            if (!clickable) continue
            return SkipCandidate(node.index, 80, "奖励弹窗·关闭", raw.ifEmpty { "关闭" }.take(24))
        }
        return null
    }

    /**
     * Two-step feed-ad flow (e.g. QQ Zone): first tap the small "广告" label,
     * then the sheet offers "关闭此条广告". This finds the second-step entry.
     */
    fun findCloseThisAd(nodes: List<NodeSnapshot>): SkipCandidate? {
        // A feed-ad sheet only exists on top of an ad surface. Without this
        // guard, every app's own "不感兴趣 / 关闭" menu would match.
        if (!hasAdLabel(nodes) && !hasStrongAdContext(nodes)) return null
        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text.isEmpty()) continue
            if (!closeThisAdPattern.containsMatchIn(text)) continue
            val clickable = node.clickable || ancestorClickable(nodes, node)
            if (!clickable) continue
            return SkipCandidate(node.index, 100, "关闭此条广告", node.text.trim().take(24))
        }
        return null
    }

    /**
     * The small "广告" pill on a feed ad. Only returned when it is actually
     * clickable and small, so plain ad markers are never tapped.
     */
    fun findAdLabel(
        nodes: List<NodeSnapshot>,
        screenWidth: Int,
        screenHeight: Int,
    ): SkipCandidate? {
        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text != "广告" && text != "廣告" && text != "广告∨" && text != "广告v") continue
            if (node.bounds.width() > 220 || node.bounds.height() > 120) continue
            if (node.bounds.width() < MIN_NODE_SIZE_PX || node.bounds.height() < MIN_NODE_SIZE_PX) continue
            if (node.bounds.top < screenHeight * 0.05f) continue
            if (node.bounds.left >= screenWidth) continue
            val clickable = node.clickable || ancestorClickable(nodes, node)
            if (!clickable) continue
            return SkipCandidate(node.index, 55, "广告标签", "广告")
        }
        return null
    }

    fun detect(
        nodes: List<NodeSnapshot>,
        screenWidth: Int,
        screenHeight: Int,
        config: DetectorConfig,
        suppressCloseAd: Boolean = false,
    ): SkipCandidate? {
        if (nodes.isEmpty() || screenWidth <= 0 || screenHeight <= 0) return null

        val strongContext = hasStrongAdContext(nodes)

        var best: SkipCandidate? = null

        for (node in nodes) {
            if (!node.enabled) continue
            if (node.bounds.right <= 0 || node.bounds.bottom <= 0) continue
            if (node.bounds.left >= screenWidth || node.bounds.top >= screenHeight) continue

            val text = node.text
            val description = node.contentDescription

            // Never treat text fields as skip buttons.
            if (node.className.contains("EditText")) continue

            val strongText = text.isNotBlank() && (
                normalize(text) in skipWords ||
                    (normalize(text).length <= 8 && skipPhrasePattern.containsMatchIn(normalize(text))) ||
                    normalize(description) in skipWords
                )
            // Inside an ad window, some SDKs mark the skip button as
            // not-visible-to-user even though it is drawn; do not drop it.
            if (!node.visible && !strongText && !strongContext) continue

            val textHit = if (text.isNotBlank()) {
                scoreText(text, node, screenWidth, screenHeight, config, strongContext, nodes, suppressCloseAd)
            } else {
                null
            }

            val descriptionHit = if (config.matchContentDescription && description.isNotBlank() && textHit == null) {
                scoreText(
                    description, node, screenWidth, screenHeight, config,
                    strongContext, nodes, suppressCloseAd,
                )?.let { Hit(it.score - 4, it.rule + "·描述", it.label) }
            } else {
                null
            }

            val idHit = if (config.matchViewId && node.viewId.isNotEmpty() && viewIdPattern.containsMatchIn(node.viewId)) {
                Hit(96, "控件 ID", displayLabel(node))
            } else {
                null
            }

            val closeHit = if (
                config.matchCloseIcon &&
                !suppressCloseAd &&
                strongContext &&
                text.isBlank() &&
                description.isBlank()
            ) {
                closeIconHit(node, screenWidth, screenHeight, nodes)
            } else {
                null
            }

            // A full-width, non-clickable label is almost never a skip button.
            val wideText = node.bounds.width() > screenWidth * 0.6f &&
                !node.clickable &&
                !ancestorClickable(nodes, node)
            if (wideText && closeHit == null && (textHit?.score ?: 0) < 100) continue

            val hit = listOfNotNull(textHit, descriptionHit, idHit, closeHit).maxByOrNull { it.score } ?: continue
            val label = hit.label.ifBlank { displayLabel(node) }
            val candidate = SkipCandidate(node.index, hit.score, hit.rule, label)
            if (candidate.score > (best?.score ?: -1)) best = candidate
        }
        return best
    }

    fun rewardGranted(nodes: List<NodeSnapshot>): Boolean =
        nodes.any { containsRewardGranted(it) }

    /**
     * A rewarded video is playing: there is a reward promise and the window
     * really looks like an ad (or shows a countdown). Plain pages that merely
     * mention "看广告得时长" no longer qualify.
     */
    fun rewardPromise(nodes: List<NodeSnapshot>): Boolean {
        val mentioned = nodes.any { node ->
            val text = node.text + " " + node.contentDescription
            if (text.length > 120) return@any false
            rewardPromiseWords.any { text.contains(it, ignoreCase = true) }
        }
        if (!mentioned) return false
        return hasStrongAdContext(nodes) || hasCountdownNode(nodes)
    }

    /** Close a small in-app popup ad. */
    fun detectPopupClose(
        nodes: List<NodeSnapshot>,
        container: Rect,
        config: DetectorConfig,
    ): SkipCandidate? {
        if (nodes.isEmpty() || container.width() <= 0 || container.height() <= 0) return null

        val hasAdLabel = nodes.any { node ->
            val text = normalize(node.text)
            (text.startsWith("广告") || text.startsWith("廣告")) &&
                node.bounds.width() in 1..AD_LABEL_MAX_WIDTH_PX
        }
        val hasSdkId = nodes.any { node -> viewIdPattern.containsMatchIn(node.viewId) }
        if (!hasAdLabel && !hasSdkId) return null

        var best: SkipCandidate? = null
        for (node in nodes) {
            if (!node.enabled) continue
            if (node.bounds.width() < MIN_NODE_SIZE_PX || node.bounds.height() < MIN_NODE_SIZE_PX) continue

            val text = normalize(node.text)
            val description = normalize(node.contentDescription)
            val id = node.viewId.lowercase()
            val clickable = node.clickable || ancestorClickable(nodes, node)

            val hit = when {
                text in setOf("×", "✕", "╳", "✖", "关闭", "關閉", "close") ->
                    Hit(90, "弹窗关闭", "关闭弹窗")
                description.contains("关闭") || description.contains("關閉") ||
                    description.contains("close") -> Hit(88, "弹窗关闭·描述", "关闭弹窗")
                id.contains("close") || id.contains("dismiss") || id.contains("cancel") ->
                    Hit(86, "弹窗关闭 ID", "关闭弹窗")
                text.isBlank() && description.isBlank() && clickable ->
                    closeIconInContainer(node, container, config)
                else -> null
            } ?: continue

            if (!clickable) continue
            val candidate = SkipCandidate(node.index, hit.score, hit.rule, hit.label)
            if (candidate.score > (best?.score ?: -1)) best = candidate
        }
        return best
    }

    /** Text-only classification used by the in-app rule tester. */
    fun classifyText(raw: String): String? {
        val text = normalize(raw)
        if (text.isEmpty()) return null
        if (text in skipWords) return "跳过关键词"
        if (text.length <= 8 && skipPhrasePattern.containsMatchIn(text)) return "跳过文案"
        if (text in closeWords) return "关闭文案"
        if (pureNumberPattern.matchEntire(text) != null) return "倒计时数字"
        if (numberWithUnitPattern.matchEntire(text) != null) return "倒计时数字"
        if (rewardGrantedWords.any { raw.contains(it) }) return "奖励已发放"
        if (rewardPromiseWords.any { raw.contains(it) }) return "奖励承诺"
        return null
    }

    /**
     * A window looks like an ad when it contains a real skip label, an ad-SDK
     * view id, or a small "广告" corner label. A plain app screen matches none.
     */
    fun hasStrongAdContext(nodes: List<NodeSnapshot>): Boolean {
        if (hasSkipLabel(nodes)) return true

        val hasSdkId = nodes.any { node ->
            viewIdPattern.containsMatchIn(node.viewId) || sdkIdPattern.containsMatchIn(node.viewId)
        }
        if (hasSdkId) return true

        return nodes.any { node ->
            val text = normalize(node.text)
            (text.contains("广告") || text.contains("廣告")) &&
                node.bounds.width() in 1..AD_LABEL_MAX_WIDTH_PX
        }
    }

    /**
     * A forced-browse sheet on top of a "rewarded" video: it demands that the
     * user leave the app and browse a shop for N seconds instead of watching
     * the video, and it provides no close button. Such ads are abandoned
     * outright (back out of the ad) rather than played along with.
     */
    fun isForcedBrowseAd(nodes: List<NodeSnapshot>): Boolean {
        val hit = nodes.any { node ->
            val text = normalize(node.text)
            text.length in 1..40 && forcedBrowseWords.any { text.contains(it) }
        }
        if (!hit) return false
        // The sheet is usually a WebView layer, so SDK view ids and the small
        // "广告" pill are often missing from the node tree. Reward wording on
        // the same screen is enough context.
        if (hasStrongAdContext(nodes)) return true
        return nodes.any { node ->
            val text = normalize(node.text)
            text.length <= 60 && (
                text.contains("广告") || text.contains("廣告") ||
                    text.contains("奖励") || text.contains("獎勵") ||
                    text.contains("领奖") || text.contains("領獎")
                )
        }
    }

    /**
     * "是否放弃提前领奖？" — the confirmation shown after tapping 继续看视频 on a
     * "complete the task to claim early" sheet. Choosing 确认放弃 closes the
     * sheet and playback resumes, so it is the correct answer here.
     */
    fun findEarlyRewardAbandon(nodes: List<NodeSnapshot>): SkipCandidate? {
        val hasPrompt = nodes.any { node ->
            val text = normalize(node.text)
            text.length <= 30 && text.contains("放弃") &&
                (text.contains("提前领奖") || text.contains("提前领取") ||
                    text.contains("提前奖励") || text.contains("提前获得"))
        }
        if (!hasPrompt) return null

        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text.isEmpty() || text.length > 8) continue
            val matched = text.contains("确认放弃") || text.contains("确定放弃") ||
                text.contains("确认离开") || (text == "放弃" && nodes.size < 60)
            if (!matched) continue
            if (node.bounds.width() <= 0 || node.bounds.height() <= 0) continue
            return SkipCandidate(node.index, 100, "确认放弃提前领奖", node.text.trim().take(24))
        }
        return null
    }

    /** Any visible "跳过" control (used to abandon an ad outright). */
    fun findSkipButton(nodes: List<NodeSnapshot>): SkipCandidate? {
        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text.isEmpty() || text.length > 12) continue
            if (!skipPhrasePattern.containsMatchIn(text)) continue
            if (node.bounds.width() <= 0 || node.bounds.height() <= 0) continue
            return SkipCandidate(node.index, 100, "跳过", node.text.trim().take(24))
        }
        return null
    }

    /**
     * "继续看视频" on the promo sheet of a rewarded video: tapping it closes the
     * sheet and playback continues, so the reward still lands. WebView links do
     * not always report clickable, so the coordinate tap is always attempted.
     */
    fun findContinueWatching(
        nodes: List<NodeSnapshot>,
        screenHeight: Int = Int.MAX_VALUE,
    ): SkipCandidate? {
        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text.isEmpty() || text.length > 12) continue
            if (resumeWords.none { text.contains(it) }) continue
            val bounds = node.bounds
            if (bounds.width() <= 0 || bounds.height() <= 0) continue
            // Off-screen leftovers of a closed sheet must not be tapped: the
            // coordinate tap would land on whatever is drawn at that spot.
            if (bounds.top >= screenHeight || bounds.bottom <= 0) continue
            return SkipCandidate(node.index, 90, "继续观看", node.text.trim().take(24))
        }
        return null
    }

    /** "确定退出 / 狠心离开" button of the exit confirmation dialog. */
    fun findExitConfirm(nodes: List<NodeSnapshot>): SkipCandidate? {
        for (node in nodes) {
            if (!node.enabled) continue
            val text = normalize(node.text)
            if (text.isEmpty() || text.length > 8) continue
            val matched = text in exitConfirmWords ||
                text.contains("退出") || text.contains("放弃") ||
                text.contains("离开") || text.contains("不看了")
            if (!matched) continue
            val clickable = node.clickable || ancestorClickable(nodes, node)
            if (!clickable) continue
            return SkipCandidate(node.index, 95, "退出确认", node.text.trim().take(24))
        }
        return null
    }

    /**
     * The rewarded video already reached its end card: a call-to-action is
     * visible while the countdown and the "跳过" label are gone. Closing the ad
     * at this point no longer forfeits the reward.
     */
    fun rewardFinished(nodes: List<NodeSnapshot>): Boolean {
        if (!hasCta(nodes)) return false
        if (hasCountdownNode(nodes)) return false
        if (hasSkipLabel(nodes)) return false
        return true
    }

    /**
     * A video ad that cannot be skipped yet (rewarded video still playing):
     * a countdown plus a call-to-action, but no "跳过" label. Tapping anything
     * here risks closing the ad and losing the reward, so the service only
     * mutes and closes unrelated interrupt popups.
     */
    fun isUnskippableVideo(nodes: List<NodeSnapshot>): Boolean =
        hasCountdownNode(nodes) && hasCta(nodes) && !hasSkipLabel(nodes)

    private fun hasCta(nodes: List<NodeSnapshot>): Boolean =
        nodes.any { node ->
            val text = normalize(node.text)
            if (text.length > 12) return@any false
            ctaWords.any { text == it || text.startsWith(it) }
        }

    private fun hasSkipLabel(nodes: List<NodeSnapshot>): Boolean =
        nodes.any { node ->
            val text = normalize(node.text)
            val description = normalize(node.contentDescription)
            isSkipLike(text) || isSkipLike(description)
        }

    private data class Hit(val score: Int, val rule: String, val label: String)

    /**
     * Splash / interstitial / rewarded windows are simple: no text input and
     * at most two scrolling containers. Chat and feed screens carry an
     * EditText and several lists, so their corner icons are never mistaken
     * for an ad close button.
     */
    private fun isAdLikeWindow(nodes: List<NodeSnapshot>): Boolean {
        var containers = 0
        for (node in nodes) {
            val className = node.className
            if (className.contains("EditText")) return false
            if (className.contains("RecyclerView") || className.contains("ListView") ||
                className.contains("ScrollView") || className.contains("WebView")
            ) {
                containers++
                if (containers > 2) return false
            }
        }
        return true
    }

    private fun hasCountdownNode(nodes: List<NodeSnapshot>): Boolean =        nodes.any { node ->
            val text = normalize(node.text)
            if (!node.visible) return@any false
            if (pureNumberPattern.matchEntire(text) == null &&
                numberWithUnitPattern.matchEntire(text) == null
            ) {
                return@any false
            }
            node.bounds.width() <= 200 && node.bounds.height() <= 200
        }

    private fun scoreText(
        raw: String,
        node: NodeSnapshot,
        screenWidth: Int,
        screenHeight: Int,
        config: DetectorConfig,
        strongContext: Boolean,
        nodes: List<NodeSnapshot>,
        suppressCloseAd: Boolean,
    ): Hit? {
        val text = normalize(raw)
        if (text.isEmpty()) return null

        // While a reward is pending, "关闭广告" forfeits it: never tap it.
        if (suppressCloseAd) {
            if (closeAdWords.any { text.contains(it) }) return null
            if (text in closeWords) return null
        }

        if (text in skipWords) {
            if (!skipTextAllowed(node, screenWidth, screenHeight, strongContext, config, nodes)) return null
            return Hit(100, "跳过关键词", raw.trim().take(24))
        }

        if (text.length <= 8 && skipPhrasePattern.containsMatchIn(text)) {
            if (!skipTextAllowed(node, screenWidth, screenHeight, strongContext, config, nodes)) return null
            return Hit(95, "跳过文案", raw.trim().take(24))
        }

        // "关闭" / "×" only makes sense on a window that already looks like an ad.
        if (text in closeWords) {
            if (!strongContext) return null
            val corner = isCorner(node, screenWidth, screenHeight) ||
                isBottomRightish(node.bounds, screenWidth, screenHeight)
            if (corner || config.aggressive) {
                return Hit(74, "关闭按钮", raw.trim().take(24))
            }
            return null
        }

        // Bare numerals are only trusted inside an ad window. Splash ads often
        // render the countdown as a non-clickable TextView, so fall back to a
        // coordinate tap instead of waiting for the clickable "跳过" button.
        if (config.matchCountdown && strongContext) {
            val number = pureNumberPattern.matchEntire(text) ?: numberWithUnitPattern.matchEntire(text)
            if (number != null && isTopEnd(node, screenWidth, screenHeight)) {
                val clickable = node.clickable || ancestorClickable(nodes, node)
                return if (clickable) {
                    Hit(90, "倒计时", raw.trim().take(24))
                } else {
                    Hit(82, "倒计时·坐标", raw.trim().take(24))
                }
            }
        }

        return null
    }

    /**
     * A skip label only counts when it is a real, compact control.
     * WebView-rendered links often report `clickable=false` even though they are
     * perfectly tappable, so a small label is allowed to fall back to a
     * coordinate tap instead of being rejected.
     */
    private fun skipTextAllowed(
        node: NodeSnapshot,
        screenWidth: Int,
        screenHeight: Int,
        strongContext: Boolean,
        config: DetectorConfig,
        nodes: List<NodeSnapshot>,
    ): Boolean {
        val clickable = node.clickable || ancestorClickable(nodes, node)
        val compact = node.bounds.width() < screenWidth * 0.32f &&
            node.bounds.height() < screenHeight * 0.09f
        if (!clickable && !compact && !config.aggressive) return false
        if (node.bounds.width() > screenWidth * 0.5f) return false
        if (node.bounds.height() > screenHeight * 0.25f) return false
        if (node.bounds.top > screenHeight * 0.94f) return false
        if (!strongContext && !config.aggressive && node.bounds.width() > screenWidth * 0.42f) return false
        return true
    }

    private fun closeIconHit(
        node: NodeSnapshot,
        screenWidth: Int,
        screenHeight: Int,
        nodes: List<NodeSnapshot>,
    ): Hit? {
        val bounds = node.bounds
        if (bounds.width() < MIN_NODE_SIZE_PX || bounds.height() < MIN_NODE_SIZE_PX) return null
        if (bounds.width() > MAX_ICON_SIZE_PX || bounds.height() > MAX_ICON_SIZE_PX) return null

        val clickable = node.clickable || ancestorClickable(nodes, node)
        if (!clickable) return null

        // Chat / feed screens also have cute little corner icons. Only a plain
        // ad window (no text input, at most two scrolling containers) may have
        // its corner icons treated as a close button.
        if (!isAdLikeWindow(nodes)) return null

        if (closeIdPattern.containsMatchIn(node.viewId)) {
            // Top-left is the SDK's "back / close content" affordance. On a
            // rewarded video tapping it forfeits the reward, so never use it.
            if (bounds.top < screenHeight * 0.30f && bounds.left < screenWidth * 0.30f) return null
            return Hit(92, "关闭图标 ID", "关闭按钮")
        }

        // Icon-only "×" in the top-right corner: the standard close button of
        // splash ads and of the end card shown after a rewarded video. A
        // labelled "跳过" / countdown is handled earlier and is never text-less.
        if (bounds.right > screenWidth * 0.78f &&
            bounds.top < screenHeight * 0.16f &&
            bounds.width() < screenWidth * 0.12f &&
            bounds.height() < screenHeight * 0.06f
        ) {
            return Hit(60, "右上角关闭图标", "关闭按钮")
        }

        // Bottom-right of an interstitial card. Top-right is deliberately NOT
        // considered: that area is shared with "消息 / 搜索 / 更多" icons.
        if (isBottomRightish(bounds, screenWidth, screenHeight)) {
            return Hit(66, "右下角关闭图标", "关闭按钮")
        }

        return null
    }

    private fun isSkipLike(text: String): Boolean =
        text.isNotEmpty() &&
            (text in skipWords || (text.length <= 8 && skipPhrasePattern.containsMatchIn(text)))

    private fun closeIconInContainer(
        node: NodeSnapshot,
        container: Rect,
        config: DetectorConfig,
    ): Hit? {
        val bounds = node.bounds
        if (bounds.width() > container.width() * 0.18f) return null
        if (bounds.height() > container.height() * 0.18f) return null
        val nearRight = bounds.right > container.right - container.width() * 0.22f
        val nearTop = bounds.top < container.top + container.height() * 0.28f
        val nearBottom = bounds.bottom > container.bottom - container.height() * 0.28f
        if (!nearRight) return null
        if (!nearTop && !nearBottom) return null
        return Hit(if (config.aggressive) 72 else 64, "弹窗角标关闭", "关闭弹窗")
    }

    private fun isTopEnd(node: NodeSnapshot, screenWidth: Int, screenHeight: Int): Boolean {
        val bounds = node.bounds
        return bounds.right > screenWidth * 0.55f &&
            bounds.top < screenHeight * 0.42f &&
            bounds.width() < screenWidth * 0.42f
    }

    /**
     * Right-side corner of an ad window. The left corner is deliberately
     * excluded: browsers put their shield / back / menu controls there, and a
     * "关闭" label found on the left is almost never an ad close button.
     */
    private fun isCorner(node: NodeSnapshot, screenWidth: Int, screenHeight: Int): Boolean {
        val bounds = node.bounds
        if (bounds.width() > screenWidth * 0.3f || bounds.height() > screenHeight * 0.18f) return false
        val horizontal = bounds.right > screenWidth * 0.72f
        val vertical = bounds.top < screenHeight * 0.3f || bounds.bottom > screenHeight * 0.7f
        return horizontal && vertical
    }

    /**
     * A control glued to a screen edge (the ad's own close button). Popups
     * that merely interrupt a video place their × inside the sheet, so an
     * edge-pinned "关闭" must never be treated as a popup close.
     */
    private fun isScreenCorner(bounds: Rect, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val nearEdgeX = bounds.right > screenWidth * 0.84f || bounds.left < screenWidth * 0.16f
        val nearEdgeY = bounds.top < screenHeight * 0.13f || bounds.bottom > screenHeight * 0.87f
        return nearEdgeX && nearEdgeY
    }

    /** Bottom-right quadrant of an interstitial/splash ad card (not necessarily the screen edge). */
    private fun isBottomRightish(bounds: Rect, screenWidth: Int, screenHeight: Int): Boolean {
        return bounds.right > screenWidth * 0.45f &&
            bounds.bottom > screenHeight * 0.72f &&
            bounds.width() < screenWidth * 0.18f &&
            bounds.height() < screenHeight * 0.12f
    }

    private fun ancestorClickable(nodes: List<NodeSnapshot>, node: NodeSnapshot): Boolean {
        var parentIndex = node.parentIndex
        var depth = 0
        while (parentIndex >= 0 && depth < 5) {
            val parent = nodes.getOrNull(parentIndex) ?: return false
            if (parent.clickable) return true
            parentIndex = parent.parentIndex
            depth++
        }
        return false
    }

    private fun containsRewardGranted(node: NodeSnapshot): Boolean {
        val text = node.text + " " + node.contentDescription
        return rewardGrantedWords.any { text.contains(it, ignoreCase = true) }
    }

    private fun normalize(value: String): String =
        value.replace(Regex("""\s+"""), "").trim().lowercase()

    private fun displayLabel(node: NodeSnapshot): String {
        val text = node.text.trim()
        if (text.isNotEmpty()) return text.take(24)
        val description = node.contentDescription.trim()
        if (description.isNotEmpty()) return description.take(24)
        val id = node.viewId.substringAfterLast('/')
        if (id.isNotEmpty()) return id.take(24)
        return "未命名控件"
    }
}
