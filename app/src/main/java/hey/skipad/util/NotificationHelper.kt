package hey.skipad.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import hey.skipad.MainActivity
import hey.skipad.R
import hey.skipad.receiver.PauseReceiver

/**
 * Skip notifications are always silent: low-importance channel, no heads-up
 * banner, no sound, no vibration. The in-app toggle only decides whether the
 * notification is posted at all.
 */
object NotificationHelper {

    private const val CHANNEL_SILENT = "skip_silent"
    private const val CHANNEL_ONGOING = "service_running_v2"
    private const val LEGACY_ONGOING_CHANNEL = "service_running"
    private const val LEGACY_ALERT_CHANNEL = "skip_alert"
    private const val NOTIFICATION_ID = 1001
    private const val ONGOING_NOTIFICATION_ID = 1002
    private const val GROUP_KEY = "hey_skipad_skips"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(LEGACY_ALERT_CHANNEL)
        manager.deleteNotificationChannel(LEGACY_ONGOING_CHANNEL)

        val silent = NotificationChannel(
            CHANNEL_SILENT,
            "跳过提示",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "跳过广告时在通知栏静默提示，不弹横幅、不响铃"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(silent)
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "服务运行中",
            // IMPORTANCE_MIN collapses the whole notification on many vendor
            // ROMs and hides the action button, which made the pause switch
            // look broken. LOW stays silent but keeps the button tappable.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "无障碍服务运行时的常驻提示，可单独关闭"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(ongoing)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyRunning(context: Context) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val paused = PauseState.isPaused(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, PauseReceiver::class.java)
                .setAction(hey.skipad.service.AdSkipService.ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val actionLabel = if (paused) "开启拦截" else "暂停拦截"
        val text = if (paused) {
            "已手动暂停，点击右下按钮开启拦截"
        } else {
            "正在守护广告拦截，点击右下按钮可临时暂停"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_skip)
            .setContentTitle(if (paused) "闪退广告 已暂停" else "闪退广告 正在运行")
            .setContentText(text)
            .setSubText(if (paused) "暂停中" else "守护中")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stat_skip, actionLabel, pauseIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    fun cancelRunning(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(ONGOING_NOTIFICATION_ID)
        }
    }

    fun notifySkip(context: Context, label: String, rule: String, todayCount: Int) {
        if (!canNotify(context)) return
        ensureChannels(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = if (todayCount > 1) "已跳过广告 · 今日 $todayCount 次" else "已跳过广告"
        val notification = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_stat_skip)
            .setContentTitle(title)
            .setContentText("$label · $rule")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
