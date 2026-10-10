package com.example.wattson.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.model.AppSettings
import com.example.wattson.model.FuelSavingRow
import com.example.wattson.ui.theme.*

@Composable
fun SavingsScreen(
    savingsRows: List<FuelSavingRow>,
    savingsTotal: Float,
    settings: AppSettings,
    lifetimeEfficiency: Float,
    isDemo: Boolean,
    onNavigateToSettings: () -> Unit
) {
    val totalEvCost = savingsRows.sumOf { it.evCost.toDouble() }.toFloat()
    val totalGasCost = savingsRows.sumOf { it.gasCost.toDouble() }.toFloat()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RivianBlack)
            .testTag("savings_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Fuel Savings vs Gas",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Lifetime and per-session savings against driving a gas SUV.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hero Savings Total Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.5.dp, RivianGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL SAVED",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (savingsTotal >= 0) "+$${String.format("%.2f", savingsTotal)}" else "-$${String.format("%.2f", -savingsTotal)}",
                            color = RivianGreen,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Saved across ${savingsRows.size} priced charging sessions",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL EV COST", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$${String.format("%.2f", totalEvCost)}", color = RivianAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("GAS SUV COST", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$${String.format("%.2f", totalGasCost)}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SAVINGS RATIO", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                val ratio = if (totalEvCost > 0) totalGasCost / totalEvCost else 1f
                                Text("${String.format("%.1f", ratio)}x", color = RivianGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Comparison methodology card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianDarkSurface),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Comparison Parameters", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = onNavigateToSettings,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Edit in Settings", color = RivianGreen, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = (if (isDemo) "• Gas SUV Baseline: ${settings.gasMpg.toInt()} MPG @ $${String.format("%.2f", settings.gasPrice)}/gal\n"
                                   else "• Gas SUV Baseline: ${settings.gasMpg.toInt()} MPG at each charge day's AAA ${settings.gasMetro} average (fallback $${String.format("%.2f", settings.gasPrice)}/gal)\n") +
                                   "• Rivian Home Rate: $${String.format("%.2f", settings.homeKwhRate)}/kWh all-in\n" +
                                   "• Fleet Efficiency: ${String.format("%.2f", lifetimeEfficiency)} mi/kWh",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Savings by Charging Session",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Home sessions calculate automatically; priced DC sessions included.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        if (savingsRows.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No priced charging sessions recorded yet.", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(savingsRows, key = { it.id }) { row ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (row.isDc) RivianBlue.copy(alpha = 0.2f) else RivianGreen.copy(alpha = 0.2f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (row.isDc) "DC" else "AC",
                                        color = if (row.isDc) RivianBlue else RivianGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = row.date,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = if (row.saved >= 0) "+$${String.format("%.2f", row.saved)}" else "-$${String.format("%.2f", -row.saved)}",
                                color = if (row.saved >= 0) RivianGreen else RivianAmber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${String.format("%.1f", row.kwh)} kWh added",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "EV: $${String.format("%.2f", row.evCost)} vs Gas: $${String.format("%.2f", row.gasCost)} @ $${String.format("%.2f", row.gasPrice)}/gal",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
