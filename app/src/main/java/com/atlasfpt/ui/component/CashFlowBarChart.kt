package com.atlasfpt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasfpt.domain.usecase.CashFlowBar
import com.atlasfpt.ui.theme.CashFlowNetColor
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private val BAR_HEIGHT = 160.dp
private val SLOT_WIDTH = 52.dp
private val BAR_WIDTH_MAX = 24.dp
private const val BAR_WIDTH_FRACTION = 0.4f
private const val SELECTED_ALPHA = 1.0f
private const val UNSELECTED_ALPHA = 0.4f
private val NET_LINE_STROKE = 2.dp
private val NET_DOT_RADIUS = 3.5.dp
private val GUTTER_WIDTH = 40.dp
private const val TICKS_PER_HALF = 2

@Composable
fun CashFlowBarChart(
    bars: List<CashFlowBar>,
    selectedIndex: Int,
    onBarSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return

    val maxAbs = remember(bars) {
        max(
            bars.maxOf { it.income },
            bars.maxOf { it.expense }
        ).coerceAtLeast(1.0)
    }

    val nets = remember(bars) {
        DoubleArray(bars.size) { bars[it].income - bars[it].expense }
    }

    val tickStep = remember(maxAbs) { niceStepFloor(maxAbs / TICKS_PER_HALF.toDouble()) }

    val scrollState = rememberScrollState()
    val totalWidth = SLOT_WIDTH * bars.size
    val anchorKey = bars.firstOrNull()?.periodStart to bars.size
    LaunchedEffect(anchorKey) {
        snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .first()
            .let { scrollState.scrollTo(it) }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        AxisGutter(
            tickStep = tickStep,
            maxAbs = maxAbs,
            modifier = Modifier.width(GUTTER_WIDTH).height(BAR_HEIGHT)
        )
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            Box(modifier = Modifier.width(totalWidth).height(BAR_HEIGHT)) {
                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    val w = size.width
                    val h = size.height
                    val zeroY = h / 2f
                    val halfH = h / 2f
                    val slotWidth = w / bars.size
                    val barWidth = (slotWidth * BAR_WIDTH_FRACTION)
                        .coerceAtMost(BAR_WIDTH_MAX.toPx())

                    drawGrid(
                        w = w,
                        halfH = halfH,
                        zeroY = zeroY,
                        maxAbs = maxAbs,
                        tickStep = tickStep
                    )

                    bars.forEachIndexed { index, bar ->
                        val cx = slotWidth * index + slotWidth / 2f
                        val barLeft = cx - barWidth / 2f
                        val alpha = if (index == selectedIndex) SELECTED_ALPHA else UNSELECTED_ALPHA

                        val incomeH = (bar.income / maxAbs).toFloat() * halfH
                        drawBar(
                            color = IncomeColor.copy(alpha = alpha),
                            left = barLeft,
                            top = zeroY - incomeH,
                            width = barWidth,
                            height = incomeH
                        )

                        val expenseH = (bar.expense / maxAbs).toFloat() * halfH
                        drawBar(
                            color = ExpenseColor.copy(alpha = alpha),
                            left = barLeft,
                            top = zeroY,
                            width = barWidth,
                            height = expenseH
                        )
                    }

                    drawNetLine(
                        nets = nets,
                        maxAbs = maxAbs,
                        zeroY = zeroY,
                        halfH = halfH,
                        slotWidth = slotWidth,
                        selectedIndex = selectedIndex
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    bars.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .width(SLOT_WIDTH)
                                .fillMaxHeight()
                                .clickable { onBarSelected(index) }
                        )
                    }
                }
            }

            Row(modifier = Modifier.width(totalWidth).padding(top = 6.dp)) {
                bars.forEachIndexed { index, bar ->
                    Box(
                        modifier = Modifier.width(SLOT_WIDTH),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bar.label,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = if (index == selectedIndex) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AxisGutter(
    tickStep: Double,
    maxAbs: Double,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            val zeroColor = MaterialTheme.colorScheme.onSurface
            val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
            val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)

            // Order: -2*step, -step, 0, +step, +2*step
            for (i in -TICKS_PER_HALF..TICKS_PER_HALF) {
                val value = tickStep * i
                Text(
                    text = formatAxisLabel(value),
                    style = labelStyle,
                    color = if (i == 0) zeroColor else tickColor,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val halfH = height / 2f
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        layout(width, height) {
            placeables.forEachIndexed { idx, placeable ->
                val tickIndex = idx - TICKS_PER_HALF  // -2..2
                val value = tickStep * tickIndex
                val tickFraction = (value / maxAbs).toFloat().coerceIn(-1f, 1f)
                val yCenter = halfH - tickFraction * halfH
                val x = width - placeable.width
                val y = yCenter.toInt() - placeable.height / 2
                placeable.placeRelative(x = x, y = y)
            }
        }
    }
}

private fun niceStepFloor(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val exponent = floor(log10(raw))
    val pow = 10.0.pow(exponent)
    val fraction = raw / pow
    val niceFraction = when {
        fraction >= 5.0 -> 5.0
        fraction >= 2.0 -> 2.0
        else -> 1.0
    }
    return niceFraction * pow
}

private fun formatAxisLabel(value: Double): String {
    if (value == 0.0) return "0"
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "−" else ""
    val body = if (abs >= 1000.0) {
        val k = abs / 1000.0
        if (k == k.toLong().toDouble()) "${k.toLong()}k"
        else String.format(java.util.Locale.US, "%.1fk", k).replace('.', ',')
    } else {
        if (abs == abs.toLong().toDouble()) "${abs.toLong()}"
        else String.format(java.util.Locale.US, "%.1f", abs).replace('.', ',')
    }
    return "$sign$body"
}

private fun DrawScope.drawGrid(
    w: Float,
    halfH: Float,
    zeroY: Float,
    maxAbs: Double,
    tickStep: Double
) {
    val zeroColor = Color.Gray.copy(alpha = 0.5f)
    val tickColor = Color.Gray.copy(alpha = 0.3f)
    val dotted = PathEffect.dashPathEffect(floatArrayOf(2f, 4f))

    for (i in 1..TICKS_PER_HALF) {
        val tickValue = tickStep * i
        val tickFraction = (tickValue / maxAbs).toFloat().coerceAtMost(1f)
        val tickOffset = tickFraction * halfH
        drawLine(
            color = tickColor,
            start = Offset(0f, zeroY - tickOffset),
            end = Offset(w, zeroY - tickOffset),
            strokeWidth = 1f,
            pathEffect = dotted
        )
        drawLine(
            color = tickColor,
            start = Offset(0f, zeroY + tickOffset),
            end = Offset(w, zeroY + tickOffset),
            strokeWidth = 1f,
            pathEffect = dotted
        )
    }

    drawLine(
        color = zeroColor,
        start = Offset(0f, zeroY),
        end = Offset(w, zeroY),
        strokeWidth = 1f
    )
}

private fun DrawScope.drawBar(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    if (height <= 0f) return
    val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = cornerRadius
    )
}

private fun DrawScope.drawNetLine(
    nets: DoubleArray,
    maxAbs: Double,
    zeroY: Float,
    halfH: Float,
    slotWidth: Float,
    selectedIndex: Int
) {
    val strokePx = NET_LINE_STROKE.toPx()
    for (i in 0 until nets.size - 1) {
        val touchesSelected = (i == selectedIndex) || (i + 1 == selectedIndex)
        val segmentAlpha = if (touchesSelected) SELECTED_ALPHA else UNSELECTED_ALPHA
        val xStart = slotWidth * i + slotWidth / 2f
        val yStart = zeroY - (nets[i] / maxAbs).toFloat() * halfH
        val xEnd = slotWidth * (i + 1) + slotWidth / 2f
        val yEnd = zeroY - (nets[i + 1] / maxAbs).toFloat() * halfH
        drawLine(
            color = CashFlowNetColor.copy(alpha = segmentAlpha),
            start = Offset(xStart, yStart),
            end = Offset(xEnd, yEnd),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }

    val dotRadiusPx = NET_DOT_RADIUS.toPx()
    for (i in nets.indices) {
        val dotAlpha = if (i == selectedIndex) SELECTED_ALPHA else UNSELECTED_ALPHA
        val cx = slotWidth * i + slotWidth / 2f
        val cy = zeroY - (nets[i] / maxAbs).toFloat() * halfH
        drawCircle(
            color = CashFlowNetColor.copy(alpha = dotAlpha),
            radius = dotRadiusPx,
            center = Offset(cx, cy)
        )
    }
}
