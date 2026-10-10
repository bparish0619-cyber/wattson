package com.example.wattson.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.model.BatteryTrendPoint
import com.example.wattson.model.OvernightDrain
import com.example.wattson.model.PackHealthPoint
import com.example.wattson.ui.components.BatteryTrendChart
import com.example.wattson.ui.components.DrainBarChart
import com.example.wattson.ui.theme.*

@Composable
fun DrainScreen(
    drains: List<OvernightDrain>,
    batteryTrend: List<BatteryTrendPoint>,
    packHealth: List<PackHealthPoint>
) {
    var selectedDrain by remember(drains) { mutableStateOf(drains.firstOrNull()) }
    val latestPack = packHealth.lastOrNull()

    val avgKwh = if (drains.isNotEmpty()) drains.map { it.kwhLost }.average().toFloat() else 0f
    val avgWatts = if (drains.isNotEmpty()) drains.map { it.wattsAvg }.average().toFloat() else 0f
    val abnormalCount = drains.count { it.isAbnormal }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RivianBlack)
            .testTag("drain_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Overnight Drain & Battery",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Multi-night vampire-drain trends, sleep vs wakeups, and pack health.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Morning Summary Headline
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "MORNING SUMMARY",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val latest = drains.firstOrNull()
                        if (latest != null) {
                            Text(
                                text = "Last night: ${String.format("%.2f", latest.kwhLost)} kWh lost (${latest.socStart.toInt()}% → ${latest.socEnd.toInt()}%)",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "About ${latest.wattsAvg.toInt()} W average draw over ${latest.hours.toInt()}h parked. Observed wake-ups: ${latest.wakeCount}.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            Text("No full night recorded yet.", color = TextSecondary, fontSize = 13.sp)
                        }

                        if (abnormalCount > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(RivianRed.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .border(1.dp, RivianRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = RivianRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$abnormalCount ${if (abnormalCount == 1) "night" else "nights"} had abnormal drain (>1.6x baseline median), likely from proximity wake-ups.",
                                    color = RivianRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Drain Trend Bar Chart
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Night-by-Night Trend",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Avg: ${String.format("%.2f", avgKwh)} kWh (~${avgWatts.toInt()} W)",
                                color = RivianAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        DrainBarChart(
                            drains = drains,
                            selectedDrain = selectedDrain,
                            onSelectDrain = { selectedDrain = it }
                        )
                    }
                }

                // Selected Night Detail card
                selectedDrain?.let { drain ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RivianDarkSurface),
                        border = BorderStroke(1.dp, if (drain.isAbnormal) RivianRed else RivianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Details: ${drain.day} (${drain.label})",
                                    color = if (drain.isAbnormal) RivianRed else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (drain.isAbnormal) "ABNORMAL DRAIN" else "TYPICAL",
                                    color = if (drain.isAbnormal) RivianRed else RivianGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Energy lost:", color = TextSecondary, fontSize = 12.sp)
                                Text("${drain.kwhLost} kWh (~${drain.wattsAvg.toInt()} W draw)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Battery drop:", color = TextSecondary, fontSize = 12.sp)
                                Text("${drain.socStart.toInt()}% → ${drain.socEnd.toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            drain.milesLost?.let { mi ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Range lost:", color = TextSecondary, fontSize = 12.sp)
                                    Text("${mi.toInt()} mi range", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Wakeups registered:", color = TextSecondary, fontSize = 12.sp)
                                Text("${drain.wakeCount}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Battery SoC Trend Line Chart
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily Battery Curve",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        BatteryTrendChart(points = batteryTrend)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Pack Health card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pack Health & Capacity",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The BMS's estimated battery pack capacity vs implied full range.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PACK CAPACITY", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${String.format("%.1f", latestPack?.capacityKwh ?: 91.4f)} kWh", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("BMS raw estimate", color = TextTertiary, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("USABLE CAPACITY", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("87.9 kWh", color = RivianGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Rated usable pack", color = TextTertiary, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("IMPLIED RANGE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${(latestPack?.impliedRangeMi ?: 336f).toInt()} mi", color = RivianAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Full 100% capacity", color = TextTertiary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
