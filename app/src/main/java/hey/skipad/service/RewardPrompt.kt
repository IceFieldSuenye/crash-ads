package hey.skipad.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small floating card shown when a rewarded ad is detected and the user picked
 * "ask" mode. Uses a plain View hierarchy so it can be attached to the
 * WindowManager from an AccessibilityService without a Compose owner.
 */
class RewardPrompt(
    private val context: Context,
    private val onSkip: () -> Unit,
    private val onWait: () -> Unit,
) {

    private var root: View? = null
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun isShowing(): Boolean = root != null

    fun show(): Boolean {
        if (root != null) return true
        if (!Settings.canDrawOverlays(context)) return false

        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F2101826"))
            }
        }

        val title = TextView(context).apply {
            text = "检测到奖励广告"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }

        val message = TextView(context).apply {
            text = "现在跳过可能拿不到奖励。要等奖励到账后再自动关闭吗？"
            setTextColor(Color.parseColor("#B9C4D6"))
            textSize = 13f
            setLineSpacing(dp(3).toFloat(), 1f)
        }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }

        val waitButton = TextView(context).apply {
            text = "等一下"
            setTextColor(Color.parseColor("#B9C4D6"))
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                dismiss()
                onWait()
            }
        }

        val skipButton = TextView(context).apply {
            text = "仍然跳过"
            setTextColor(Color.parseColor("#5FA8FF"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                dismiss()
                onSkip()
            }
        }

        buttons.addView(waitButton)
        buttons.addView(skipButton)
        card.addView(title)
        card.addView(message)
        card.addView(buttons)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(110)
        }

        return runCatching {
            windowManager.addView(card, params)
            root = card
            true
        }.getOrDefault(false)
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        runCatching { windowManager.removeView(view) }
    }
}
