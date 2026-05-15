package com.atlasfpt.ui.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.PricePoint
import java.time.format.DateTimeFormatter

@Composable
fun LineChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    showXLabels: Boolean = true,
) {
    if (points.size < 2) {
        Text(
            "Not enough data to chart",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(8.dp),
        )
        return
    }
    val minY = points.minOf { it.price }
    val maxY = points.maxOf { it.price }
    val span = (maxY - minY).takeIf { it > 0 } ?: 1.0

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(vertical = 4.dp)
        ) {
            val w = size.width
            val h = size.height
            val gridColor = Color.Gray.copy(alpha = 0.25f)
            val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = h - h * f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = if (f == 0f) null else dashed,
                )
            }
            val stepX = w / (points.size - 1).coerceAtLeast(1)
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = stepX * i
                val normalised = ((p.price - minY) / span).toFloat()
                val y = h - normalised * h * 0.95f - 4f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 3f))
        }
        if (showXLabels) {
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    points.first().date.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    points.last().date.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
