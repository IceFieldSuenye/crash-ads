package hey.skipad.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import hey.skipad.R

/**
 * Small round floating ball (app icon) shown for rewarded ads. Tapping it
 * skips the ad; it never skips by itself so the reward is not lost.
 */
class RewardBall(
    private val context: Context,
    private val onSkip: () -> Unit,
) {

    private var view: View? = null
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun isShowing(): Boolean = view != null

    fun show(): Boolean {
        if (view != null) return true
        if (!Settings.canDrawOverlays(context)) return false

        val density = context.resources.displayMetrics.density
        val size = (58 * density).toInt()

        val ball = ImageView(context).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke((2 * density).toInt(), Color.parseColor("#33000000"))
            }
            val pad = (7 * density).toInt()
            setPadding(pad, pad, pad, pad)
            elevation = 8 * density
            contentDescription = "点击跳过广告"
            setOnClickListener {
                dismiss()
                onSkip()
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = (12 * density).toInt()
        }

        return runCatching {
            windowManager.addView(ball, params)
            view = ball
            true
        }.getOrDefault(false)
    }

    fun dismiss() {
        val ball = view ?: return
        view = null
        runCatching { windowManager.removeView(ball) }
    }
}
