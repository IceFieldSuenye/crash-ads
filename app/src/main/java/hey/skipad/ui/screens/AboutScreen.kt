package hey.skipad.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import hey.skipad.R
import hey.skipad.ui.components.SectionCard
import hey.skipad.ui.theme.MintGreen
import hey.skipad.ui.theme.OceanBlue

private data class OssEntry(val name: String, val version: String, val license: String)

private val ossEntries = listOf(
    OssEntry("AndroidX Core KTX", "1.19.1", "Apache-2.0"),
    OssEntry("AndroidX Activity Compose", "1.12.4", "Apache-2.0"),
    OssEntry("AndroidX Lifecycle Runtime / ViewModel", "2.11.0", "Apache-2.0"),
    OssEntry("Jetpack Compose BOM", "2026.09.00", "Apache-2.0"),
    OssEntry("Compose Material 3", "随 Compose BOM", "Apache-2.0"),
    OssEntry("Compose Material Icons", "随 Compose BOM", "Apache-2.0"),
    OssEntry("AndroidX DataStore Preferences", "1.3.0", "Apache-2.0"),
    OssEntry("Kotlin Coroutines", "1.10.2", "Apache-2.0"),
    OssEntry("Kotlin Standard Library", "2.4.21", "Apache-2.0"),
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    val licenseText = remember {
        runCatching {
            context.assets.open("licenses/apache-2.0.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("许可证文本缺失，请查看项目根目录 LICENSE 文件。")
    }
    val changelogText = remember {
        runCatching {
            context.assets.open("changelog.md").bufferedReader().use { it.readText() }
        }.getOrDefault("暂无更新日志。")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(stringResource(R.string.nav_about), style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "生成说明 · 开源合规",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { AppInfoCard() }
        item { AiNoticeCard() }
        item { OpenSourceCard(onViewLicense = { showLicense = true }) }
        item { ProjectLicenseCard() }
        item { ChangelogCard(onView = { showChangelog = true }) }
    }

    if (showChangelog) {
        TextContentDialog(
            title = "更新日志",
            text = changelogText,
            onDismiss = { showChangelog = false },
        )
    }

    if (showLicense) {
        Dialog(onDismissRequest = { showLicense = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.about_license_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(text = licenseText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showLicense = false },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppInfoCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "版本 ${hey.skipad.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))
        InfoLine("包名", "hey.skipad")
        InfoLine("作者", "IceFieldSuenye")
        InfoLine("最低系统", "Android 10（API 29）")
        InfoLine("技术栈", "Kotlin · Jetpack Compose · Material 3")
        InfoLine("核心能力", "无障碍服务（AccessibilityService）")
        InfoLine("网络", "不申请 INTERNET 权限 · 完全离线")
        InfoLine("权限", "无障碍、忽略电池优化、悬浮窗、通知")
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AiNoticeCard() {
    SectionCard {
        Text(
            text = stringResource(R.string.about_ai_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.about_ai_body),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun OpenSourceCard(onViewLicense: () -> Unit) {
    SectionCard {
        Text(
            text = stringResource(R.string.about_oss_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_oss_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ossEntries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = entry.version,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = entry.license,
                    style = MaterialTheme.typography.labelMedium,
                    color = MintGreen,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onViewLicense) {
            Text(
                text = stringResource(R.string.about_view_license),
                color = OceanBlue,
            )
        }
    }
}

@Composable
private fun ChangelogCard(onView: () -> Unit) {
    SectionCard {
        Text(
            text = "更新日志",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "查看各版本的修复与新增内容",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onView) {
            Text("查看更新日志", color = OceanBlue)
        }
    }
}

@Composable
private fun TextContentDialog(title: String, text: String, onDismiss: () -> Unit) {
    val lines = remember(text) { parseMarkdown(text) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(lines) { line -> MarkdownLine(line) }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

private enum class MdStyle { H1, H2, H3, BULLET, BODY, BLANK }

private data class MdLine(val text: String, val style: MdStyle)

private fun parseMarkdown(raw: String): List<MdLine> =
    raw.lines().map { line ->
        when {
            line.startsWith("# ") -> MdLine(line.removePrefix("# ").trim(), MdStyle.H1)
            line.startsWith("## ") -> MdLine(line.removePrefix("## ").trim(), MdStyle.H2)
            line.startsWith("### ") -> MdLine(line.removePrefix("### ").trim(), MdStyle.H3)
            line.trimStart().startsWith("- ") ->
                MdLine("• " + line.trimStart().removePrefix("- ").trim(), MdStyle.BULLET)
            line.isBlank() -> MdLine("", MdStyle.BLANK)
            else -> MdLine(line, MdStyle.BODY)
        }
    }

@Composable
private fun MarkdownLine(line: MdLine) {
    when (line.style) {
        MdStyle.H1 -> Text(
            text = line.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        MdStyle.H2 -> Text(
            text = line.text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        MdStyle.H3 -> Text(
            text = line.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MdStyle.BULLET -> Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MdStyle.BLANK -> Spacer(Modifier.height(6.dp))
        MdStyle.BODY -> Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProjectLicenseCard() {
    SectionCard {
        Text(
            text = stringResource(R.string.about_license_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.about_license_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
