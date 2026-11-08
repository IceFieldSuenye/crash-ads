package hey.skipad.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Display
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * Offline OCR fallback for splash ads whose skip button is painted as an image
 * and never exposed to the accessibility tree.
 *
 * Only the top-right region of the screen is captured, which keeps OCR fast
 * (well under one frame budget) and avoids reading unrelated content.
 */
class ScreenshotOcr(private val service: AccessibilityService) {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private var lastScanAt = 0L
    private var scanning = false

    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Captures the screen, OCRs the top-right area and invokes [onHit] with the
     * screen-space rect of a skip-looking element.
     */
    fun scan(onHit: (Rect) -> Unit) {
        if (!isAvailable()) return
        if (scanning) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastScanAt < MIN_INTERVAL_MS) return
        lastScanAt = now
        scanning = true

        runCatching {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hardware = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace,
                            )
                            if (hardware == null) {
                                scanning = false
                                return
                            }
                            val copy = hardware.copy(Bitmap.Config.ARGB_8888, false)
                            if (copy == null) {
                                scanning = false
                                return
                            }
                            recognize(copy, onHit)
                        } catch (t: Throwable) {
                            scanning = false
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        scanning = false
                    }
                },
            )
        }.onFailure {
            scanning = false
        }
    }

    private fun recognize(source: Bitmap, onHit: (Rect) -> Unit) {
        val width = source.width
        val height = source.height
        val cropLeft = (width * 0.50f).toInt()
        val cropTop = 0
        val cropRight = width
        val cropBottom = (height * 0.22f).toInt()

        val cropped = try {
            Bitmap.createBitmap(source, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
        } catch (t: Throwable) {
            scanning = false
            return
        }

        val image = InputImage.fromBitmap(cropped, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                try {
                    val hit = findSkipElement(text)
                    if (hit != null) {
                        onHit(
                            Rect(
                                hit.left + cropLeft,
                                hit.top + cropTop,
                                hit.right + cropLeft,
                                hit.bottom + cropTop,
                            ),
                        )
                    }
                } finally {
                    scanning = false
                }
            }
            .addOnFailureListener {
                scanning = false
            }
    }

    private fun findSkipElement(text: com.google.mlkit.vision.text.Text): Rect? {
        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val value = element.text.trim()
                    if (value.isEmpty()) continue
                    val lower = value.lowercase()
                    val looksLikeSkip = lower.contains("跳过") ||
                        lower.contains("跳過") ||
                        lower.contains("略过") ||
                        lower.contains("skip")
                    if (looksLikeSkip) {
                        return element.boundingBox
                    }
                }
            }
        }
        return null
    }

    fun close() {
        runCatching { recognizer.close() }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 250L
    }
}
