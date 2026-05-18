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
import kotlin.math.max

private val BAR_HEIGHT = 160.dp
private val SLOT_WIDTH = 52.dp
private val BAR_WIDTH_MAX = 24.dp
private const val BAR_WIDTH_FRACTION = 0.4f
private const val SELECTED_ALPHA = 1.0f
private const val UNSELECTED_ALPHA = 0.4f
private val NET_LINE_STROKE = 2.dp
private val NET_DOT_RADIUS = 3.5.dp

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

    val scrollState = rememberScrollState()
    val totalWidth = SLOT_WIDTH * bars.size
    val anchorKey = bars.firstOrNull()?.periodStart to bars.size
    LaunchedEffect(anchorKey) {
        snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .first()
            .let { scrollState.scrollTo(it) }
    }

    Column(modifier = modifier) {
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

                    drawGrid(w = w, h = h, zeroY = zeroY)

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
                        bars = bars,
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

private fun DrawScope.drawGrid(w: Float, h: Float, zeroY: Float) {
    val dashedColor = Color.Gray.copy(alpha = 0.3f)
    val zeroColor = Color.Gray.copy(alpha = 0.5f)
    val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

    drawLine(
        color = dashedColor,
        start = Offset(0f, 0f),
        end = Offset(w, 0f),
        strokeWidth = 1f,
        pathEffect = dashed
    )
    drawLine(
        color = dashedColor,
        start = Offset(0f, h),
        end = Offset(w, h),
        strokeWidth = 1f,
        pathEffect = dashed
    )
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
    bars: List<CashFlowBar>,
    maxAbs: Double,
    zeroY: Float,
    halfH: Float,
    slotWidth: Float,
    selectedIndex: Int
) {
    val points = bars.mapIndexed { index, bar ->
        val cx = slotWidth * index + slotWidth / 2f
        val net = bar.income - bar.expense
        val y = zeroY - (net / maxAbs).toFloat() * halfH
        Offset(cx, y)
    }

    val strokePx = NET_LINE_STROKE.toPx()
    for (i in 0 until points.lastIndex) {
        val touchesSelected = (i == selectedIndex) || (i + 1 == selectedIndex)
        val segmentAlpha = if (touchesSelected) SELECTED_ALPHA else UNSELECTED_ALPHA
        drawLine(
            color = CashFlowNetColor.copy(alpha = segmentAlpha),
            start = points[i],
            end = points[i + 1],
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }

    val dotRadiusPx = NET_DOT_RADIUS.toPx()
    points.forEachIndexed { index, point ->
        val dotAlpha = if (index == selectedIndex) SELECTED_ALPHA else UNSELECTED_ALPHA
        drawCircle(
            color = CashFlowNetColor.copy(alpha = dotAlpha),
            radius = dotRadiusPx,
            center = point
        )
    }
}
