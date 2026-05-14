package com.atlasfpt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasfpt.domain.usecase.MonthBar
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private const val BAR_HEIGHT_DP = 160
private const val CURRENT_ALPHA = 1.0f
private const val PRIOR_ALPHA = 0.55f

private val BAR_LABEL_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM\nyyyy", Locale("pt", "PT"))

@Composable
fun CashFlowBarChart(
    bars: List<MonthBar>,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return
    val maxValue = max(
        bars.maxOf { it.income },
        bars.maxOf { it.expense }
    ).coerceAtLeast(1.0)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT_DP.dp)
        ) {
            val w = size.width
            val h = size.height
            val gridColor = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f)
            val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

            // Grid: 0 (bottom), mid, top
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
                val alpha = if (bar.isCurrent) CURRENT_ALPHA else PRIOR_ALPHA

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

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEach { bar ->
                Box(
                    modifier = Modifier
                        .weight(1f),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = bar.month.atDay(1).format(BAR_LABEL_FORMATTER),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
