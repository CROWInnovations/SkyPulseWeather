package com.crowinnovations.skypulseweather.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.crowinnovations.skypulseweather.domain.safeClamp

/**
 * Auto-sizing single-line text using BoxWithConstraints + TextMeasurer so hero temperatures
 * and headers adapt across all screen widths and OS font zoom scales without clipping or wrapping.
 */
@Composable
fun AutoFitSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxFontSize: TextUnit = 82.sp,
    minFontSize: TextUnit = 26.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    style: TextStyle = LocalTextStyle.current
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = contentAlignment
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val safeMaxSp = maxFontSize.value.coerceAtLeast(minFontSize.value)
        val safeMinSp = minFontSize.value

        val fittedSp = remember(
            text,
            availableWidthPx,
            safeMaxSp,
            safeMinSp,
            fontWeight,
            style.fontFamily,
            style.fontStyle
        ) {
            var candidate = safeMaxSp
            while (candidate > safeMinSp) {
                val measured = measurer.measure(
                    text = text,
                    style = style.copy(
                        fontSize = candidate.sp,
                        fontWeight = fontWeight
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                if (measured.size.width <= availableWidthPx) break
                candidate = (candidate - 2f).safeClamp(safeMinSp, safeMaxSp)
                if (candidate <= safeMinSp) break
            }
            candidate.safeClamp(safeMinSp, safeMaxSp)
        }

        Text(
            text = text,
            color = color,
            style = style.copy(
                fontSize = fittedSp.sp,
                lineHeight = (fittedSp * 1.06f).sp,
                fontWeight = fontWeight
            ),
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Single-line non-wrapping text helper enforcing maxLines = 1, softWrap = false, and ellipsis
 * to prevent vertical text-wrapping layout crush on narrow screens or scaled system fonts.
 */
@Composable
fun SingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}
