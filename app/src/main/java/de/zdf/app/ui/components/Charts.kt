package de.zdf.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.zdf.app.data.MonthSummary
import de.zdf.app.ui.monthShort
import de.zdf.app.ui.moneyShort
import de.zdf.app.ui.theme.Positive

data class Slice(val value: Long, val color: Color)

@Composable
fun Donut(slices: List<Slice>, size: Dp, centerTop: String, centerBottom: String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.14f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val total = slices.sumOf { it.value }.toFloat()
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            val gap = if (slices.size > 1) 1.5f else 0f
            for (s in slices) {
                val sweep = s.value / total * 360f
                drawArc(s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(centerBottom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Einnahmen (grün) vs. Ausgaben (rot/grau) je Monat. */
@Composable
fun CashflowBars(months: List<MonthSummary>, modifier: Modifier = Modifier) {
    val incomeColor = Positive
    val expenseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (months.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) } ?: 0L).coerceAtLeast(1)
    Column(modifier) {
        Text(moneyShort(max), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1f)
            val slot = size.width / months.size
            val bw = (slot * 0.28f).coerceAtMost(22.dp.toPx())
            months.forEachIndexed { i, m ->
                val cx = slot * i + slot / 2
                val hi = size.height * (m.incomeCents.toFloat() / max)
                val he = size.height * (m.expenseCents.toFloat() / max)
                val r = CornerRadius(4.dp.toPx())
                drawRoundRect(incomeColor, Offset(cx - bw - 1.dp.toPx(), size.height - hi), Size(bw, hi), r)
                drawRoundRect(expenseColor, Offset(cx + 1.dp.toPx(), size.height - he), Size(bw, he), r)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            months.forEach {
                Text(monthShort(it.month), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}
