package com.openu60.core.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

@Composable
fun AnimatedNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
    color: Color = Color.Unspecified,
    animationDurationMs: Int = 400,
    prefix: String? = null,
    suffix: String? = null,
    separator: String? = null,
) {
    val animatedValue = animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = animationDurationMs),
        label = "animatedInt",
    )
    val formatted = remember(animatedValue.value, separator) {
        formatInt(animatedValue.value, separator)
    }
    NumberText(
        text = buildDisplay(prefix, formatted, suffix),
        modifier = modifier,
        style = style,
        color = color,
    )
}

@Composable
fun AnimatedNumber(
    value: Double,
    decimalPlaces: Int = 1,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
    color: Color = Color.Unspecified,
    animationDurationMs: Int = 400,
    prefix: String? = null,
    suffix: String? = null,
) {
    val target = value.toFloat()
    val animatedValue = animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = animationDurationMs),
        label = "animatedDouble",
    )
    val safeValue = animatedValue.value.takeIf { it.isFinite() }?.toDouble() ?: value
    val formatted = remember(safeValue, decimalPlaces) {
        String.format(Locale.US, "%.${decimalPlaces}f", safeValue)
    }
    NumberText(
        text = buildDisplay(prefix, formatted, suffix),
        modifier = modifier,
        style = style,
        color = color,
    )
}

@Composable
private fun NumberText(
    text: String,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = if (color == Color.Unspecified) LocalContentColor.current else color,
    )
}

private fun buildDisplay(prefix: String?, value: String, suffix: String?): String {
    return buildString {
        if (!prefix.isNullOrEmpty()) append(prefix)
        append(value)
        if (!suffix.isNullOrEmpty()) append(suffix)
    }
}

private fun formatInt(value: Int, separator: String?): String {
    return if (separator == null) {
        value.toString()
    } else {
        java.text.NumberFormat.getIntegerInstance(Locale.US)
            .format(value)
            .replace(",", separator)
    }
}
