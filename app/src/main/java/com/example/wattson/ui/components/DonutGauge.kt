package com.example.wattson.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.ui.theme.*

@Composable
fun DonutGauge(
    soc: Float,
    rangeMi: Float,
    isCharging: Boolean = false,
    chargingPowerKw: Float = 0f,
    targetSoc: Float = 90f,
    plugInSoc: Float? = null,
    size: Dp = 200.dp,
    strokeWidth: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chargingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val strokePx = strokeWidth.toPx()
            val canvasSize = this.size

            // Background circle track
            drawCircle(
                color = DonutTrack,
                radius = (canvasSize.minDimension - strokePx) / 2,
                center = Offset(canvasSize.width / 2, canvasSize.height / 2),
                style = Stroke(width = strokePx)
            )

            val sweepAngleMax = 360f
            val baseAngle = -90f // Start from top

            // If charging with a plugInSoc, show plug-in baseline (Green) and added charge (Amber)
            if (isCharging && plugInSoc != null && soc > plugInSoc) {
                val plugInSweep = (plugInSoc / 100f) * sweepAngleMax
                val addedSweep = ((soc - plugInSoc) / 100f) * sweepAngleMax

                // Base level
                drawArc(
                    color = RivianGreen,
                    startAngle = baseAngle,
                    sweepAngle = plugInSweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Added charge arc
                drawArc(
                    color = RivianAmber.copy(alpha = pulseAlpha),
                    startAngle = baseAngle + plugInSweep,
                    sweepAngle = addedSweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                // Standard battery arc
                val currentSweep = (soc.coerceIn(0f, 100f) / 100f) * sweepAngleMax
                val color = if (isCharging) RivianGreen.copy(alpha = pulseAlpha) else if (soc <= 15f) RivianRed else RivianGreen

                drawArc(
                    color = color,
                    startAngle = baseAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Target limit pip (90% daily limit)
            val targetAngle = Math.toRadians((baseAngle + (targetSoc / 100f) * sweepAngleMax).toDouble())
            val radius = (canvasSize.minDimension - strokePx) / 2
            val targetX = (canvasSize.width / 2) + radius * Math.cos(targetAngle).toFloat()
            val targetY = (canvasSize.height / 2) + radius * Math.sin(targetAngle).toFloat()
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = strokePx / 3.5f,
                center = Offset(targetX, targetY)
            )
        }

        // Center readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${soc.toInt()}%",
                color = TextPrimary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Text(
                text = "${rangeMi.toInt()} mi",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (isCharging) {
                Text(
                    text = if (chargingPowerKw > 0) "+${String.format("%.1f", chargingPowerKw)} kW" else "Charging",
                    color = RivianAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
