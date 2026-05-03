package com.tempmonitor.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

/**
 * Builds a small status-bar icon whose pixels spell out the current battery temperature.
 *
 * The icon is rendered as an alpha-mask bitmap (white text on transparent background). Android's
 * status bar applies its own theme tint, so the digits show up in the correct color
 * automatically — light theme = dark digits, dark theme = light digits.
 */
internal object NotificationIconRenderer {

    private val cache = HashMap<Int, IconCompat>()

    /**
     * Returns an [IconCompat] suitable for [androidx.core.app.NotificationCompat.Builder.setSmallIcon].
     *
     * @param context used only to query display density.
     * @param celsius battery temperature in degrees Celsius. Negative values fall back to "—".
     */
    fun render(context: Context, celsius: Float): IconCompat {
        val rounded = celsius.roundToInt().coerceIn(-9, 199)
        cache[rounded]?.let { return it }

        // Status bar icons are roughly 24dp; render at 4× to stay sharp on hdpi/xxhdpi.
        val sizePx = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(72)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val text = if (rounded >= 0) rounded.toString() else "—"

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            isFakeBoldText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = sizePx * when (text.length) {
                1 -> 0.85f
                2 -> 0.78f
                else -> 0.62f
            }
        }

        val fm = paint.fontMetrics
        val cx = sizePx / 2f
        val cy = sizePx / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, cy, paint)

        val icon = IconCompat.createWithBitmap(bmp)
        cache[rounded] = icon
        return icon
    }
}
