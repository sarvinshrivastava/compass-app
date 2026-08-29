package com.pubpeb.compass.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pubpeb.compass.MainActivity
import com.pubpeb.compass.Constants
import com.pubpeb.compass.R

/**
 * The one brand colour the tile pins down.
 *
 * Everything else follows the launcher's theme; the mark does not, because a
 * compass needle that is not red stops reading as a compass needle. Mirrors
 * `@color/brand_red`, which the picker preview layout uses.
 *
 * Must be `ColorProvider(androidx.compose.ui.graphics.Color)`: the `Int`
 * overload treats its argument as an `@ColorRes` id and hands it to
 * `Resources.getColor()`, so a raw ARGB literal there crashes the launcher's
 * RemoteViews inflater with `Resources$NotFoundException`.
 */
private val BrandRed = ColorProvider(Color(Constants.Widget.BRAND_RED))

/**
 * Home-screen tile: a compass mark, the app's name, and nothing else.
 *
 * ### Why there is no heading here
 * A widget cannot hold the magnetometer open, so any number it showed would be
 * a single reading snatched in whatever pose the phone happened to be in —
 * face-down on a desk, or in a pocket — and then displayed for up to fifteen
 * minutes with no timestamp and no accuracy gate. That is a guess presented as
 * a fact, and it is worse than no number at all. The tile is a launcher: it
 * gets the user to the live compass in one tap, and claims nothing on its own.
 *
 * ### Glance gotchas
 * - `remember` is NOT supported inside `provideContent { }`. Anything stateful
 *   must be computed before the call. (Nothing here is.)
 * - The reified `actionStartActivity<T : Activity>()` lives in
 *   `androidx.glance.action`. The `androidx.glance.appwidget.action` package
 *   has same-named overloads that take an Intent instead, and importing that
 *   one leaves the reified call unresolvable.
 */
class CompassWidget : GlanceAppWidget() {

    /**
     * Two tiers, because with no data there are only two honest layouts: the
     * mark alone when the tile is roughly square, and the mark plus wordmark
     * when it is wide. A third tier would differ from its neighbours by
     * padding alone.
     */
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(60.dp, 60.dp),     // narrow — mark only
            DpSize(140.dp, 60.dp),    // wide   — mark + wordmark
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val label = context.getString(R.string.app_name)

        provideContent {
            GlanceTheme {
                val showWordmark = LocalSize.current.width >= Constants.Widget.WORDMARK_MIN_WIDTH
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        // Follow the launcher's theme — home screens are light
                        // at least as often as they are dark, and a tile that
                        // hardcodes the app's night palette looks like a hole
                        // punched in a light wallpaper.
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        // The whole tile is the target. A launcher tile with a
                        // dead margin reads as broken.
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_launcher_monochrome),
                            // Single-colour vector, so one tint paints the whole
                            // mark. Description is dropped when the wordmark is
                            // visible so TalkBack does not say "Compass" twice.
                            colorFilter = ColorFilter.tint(BrandRed),
                            contentDescription = if (showWordmark) null else label,
                            // The mark shrinks when the wordmark joins it: two
                            // cells is only ~140dp and the pair has to fit
                            // without the launcher clipping the text.
                            modifier = GlanceModifier.size(if (showWordmark) 32.dp else 40.dp),
                        )
                        if (showWordmark) {
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                text = label,
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
