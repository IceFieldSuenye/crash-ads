package hey.skipad.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating card shown after several interactive ads in a row ("上滑浏览 获取奖励"
 * and friends). Those ads only advance their countdown when the user swipes, so
 * the card asks once whether to keep quitting them outright.
 */
class InteractivePrompt(
    private val context: Context,
    private val onSkip: () -> Unit,
    private val onStay: () -> Unit,
) {

    private var view: View? = null
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun isShowing(): Boolean = view != null

    fun show(): Boolean {
        if (view != null) return true
        if (!Settings.canDrawOverlays(context)) return false

        val density = context.resources.displayMetrics.density
        val widthPx = (context.resources.displayMetrics.widthPixels - (32 * density).toInt())
            .coerceAtLeast((240 * density).toInt())

        fun px(value: Int) = (value * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(Color.WHITE)
                setStroke(px(1), Color.parseColor("#22000000"))
            }
            setPadding(px(18), px(16), px(18), px(14))
            elevation = 12 * density
        }

        card.addView(
            TextView(context).apply {
                text = "连续遇到互动广告"
                setTextColor(Color.parseColor("#111114"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        card.addView(
            TextView(context).apply {
                text = "这类广告必须滑屏才会继续计时。要直接跳过吗？"
                setTextColor(Color.parseColor("#5A5A66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setPadding(0, px(6), 0, px(12))
            },
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        row.addView(
            Button(context).apply {
                text = "继续观看"
                isAllCaps = false
                setOnClickListener {
                    dismiss()
                    onStay()
                }
            },
        )
        row.addView(
            Button(context).apply {
                text = "直接跳过"
                isAllCaps = false
                setOnClickListener {
                    dismiss()
                    onSkip()
                }
            },
        )
        card.addView(row)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = px(110)
        }

        return runCatching {
            windowManager.addView(card, params)
            view = card
            true
        }.getOrDefault(false)
    }

    fun dismiss() {
        val current = view ?: return
        view = null
        runCatching { windowManager.removeView(current) }
    }
}
