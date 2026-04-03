package com.pubpeb.mycompass.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.roundToInt

// ── Widget colour palette ──────────────────────────────────────────────────────────────────────
//
// TWO sets of constants:
//  • `CLR_*` — android.graphics.Color ints used by android.graphics.Paint (canvas drawing).
//  • `CP_*`  — ColorProvider(Color) used by Glance composables.
//
// WHY TWO SETS:
// ColorProvider(Int) treats the integer as an @ColorRes resource ID and passes it to
// Resources.getColor(), causing a Resources$NotFoundException crash when the launcher
// tries to inflate the RemoteViews (the int is a raw ARGB value, not a resource ID).
// The correct Glance API is ColorProvider(androidx.compose.ui.graphics.Color).
// The canvas Paint API uses android.graphics.Color ints, which is a completely
// different code path and is correct as-is.

// Canvas paint colours (android.graphics.Color ints)
private const val CLR_BG_CARD    = 0xFF161A22.toInt()
private const val CLR_TEXT_MAIN  = 0xFFCCD6F6.toInt()
private const val CLR_ACCENT_RED = 0xFFE63946.toInt()
private const val CLR_RING       = 0xFF2A2E38.toInt()
private const val CLR_CENTER     = 0xFF1E2230.toInt()

// Glance ColorProvider values (androidx.compose.ui.graphics.Color)
private val CP_BG_DEEP    = ColorProvider(Color(0xFF0D0F14))
private val CP_TEXT_MAIN  = ColorProvider(Color(0xFFCCD6F6))
private val CP_ACCENT_RED = ColorProvider(Color(0xFFE63946))

/**
 * Home-screen widget with three responsive size tiers:
 *
 * | Size         | Content                                   |
 * |--------------|-------------------------------------------|
 * | Small  (1×1) | Cardinal direction text only              |
 * | Medium (2×1) | Cardinal direction + degree number        |
 * | Large  (2×2) | Rotating arrow Bitmap pointing to N + label |
 *
 * Data is read from [widgetDataStore] which is written by [CompassWidgetUpdateWorker].
 * The worker runs:
 *  - **Immediately** when the widget is first placed (via [CompassWidgetReceiver.onUpdate])
 *  - **On tap** — tapping the widget sends [CompassWidgetReceiver.ACTION_REFRESH] which
 *    enqueues a fresh one-shot sensor read; the widget updates in-place within ~3–5 seconds.
 *  - **Every 15 minutes** via WorkManager periodic job (OS-enforced minimum interval).
 *
 * ### Glance gotchas
 * - `remember` is NOT supported inside `provideContent { }`. Bitmap is computed
 *   before `provideContent` is called and passed in as a parameter.
 * - Use `ColorProvider(androidx.compose.ui.graphics.Color)` for Glance composables.
 *   Do NOT pass raw ARGB ints — `ColorProvider(Int)` expects a @ColorRes resource ID
 *   and causes a `Resources$NotFoundException` crash in the launcher's RemoteViews inflater.
 *   Raw ARGB ints (`android.graphics.Color`) are only valid for `android.graphics.Paint`.
 * - `actionSendBroadcast` is in `androidx.glance.appwidget.action`, NOT `androidx.glance.action`.
 */
class CompassWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 100.dp),    // Small  — 1×1
            DpSize(180.dp,  80.dp),    // Medium — 2×1
            DpSize(180.dp, 180.dp),    // Large  — 2×2
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read persisted heading from DataStore (written by WorkManager worker)
        val prefs    = context.widgetDataStore.data.firstOrNull()
        val azimuth  = prefs?.get(WidgetPrefsKeys.AZIMUTH)  ?: 0f
        val cardinal = prefs?.get(WidgetPrefsKeys.CARDINAL) ?: "N"

        // Pre-render the arrow bitmap here — Glance composables do NOT support remember {}
        val arrowBmp = drawArrowBitmap(azimuth, sizePx = 280)

        // Build the tap-to-refresh action once — explicit Intent targets this app's
        // CompassWidgetReceiver so the broadcast is not system-wide.
        val refreshIntent = Intent(CompassWidgetReceiver.ACTION_REFRESH).apply {
            setClass(context, CompassWidgetReceiver::class.java)
        }
        val refreshAction: Action = actionSendBroadcast(refreshIntent)

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.width >= 180.dp && size.height >= 180.dp ->
                        LargeContent(arrowBmp, azimuth, cardinal, refreshAction)
                    size.width >= 180.dp ->
                        MediumContent(azimuth, cardinal, refreshAction)
                    else ->
                        SmallContent(cardinal, refreshAction)
                }
            }
        }
    }
}

// ── Small (1×1): cardinal direction only ─────────────────────────────────────

@androidx.compose.runtime.Composable
private fun SmallContent(cardinal: String, refreshAction: Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CP_BG_DEEP)
            .clickable(refreshAction),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cardinal,
            style = TextStyle(
                color = CP_TEXT_MAIN,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

// ── Medium (2×1): cardinal + degree number ────────────────────────────────────

@androidx.compose.runtime.Composable
private fun MediumContent(azimuth: Float, cardinal: String, refreshAction: Action) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CP_BG_DEEP)
            .padding(12.dp)
            .clickable(refreshAction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = cardinal,
            style = TextStyle(
                color = CP_ACCENT_RED,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = "${azimuth.roundToInt()}°",
            style = TextStyle(
                color = CP_TEXT_MAIN,
                fontSize = 28.sp,
            ),
        )
    }
}

// ── Large (2×2): rotating arrow + label ──────────────────────────────────────

@androidx.compose.runtime.Composable
private fun LargeContent(arrowBmp: Bitmap, azimuth: Float, cardinal: String, refreshAction: Action) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CP_BG_DEEP)
            .padding(8.dp)
            .clickable(refreshAction),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(arrowBmp),
            contentDescription = "Compass arrow pointing $cardinal",
            modifier = GlanceModifier.size(120.dp),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "$cardinal  ${azimuth.roundToInt()}°",
            style = TextStyle(
                color = CP_TEXT_MAIN,
                fontSize = 14.sp,
            ),
        )
    }
}

// ── Arrow bitmap renderer ─────────────────────────────────────────────────────

/**
 * Renders a compass arrow onto a [Bitmap] rotated by [azimuthDeg].
 *
 * The north (red) tip always points toward magnetic north after rotation.
 * The "N" label is drawn *outside* the rotation so it always appears at the top.
 *
 * @param azimuthDeg  Current heading in degrees [0, 360)
 * @param sizePx      Side length of the output bitmap in pixels (default 280)
 */
private fun drawArrowBitmap(azimuthDeg: Float, sizePx: Int = 280): Bitmap {
    val bmp    = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val outerR     = sizePx / 2f * 0.90f
    val arrowLen   = sizePx * 0.38f
    val halfWidth  = sizePx * 0.075f

    // ── 1. Background circle ──────────────────────────────────────────────────
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLR_BG_CARD
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, outerR, bgPaint)

    // ── 2. Ring border ────────────────────────────────────────────────────────
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLR_RING
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.02f
    }
    canvas.drawCircle(cx, cy, outerR, ringPaint)

    // ── 3. Rotate canvas — arrow points to magnetic north ────────────────────
    canvas.save()
    canvas.rotate(azimuthDeg, cx, cy)

    // North (red) triangle — tip at top
    val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLR_ACCENT_RED
        style = Paint.Style.FILL
    }
    val northPath = Path().apply {
        moveTo(cx, cy - arrowLen)
        lineTo(cx - halfWidth, cy)
        lineTo(cx + halfWidth, cy)
        close()
    }
    canvas.drawPath(northPath, northPaint)

    // South (silver) triangle — shorter tip at bottom
    val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLR_TEXT_MAIN
        style = Paint.Style.FILL
    }
    val southPath = Path().apply {
        moveTo(cx, cy + arrowLen * 0.75f)
        lineTo(cx - halfWidth, cy)
        lineTo(cx + halfWidth, cy)
        close()
    }
    canvas.drawPath(southPath, southPaint)

    // Centre cap
    val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLR_CENTER
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.07f, capPaint)
    val capRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLR_TEXT_MAIN
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.015f
    }
    canvas.drawCircle(cx, cy, sizePx * 0.07f, capRingPaint)

    canvas.restore()   // restore before drawing the static "N" label

    // ── 4. Static "N" label — always at top, never rotated ───────────────────
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLR_TEXT_MAIN
        textSize = sizePx * 0.12f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(
        "N",
        cx,
        sizePx * 0.09f + textPaint.textSize / 3f,
        textPaint,
    )

    return bmp
}
