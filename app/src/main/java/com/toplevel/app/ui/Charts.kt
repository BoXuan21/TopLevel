package com.toplevel.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Minimal line chart: x = index order, y = value. Shows min/max labels. */
@Composable
fun LineChart(values: List<Float>, color: Color, unit: String, modifier: Modifier = Modifier, labels: Pair<String, String>? = null) {
    if (values.size < 2) {
        Text("Log at least 2 entries to see a trend.", color = C.muted, fontSize = 12.sp)
        return
    }
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0.0001f } ?: 1f
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text("${"%.1f".format(max)} $unit", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech,
                modifier = Modifier.weight(1f))
            Text("now ${"%.1f".format(values.last())} $unit", color = color, fontSize = 10.sp, fontFamily = Fonts.tech)
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val stepX = size.width / (values.size - 1)
            fun y(v: Float) = size.height - (v - min) / span * (size.height * 0.9f) - size.height * 0.05f
            // grid
            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(C.border.copy(alpha = 0.4f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            val line = Path()
            val fill = Path()
            values.forEachIndexed { i, v ->
                val p = Offset(i * stepX, y(v))
                if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, size.height); fill.lineTo(p.x, p.y) }
                else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
            }
            fill.lineTo(size.width, size.height)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
            drawCircle(color, radius = 7f, center = Offset((values.size - 1) * stepX, y(values.last())))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${"%.1f".format(min)} $unit", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech,
                modifier = Modifier.weight(1f))
            if (labels != null) Text("${labels.first} → ${labels.second}", color = C.muted, fontSize = 10.sp)
        }
    }
}
