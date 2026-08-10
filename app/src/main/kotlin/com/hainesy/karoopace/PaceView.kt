package com.hainesy.karoopace

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.hammerhead.karooext.models.ViewConfig
import java.text.NumberFormat
import java.util.Locale

/**
 * Geometry measured off a native Karoo 3 field (AVG SPEED, 238x126 slot, 300dpi):
 *
 *  - the label sits in a ~20dp band at the top, uppercase, around 15sp
 *  - the number is drawn at exactly the host's [ViewConfig.textSize] (46sp in that slot)
 *  - the digits sit flush to the bottom of the card — 1px of clearance, not a padded gap
 *
 * The last point is the awkward one. A Text's line box includes descent below the baseline,
 * but digits have no descenders, so bottom-aligning leaves a gap native does not have. Instead
 * both label and number are offset from the top of the card by measured amounts, and the
 * number's line box is allowed to overflow the bottom, where it is clipped.
 */
private const val LABEL_SP = 15f

/** Sized so the glyph renders 23x18px, matching a native field icon measured at 300dpi. */
private const val ICON_DP = 15.1f

/**
 * Offsets from the top of the card, both measured against a native field on a 238x126 slot at
 * 300dpi (native: label glyphs 17px down, digit glyphs 53px down, digits ending 1px off the
 * bottom). Expressed in dp so they hold if the slot geometry changes.
 */
private const val LABEL_TOP_DP = 4.8f
private const val NUMBER_TOP_DP = 10.4f

/**
 * The Karoo remaps `sans-serif` to IBM Plex Sans and registers IBM Plex Sans Condensed Medium
 * as `ibm-sans-cond` (see /system/etc/fonts.xml on the device). The native data fields draw
 * their numbers in the condensed face — using plain sans-serif gives noticeably wider digits.
 */
private val KAROO_FONT = FontFamily("ibm-sans-cond")

/** Native shows "0", not "0.0" — one decimal only when there is one to show. */
private fun formatSpeed(value: Double): String =
    NumberFormat.getInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(value)
@Composable
fun PaceView(
    context: Context,
    config: ViewConfig,
    band: PaceBands.Band,
    speedDisplay: Double,
) {
    val background = Color(PaceBands.backgroundArgb(band))
    val foreground = Color(PaceBands.textArgb(band))
    val arrow = PaceBands.arrowLevel(band)

    val numberSp = config.textSize.toFloat()

    val textAlign = when (config.alignment) {
        ViewConfig.Alignment.LEFT -> TextAlign.Start
        ViewConfig.Alignment.CENTER -> TextAlign.Center
        else -> TextAlign.End
    }
    val rowAlign = when (config.alignment) {
        ViewConfig.Alignment.LEFT -> Alignment.Start
        ViewConfig.Alignment.CENTER -> Alignment.CenterHorizontally
        else -> Alignment.End
    }

    // Box rather than Column: stacked in a Column the number begins wherever the label row
    // ends, so the two positions are coupled and cannot both match native. Measured against a
    // native field the label needed to move down 9px while the number moved up 10px — only
    // possible if each is offset from the top independently.
    Box(
        // background before padding, so the colour fills the whole card. Padding first would
        // inset it and leave the card's edges uncoloured.
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(8.dp)
            .background(ColorProvider(background))
            .padding(start = 6.dp, end = 6.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = LABEL_TOP_DP.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // Native fields put a tinted icon hard left with the label pushed right. The icon
            // is green on an uncoloured card and black on a coloured one, same as the label.
            Image(
                modifier = GlanceModifier.size(ICON_DP.dp),
                provider = ImageProvider(R.drawable.ic_pace),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(iconColour(band, foreground))),
            )
            Text(
                modifier = GlanceModifier.defaultWeight(),
                text = context.getString(R.string.field_label),
                style = TextStyle(
                    color = ColorProvider(foreground),
                    fontSize = LABEL_SP.sp,
                    textAlign = textAlign,
                    fontFamily = KAROO_FONT,
                ),
                maxLines = 1,
            )
        }

        // Positioned from the top, not bottom-aligned: an oversized child spills past the
        // bottom of the card and is clipped there, which is exactly what puts the digits on
        // the bottom edge the way native does. Bottom-aligning instead leaves the font's
        // descent as a visible gap.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = NUMBER_TOP_DP.dp),
            verticalAlignment = Alignment.Vertical.Bottom,
            horizontalAlignment = rowAlign,
        ) {
            if (arrow != 0) {
                Image(
                    modifier = GlanceModifier
                        .size((numberSp * 0.55f).dp)
                        .padding(end = 4.dp),
                    provider = ImageProvider(arrowDrawable(arrow)),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(foreground)),
                )
            }
            Text(
                text = formatSpeed(speedDisplay),
                style = TextStyle(
                    color = ColorProvider(foreground),
                    fontSize = numberSp.sp,
                    textAlign = textAlign,
                    fontFamily = KAROO_FONT,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Native uncoloured fields tint their icon green (`elementViewStreamStateConnectedColor` in
 * ride.apk); on a coloured card everything goes black, icon included.
 */
private fun iconColour(band: PaceBands.Band, foreground: Color): Color = when (band) {
    PaceBands.Band.SAME, PaceBands.Band.NEUTRAL -> Color(0xFF129A5E)
    else -> foreground
}

private fun arrowDrawable(level: Int): Int = when (level) {
    2 -> R.drawable.ic_chevrons_up
    1 -> R.drawable.ic_chevron_up
    -1 -> R.drawable.ic_chevron_down
    else -> R.drawable.ic_chevrons_down
}
