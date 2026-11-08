package hey.skipad.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hey.skipad.R
import hey.skipad.data.AppSettings
import hey.skipad.data.SkipLogEntry
import hey.skipad.data.SkipStats
import hey.skipad.ui.components.Dot
import hey.skipad.ui.components.LogRow
import hey.skipad.ui.components.SectionCard
import hey.skipad.ui.components.StatCard
import hey.skipad.ui.theme.MintGreen
import hey.skipad.ui.theme.OceanBlue
import hey.skipad.ui.theme.SunsetOrange

@Composable
fun OverviewScreen(
    settings: AppSettings,
    stats: SkipStats,
    logs: List<SkipLogEntry>,
    running: Boolean,
    serviceEnabled: Boolean,
    batteryIgnored: Boolean,
    paused: Boolean,
    onOpenAccessibility: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePause: (Boolean) -> Unit,
    onRequestBattery: () -> Unit,
    onOpenOem: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeroCard(running, settings.enabled, onToggleEnabled) }
        item { PauseCard(paused, running, onTogglePause) }

        if (!serviceEnabled || !running) {
            item {
                if (!serviceEnabled) {
                    SetupBanner(
                        title = "需开启无障碍权限才能使用",
                        message = "闪退广告 通过无障碍服务识别并跳过广告。未开启权限时无法工作，请在系统设置中开启「闪退广告」。",
                        onOpen = onOpenAccessibility,
                    )
                } else {
                    SetupBanner(
                        title = "无障碍服务已断开",
                        message = "服务可能被系统回收或异常停止，请重新开启「闪退广告」。",
                        onOpen = onOpenAccessibility,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    value = stats.today.toString(),
                    label = stringResource(R.string.stat_today),
                    accent = OceanBlue,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = stats.total.toString(),
                    label = stringResource(R.string.stat_total),
                    accent = MintGreen,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { BatteryCard(batteryIgnored, onRequestBattery, onOpenOem) }
        item { RecentHitsCard(logs) }
        item { PrivacyCard() }
    }
}

@Composable
private fun PauseCard(
    paused: Boolean,
    running: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (paused) "已暂停拦截" else "自动拦截进行中",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (paused) SunsetOrange else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (paused) {
                        "所有应用都不会被自动跳过，重新打开开关立即恢复"
                    } else {
                        "临时关闭可暂停全部自动跳过，不必关掉无障碍服务"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = !paused,
                onCheckedChange = { onToggle(!it) },
                enabled = running,
            )
        }
    }
}

@Composable
private fun HeroCard(
    running: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0B7BFF), Color(0xFF00C08B)),
                ),
            )
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "无障碍 · 自动跳过开屏广告",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HeroPill(running)
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.rule_master),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.rule_master_desc),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OceanBlue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.55f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun HeroPill(running: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (running) Color(0xFF8CFFD2) else Color(0xFFFFD2C7)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(if (running) R.string.status_running else R.string.status_off),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SetupBanner(
    title: String,
    message: String,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpen) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}

@Composable
private fun BatteryCard(
    ignored: Boolean,
    onRequest: () -> Unit,
    onOpenOem: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (ignored) MintGreen else SunsetOrange,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.battery_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(ignored)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(if (ignored) R.string.battery_ok else R.string.battery_action),
                color = if (ignored) MintGreen else SunsetOrange,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!ignored) {
                FilledTonalButton(onClick = onRequest) {
                    Text(stringResource(R.string.battery_action))
                }
            }
            OutlinedButton(onClick = onOpenOem) {
                Text(stringResource(R.string.battery_oem_action))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.battery_oem_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentHitsCard(logs: List<SkipLogEntry>) {
    SectionCard {
        Text(stringResource(R.string.recent_hits), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        if (logs.isEmpty()) {
            Text(
                text = stringResource(R.string.recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            logs.take(3).forEach { entry -> LogRow(entry) }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "前往日志页查看全部记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
    SectionCard {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
