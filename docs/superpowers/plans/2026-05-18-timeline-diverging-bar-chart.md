# Timeline Diverging Bar Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Timeline `CashFlowBarChart`'s side-by-side income/expense bars with single-column diverging bars (income above zero, expense below), and overlay a per-period net cashflow line.

**Architecture:** Pure UI-layer change scoped to `CashFlowBarChart.kt`. Same public composable signature, same horizontal-scroll plumbing, same selection model in `TimelineViewModel`. The only data input is the existing `List<CashFlowBar>` — net per period is computed inline. One color constant is added to `theme/Color.kt`.

**Tech Stack:** Jetpack Compose `Canvas` drawing (`drawRoundRect`, `drawLine`, `drawCircle`), `Modifier.horizontalScroll`, existing dark-theme Material 3 colors.

**Spec:** `docs/superpowers/specs/2026-05-18-timeline-diverging-bar-chart-design.md`

**Manual verification recipe (used in multiple tasks):**

```bash
./gradlew assembleDebug
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```

The user has a `/app-upload-latest` slash command that wraps these for convenience.

**No automated tests are added.** The change is pure Canvas drawing; the project has no Compose UI test infrastructure today and the spec excludes adding it.

---

## Task 1: Add `CashFlowNetColor` to theme

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/theme/Color.kt`

- [ ] **Step 1: Add the color constant**

Add `CashFlowNetColor` immediately after `IncomeColor`. After the edit, `Color.kt` should read:

```kotlin
package com.atlasfpt.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF1DB984)
val OnPrimary = Color(0xFF003827)
val Background = Color(0xFF121212)
val OnBackground = Color(0xFFE0E0E0)
val Surface = Color(0xFF1E1E1E)
val OnSurface = Color(0xFFE0E0E0)
val SurfaceVariant = Color(0xFF252525)
val OnSurfaceVariant = Color(0xFFB0B0B0)
val ExpenseColor = Color(0xFFE05252)
val IncomeColor = Color(0xFF1DB984)
val CashFlowNetColor = Color(0xFFFFD66E)
val DateHeaderBackground = Color(0xFF252525)
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Nothing references the new constant yet, but adding an unused top-level `val` should not break compilation.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/theme/Color.kt
git commit -m "feat: add CashFlowNetColor theme constant"
```

---

## Task 2: Refactor bar drawing to diverging layout

Transform the chart from "two side-by-side bars per slot (both positive)" to "one column per slot (income up, expense down) around a zero baseline". The net line is not added yet — that's Task 3. After this task the chart should look correct as a diverging bar chart with no overlay.

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt`

- [ ] **Step 1: Replace the chart-drawing constants and `Canvas` content**

Open `app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt`. Replace the entire file body with:

```kotlin
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasfpt.domain.usecase.CashFlowBar
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
```

Notes on the diff vs. today's file:
- Grid moved from `0% / 50% / 100% of h` to `top dashed / bottom dashed / zero solid` via the new `drawGrid` helper.
- `drawBar` signature changed: `(color, left, top, width, height)` instead of `(color, left, bottom, width, height)`. The earlier code reconstructed `topLeft` from `(left, bottom − height)`; the new code takes `top` directly so income (above zero) and expense (below zero) share one helper.
- Two bars per slot become one column: removed the `pairWidth`/`gap`/`pairLeft` math; both bars share `barLeft = cx − barWidth/2`.
- Added the unused-for-now `Color` import (used in `drawGrid` and the new `drawBar` signature).

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Compose's `@Composable` annotation processor runs as part of this; a structural error in the rewritten composable will surface here.)

- [ ] **Step 3: Install on the running emulator and verify the diverging layout**

Run:

```bash
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```

In the app, open the Timeline tab. Verify:

- Each period has one column, not two side-by-side bars.
- Income bars sit above a horizontal midline; expense bars sit below it.
- The midline is solid; the top and bottom edges of the chart area are dashed.
- The selected (rightmost on load) column is full opacity; others are dimmed.
- Tapping any column scopes the day list to that period — same behaviour as before.
- Switch through Monthly / Annual / Total modes; the chart updates correctly in each.
- Horizontally scroll the Monthly chart; bars remain aligned with their labels.

If the user has no period with `expense > income`, ask them to add a temporary test transaction so a period's expense bar visibly exceeds its income bar — verify both render at the same x-center, mirrored across the zero line.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt
git commit -m "feat(timeline): diverging income/expense bars around zero line"
```

---

## Task 3: Overlay the net cashflow line

Add a per-period net line with sign-aware y position and selection-aware per-segment alpha.

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt`

- [ ] **Step 1: Add line/dot constants and the `CashFlowNetColor` import**

Near the top of `CashFlowBarChart.kt`, alongside the existing imports, add:

```kotlin
import androidx.compose.ui.graphics.StrokeCap
import com.atlasfpt.ui.theme.CashFlowNetColor
```

Below the existing constants block, add:

```kotlin
private val NET_LINE_STROKE = 2.dp
private val NET_DOT_RADIUS = 3.5.dp
```

- [ ] **Step 2: Add a `drawNetLine` helper**

At the end of the file (after `drawBar`), add:

```kotlin
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
```

- [ ] **Step 3: Call `drawNetLine` after the bars in the `Canvas` block**

Inside the `Canvas { ... }` block in `CashFlowBarChart`, immediately after the `bars.forEachIndexed { ... }` loop that draws the bars, append:

```kotlin
                    drawNetLine(
                        bars = bars,
                        maxAbs = maxAbs,
                        zeroY = zeroY,
                        halfH = halfH,
                        slotWidth = slotWidth,
                        selectedIndex = selectedIndex
                    )
```

The bars loop must run first so the line and dots render on top of the bars.

- [ ] **Step 4: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install and verify the net line**

Run:

```bash
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```

In the app, open the Timeline tab. Verify:

- An amber line runs across the chart, connecting one filled dot per period.
- The line passes through the zero baseline when a period's net is zero, sits above it when net > 0, below it when net < 0.
- The dot for the selected (rightmost on load) period is bright; other dots are dimmed.
- The line segment(s) directly adjacent to the selected dot are bright; segments between two non-selected periods are dimmed.
- Tap a different column — the bright dot moves; the two segments adjacent to the new selection brighten; the previous bright segments dim.
- With only one period visible (e.g. Total mode if it produces a single bar, or a Monthly view with one month of data), only a single dot renders, with no line segments.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt
git commit -m "feat(timeline): overlay net cashflow line on bar chart"
```

---

## Self-Review Notes

- **Spec coverage:**
  - Architecture & data — Task 2 keeps the public composable signature; no domain/use case/ViewModel edits. ✓
  - Canvas geometry (zeroY at midpoint, 160dp height, 52dp slot) — Task 2 step 1 code. ✓
  - Symmetric Y-scale (`max(maxIncome, maxExpense).coerceAtLeast(1.0)`) — Task 2 step 1 code. ✓
  - Bars: single column at slot center, `(slotWidth * 0.4f).coerceAtMost(24.dp.toPx())`, 4dp corners, `IncomeColor`/`ExpenseColor`, income up / expense down — Task 2 step 1. ✓
  - Grid: solid zero baseline + dashed top/bottom; mid-line dropped — Task 2 `drawGrid` helper. ✓
  - Net line: per-segment `drawLine` calls (not a single polyline) for per-segment alpha; `StrokeCap.Round`; `CashFlowNetColor`; dots at radius 3.5dp — Task 3 `drawNetLine` helper. ✓
  - Selection alpha: bars + dot dim when not selected; line segment bright if either endpoint is selected — Task 3 step 2 code. ✓
  - `CashFlowNetColor` lives in `theme/Color.kt` — Task 1. ✓
  - Edge cases (single bar → only a dot; all-zero period → bars elided via `height <= 0` guard, dot on baseline; `maxAbs` coerced to ≥ 1) — covered by the `drawBar` early-return and `coerceAtLeast(1.0)`. ✓
  - Manual-only testing across the three modes — verification steps in Tasks 2 and 3. ✓
- **Placeholder scan:** No "TBD"/"TODO"/"similar to"/"add error handling" placeholders. All code blocks are concrete. ✓
- **Type consistency:** `drawBar(color, left, top, width, height)` signature is consistent between the helper definition (Task 2 step 1) and both call sites for income/expense bars. `drawNetLine` parameters match the call site (Task 3 step 3). `CashFlowNetColor` is defined as `Color(0xFFFFD66E)` in Task 1 and imported in Task 3 step 1. ✓
