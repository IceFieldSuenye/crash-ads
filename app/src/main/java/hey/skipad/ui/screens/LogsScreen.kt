package hey.skipad.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import hey.skipad.R
import hey.skipad.data.SkipLogEntry
import hey.skipad.data.SkipOutcome
import hey.skipad.ui.components.LogRow
import hey.skipad.ui.theme.SunsetOrange
import hey.skipad.util.appLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    logs: List<SkipLogEntry>,
    onClear: () -> Unit,
) {
    var selected by remember { mutableStateOf<SkipLogEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_logs), style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = stringResource(R.string.logs_count, logs.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClear, enabled = logs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.logs_clear),
                    color = if (logs.isNotEmpty()) SunsetOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(logs, key = { it.id }) { entry ->
                    LogRow(entry = entry, onClick = { selected = entry })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    selected?.let { entry ->
        LogDetailDialog(entry = entry, onDismiss = { selected = null })
    }
}

@Composable
private fun LogDetailDialog(entry: SkipLogEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appName = remember(entry.packageName) { appLabel(context, entry.packageName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "跳过详情",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(14.dp))
                DetailLine("时间", formatTime(entry.timeMillis))
                DetailLine("应用", appName)
                DetailLine("包名", entry.packageName)
                DetailLine("命中", entry.label)
                DetailLine("规则", entry.rule)
                DetailLine(
                    "结果",
                    when (entry.outcome) {
                        SkipOutcome.CLICKED -> "直接点击"
                        SkipOutcome.GESTURE -> "手势点击"
                        SkipOutcome.FAILED -> "失败"
                        SkipOutcome.BLOCKED -> "为保护奖励已跳过本轮"
                    },
                )
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("关闭", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
