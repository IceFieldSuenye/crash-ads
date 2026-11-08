package hey.skipad.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import hey.skipad.service.AdSkipService
import hey.skipad.util.NotificationHelper
import hey.skipad.util.PauseState

/**
 * Handles the "pause / resume" action on the ongoing notification.
 *
 * The toggle is written to SharedPreferences (read synchronously by the
 * service on every event) and the running service is nudged so queued scans
 * are dropped instantly. A toast confirms the tap even on ROMs that fail to
 * redraw the notification.
 */
class PauseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val paused = PauseState.toggle(context)

        runCatching {
            Toast.makeText(
                context,
                if (paused) "已暂停拦截" else "已恢复拦截",
                Toast.LENGTH_SHORT,
            ).show()
        }

        NotificationHelper.notifyRunning(context)

        runCatching {
            context.startService(
                Intent(context, AdSkipService::class.java)
                    .setAction(AdSkipService.ACTION_PAUSE_CHANGED),
            )
        }
    }
}
