package com.atlasfpt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.usecase.CategoryBreakdown
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LABEL_VISIBILITY_THRESHOLD = 0.02f
private const val START_ANGLE_DEGREES = -90f

@Composable
fun CategoryDonut(
    slices: List<CategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return
    val total = slices.sumOf { it.amount }
    if (total <= 0.0) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.18f
            val outerRadius = (size.minDimension - stroke) / 2f
            val arcSize = Size(outerRadius * 2, outerRadius * 2)
            val topLeft = Offset(center.x - outerRadius, center.y - outerRadius)
            var startAngle = START_ANGLE_DEGREES
            slices.forEach { slice ->
                val fraction = (slice.amount / total).toFloat()
                val sweep = fraction * 360f
                drawArc(
                    color = Color(slice.category.color),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                startAngle += sweep
            }
        }

        val density = LocalDensity.current
        var cursorAngle = START_ANGLE_DEGREES
        slices.forEach { slice ->
            val fraction = (slice.amount / total).toFloat()
            val sweep = fraction * 360f
            val midAngleRad = Math.toRadians((cursorAngle + sweep / 2f).toDouble())
            cursorAngle += sweep

            if (fraction < LABEL_VISIBILITY_THRESHOLD) return@forEach

            val pctLabel = "%.1f%%".format(fraction * 100).removeSuffix(".0%") + (if (fraction * 100 % 1f == 0f) "%" else "")

            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val parentW = constraints.maxWidth
                        val parentH = constraints.maxHeight
                        val orbit = (minOf(parentW, parentH) * 0.5f) - with(density) { 12.dp.toPx() }
                        val cx = parentW / 2f + (orbit * cos(midAngleRad)).toFloat() - placeable.width / 2f
                        val cy = parentH / 2f + (orbit * sin(midAngleRad)).toFloat() - placeable.height / 2f
                        layout(placeable.width, placeable.height) {
                            placeable.place(IntOffset(cx.roundToInt(), cy.roundToInt()))
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(slice.category.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = slice.category.name.firstOrNull()?.uppercase() ?: "·",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val parentW = constraints.maxWidth
                        val parentH = constraints.maxHeight
                        val labelOrbit = (minOf(parentW, parentH) * 0.5f) + with(density) { 4.dp.toPx() }
                        val cx = parentW / 2f + (labelOrbit * cos(midAngleRad)).toFloat() - placeable.width / 2f
                        val cy = parentH / 2f + (labelOrbit * sin(midAngleRad)).toFloat() - placeable.height / 2f
                        layout(placeable.width, placeable.height) {
                            placeable.place(IntOffset(cx.roundToInt(), cy.roundToInt()))
                        }
                    }
            ) {
                Text(
                    text = pctLabel,
                    color = Color(slice.category.color),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
