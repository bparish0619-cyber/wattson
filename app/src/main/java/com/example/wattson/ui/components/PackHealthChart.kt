package com.example.wattson.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.model.BatteryTrendPoint
import com.example.wattson.model.PackHealthPoint
import com.example.wattson.ui.theme.*

@Composable
fun BatteryTrendChart(
    points: List<BatteryTrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val minSoc = 0f
    val maxSoc = 100f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("End-of-Day Battery %", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("10-day span", color = TextTertiary, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val count = points.size

                // Gridlines at 25%, 50%, 75%
                listOf(0.25f, 0.50f, 0.75f).forEach { fraction ->
                    val y = canvasHeight * (1f - fraction)
                    drawLine(
                        color = RivianBorder.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (count > 1) {
                    val path = Path()
                    points.forEachIndexed { index, pt ->
                        val x = (index.toFloat() / (count - 1)) * canvasWidth
                        val y = canvasHeight * (1f - (pt.soc / maxSoc))

                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = RivianGreen,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw dots
                    points.forEachIndexed { index, pt ->
                        val x = (index.toFloat() / (count - 1)) * canvasWidth
                        val y = canvasHeight * (1f - (pt.soc / maxSoc))
                        drawCircle(color = RivianBlack, radius = 5.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = RivianGreen, radius = 3.5.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
        }
    }
}
