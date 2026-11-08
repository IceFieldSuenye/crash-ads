package hey.skipad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hey.skipad.data.AppSettings
import hey.skipad.data.RewardMode
import hey.skipad.service.AdDetector
import hey.skipad.ui.components.SectionCard
import hey.skipad.ui.components.SwitchRow
import hey.skipad.ui.theme.MintGreen
import hey.skipad.ui.theme.SunsetOrange

@Composable
fun RulesScreen(
    settings: AppSettings,
    onToggleCountdown: (Boolean) -> Unit,
    onToggleViewId: (Boolean) -> Unit,
    onToggleContentDesc: (Boolean) -> Unit,
    onToggleGesture: (Boolean) -> Unit,
    onToggleAggressive: (Boolean) -> Unit,
    onToggleToast: (Boolean) -> Unit,
    onToggleClosePopups: (Boolean) -> Unit,
    onToggleNotify: (Boolean) -> Unit,
    onToggleOcr: (Boolean) -> Unit,
    onToggleTapAdLabel: (Boolean) -> Unit,
    onToggleRunningNotification: (Boolean) -> Unit,
    onToggleAutoMute: (Boolean) -> Unit,
    onToggleAutoSwipe: (Boolean) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onAddBlacklist: (String) -> Unit,
    onRemoveBlacklist: (String) -> Unit,
    onRewardModeChange: (RewardMode) -> Unit,
    overlayGranted: Boolean,
    onRequestOverlay: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("规则", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "识别策略与点击行为",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard {
                Text("识别规则", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = "倒计时识别",
                    description = "识别右上角的「5s / 5 秒 / 数字」倒计时并点击",
                    checked = settings.matchCountdown,
                    onCheckedChange = onToggleCountdown,
                )
                SwitchRow(
                    title = "控件 ID 识别",
                    description = "通过 skip_button、ad_close 等资源 ID 定位按钮",
                    checked = settings.matchViewId,
                    onCheckedChange = onToggleViewId,
                )
                SwitchRow(
                    title = "描述文本识别",
                    description = "匹配控件 contentDescription 中的跳过类描述",
                    checked = settings.matchContentDescription,
                    onCheckedChange = onToggleContentDesc,
                )
                SwitchRow(
                    title = "手势兜底",
                    description = "节点不可点击时，按坐标模拟点按",
                    checked = settings.gestureFallback,
                    onCheckedChange = onToggleGesture,
                )
                SwitchRow(
                    title = "小弹窗广告",
                    description = "关闭界面内带「广告」标识的小弹窗 / 悬浮广告",
                    checked = settings.closePopups,
                    onCheckedChange = onToggleClosePopups,
                )
                SwitchRow(
                    title = "图片识别（OCR）",
                    description = "Android 11+：截取右上角区域识别「跳过」图片文字，离线运行",
                    checked = settings.ocrEnabled,
                    onCheckedChange = onToggleOcr,
                )
                SwitchRow(
                    title = "信息流广告",
                    description = "先点「广告」小字，再点「关闭此条广告」（如 QQ 空间）",
                    checked = settings.tapAdLabel,
                    onCheckedChange = onToggleTapAdLabel,
                )
                SwitchRow(
                    title = "视频自动静音",
                    description = "识别广告里的音量图标并点击静音",
                    checked = settings.autoMuteVolume,
                    onCheckedChange = onToggleAutoMute,
                )
                SwitchRow(
                    title = "信息流自动上滑",
                    description = "抖音等平台刷到广告视频时自动滑走",
                    checked = settings.autoSwipeFeed,
                    onCheckedChange = onToggleAutoSwipe,
                )
            }
        }

        item {
            SectionCard {
                Text("点击行为", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                CooldownRow(settings.cooldownMs, onCooldownChange)
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = "激进模式",
                    description = "放宽「关闭 / ×」类文案的判定，命中率更高但可能误触",
                    checked = settings.aggressive,
                    onCheckedChange = onToggleAggressive,
                )
                SwitchRow(
                    title = "点击提示",
                    description = "每次成功跳过后轻提示一次",
                    checked = settings.showToast,
                    onCheckedChange = onToggleToast,
                )
                SwitchRow(
                    title = "跳过通知",
                    description = "在通知栏静默提示（不弹横幅、不响铃）；关闭后不再发送通知",
                    checked = settings.notifySkip,
                    onCheckedChange = onToggleNotify,
                )
                SwitchRow(
                    title = "运行通知",
                    description = "通知栏常显「服务运行中」提示，可随时关闭",
                    checked = settings.showRunningNotification,
                    onCheckedChange = onToggleRunningNotification,
                )
            }
        }

        item { BlacklistCard(settings, onAddBlacklist, onRemoveBlacklist) }
        item {
            RewardCard(
                mode = settings.rewardMode,
                overlayGranted = overlayGranted,
                onModeChange = onRewardModeChange,
                onRequestOverlay = onRequestOverlay,
            )
        }
        item { RuleTestCard() }
    }
}

@Composable
private fun CooldownRow(value: Int, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("点击冷却", style = MaterialTheme.typography.titleMedium)
                Text(
                    "两次自动点击之间的最小间隔，期间不会重复点击",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${local.toInt()} ms",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = 400f..3000f,
            steps = 12,
        )
    }
}

@Composable
private fun BlacklistCard(
    settings: AppSettings,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    SectionCard {
        Text("应用黑名单", style = MaterialTheme.typography.titleLarge)
        Text(
            "这些应用内的界面不会被处理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("例如 com.example.app") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    onAdd(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
            ) {
                Text("添加")
            }
        }
        Spacer(Modifier.height(6.dp))
        if (settings.blacklist.isEmpty()) {
            Text(
                "暂无黑名单应用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            settings.blacklist.sorted().forEach { packageName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = packageName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onRemove(packageName) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "移除")
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardCard(
    mode: RewardMode,
    overlayGranted: Boolean,
    onModeChange: (RewardMode) -> Unit,
    onRequestOverlay: () -> Unit,
) {
    SectionCard {
        Text("奖励广告", style = MaterialTheme.typography.titleLarge)
        Text(
            "看广告领游戏道具、金币等奖励时如何处理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RewardOption(
            title = "直接跳过",
            description = "不管是否有奖励，立即跳过",
            selected = mode == RewardMode.IMMEDIATE,
            onClick = { onModeChange(RewardMode.IMMEDIATE) },
        )
        RewardOption(
            title = "等奖励到账后关闭",
            description = "检测到「已获得奖励」后再自动关闭",
            selected = mode == RewardMode.WAIT_REWARD,
            onClick = { onModeChange(RewardMode.WAIT_REWARD) },
        )
        RewardOption(
            title = "弹窗询问",
            description = "显示悬浮窗，由你决定是否跳过",
            selected = mode == RewardMode.ASK,
            onClick = { onModeChange(RewardMode.ASK) },
        )
        if (mode == RewardMode.ASK && !overlayGranted) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "弹窗询问需要「悬浮窗」权限",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    TextButton(onClick = onRequestOverlay) {
                        Text("授予悬浮窗权限", color = SunsetOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleTestCard() {
    var input by remember { mutableStateOf("") }
    val result = remember(input) {
        if (input.isBlank()) null else AdDetector.classifyText(input)
    }
    SectionCard {
        Text("规则测试", style = MaterialTheme.typography.titleLarge)
        Text(
            "输入一段控件文字，检查它会命中哪条规则",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：跳过 5s") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        if (input.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (result != null) "命中" else "未命中",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result != null) MintGreen else SunsetOrange,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = result ?: "没有匹配的规则",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
