package com.example.wattson.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.model.OvernightDrain
import com.example.wattson.ui.theme.*

@Composable
fun DrainBarChart(
    drains: List<OvernightDrain>,
    selectedDrain: OvernightDrain? = null,
    onSelectDrain: (OvernightDrain) -> Unit,
    modifier: Modifier = Modifier
) {
    if (drains.isEmpty()) {
        Text("No overnight drain data recorded yet.", color = TextSecondary, fontSize = 13.sp)
        return
    }

    val maxKwh = (drains.maxOfOrNull { it.kwhLost } ?: 1f).coerceAtLeast(1.2f)
    val medianKwh = drains.map { it.kwhLost }.sorted().let {
        if (it.isEmpty()) 0.4f else it[it.size / 2]
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "kWh Lost (10 PM – 7 AM)",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(RivianRed, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Abnormal (>1.6x median)", color = TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height - 24.dp.toPx()
                val bottomY = canvasHeight

                // Draw baseline median dashed line
                val medianY = bottomY - (medianKwh / maxKwh) * bottomY
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(0f, medianY),
                    end = Offset(canvasWidth, medianY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )

                // Draw zero baseline
                drawLine(
                    color = RivianBorder,
                    start = Offset(0f, bottomY),
                    end = Offset(canvasWidth, bottomY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Interactive bars overlay
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                drains.forEach { drain ->
                    val heightFraction = (drain.kwhLost / maxKwh).coerceIn(0.05f, 1f)
                    val barColor = if (drain.isAbnormal) RivianRed else RivianAmber
                    val isSelected = selectedDrain?.id == drain.id

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectDrain(drain) }
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = String.format("%.2f", drain.kwhLost),
                            color = if (drain.isAbnormal) RivianRed else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .background(barColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, Color.White, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    } else Modifier
                                )
                        )
                    }
                }
            }

            // X-axis day labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                drains.forEach { drain ->
                    Text(
                        text = drain.label.take(6),
                        color = TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
