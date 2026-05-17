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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasfpt.domain.usecase.CashFlowBar
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlin.math.max

private val BAR_HEIGHT = 160.dp
private val SLOT_WIDTH = 52.dp
private const val SELECTED_ALPHA = 1.0f
private const val UNSELECTED_ALPHA = 0.4f

@Composable
fun CashFlowBarChart(
    bars: List<CashFlowBar>,
    selectedIndex: Int,
    onBarSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return

    val maxValue = remember(bars) {
        max(
            bars.maxOf { it.income },
            bars.maxOf { it.expense }
        ).coerceAtLeast(1.0)
    }

    val scrollState = rememberScrollState()
    val totalWidth = SLOT_WIDTH * bars.size
    // Re-anchor to the right whenever the bar set changes (mode switch, new data).
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
                    val gridColor = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f)
                    val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

                    listOf(0f, 0.5f, 1f).forEach { f ->
                        val y = h - h * f
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = if (f == 0f) null else dashed
                        )
                    }

                    val slotWidth = w / bars.size
                    val barWidth = (slotWidth * 0.32f).coerceAtMost(20.dp.toPx())
                    val gap = slotWidth * 0.06f
                    val pairWidth = barWidth * 2 + gap

                    bars.forEachIndexed { index, bar ->
                        val cx = slotWidth * index + slotWidth / 2f
                        val pairLeft = cx - pairWidth / 2f
                        val alpha = if (index == selectedIndex) SELECTED_ALPHA else UNSELECTED_ALPHA

                        val incomeH = (bar.income / maxValue).toFloat() * h
                        drawBar(
                            color = IncomeColor.copy(alpha = alpha),
                            left = pairLeft,
                            bottom = h,
                            width = barWidth,
                            height = incomeH
                        )

                        val expenseH = (bar.expense / maxValue).toFloat() * h
                        drawBar(
                            color = ExpenseColor.copy(alpha = alpha),
                            left = pairLeft + barWidth + gap,
                            bottom = h,
                            width = barWidth,
                            height = expenseH
                        )
                    }
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    color: androidx.compose.ui.graphics.Color,
    left: Float,
    bottom: Float,
    width: Float,
    height: Float
) {
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(left, bottom - height),
        size = Size(width, height),
        cornerRadius = cornerRadius
    )
}
