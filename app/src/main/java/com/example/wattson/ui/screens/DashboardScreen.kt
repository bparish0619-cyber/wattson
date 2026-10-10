package com.example.wattson.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.R
import com.example.wattson.model.*
import com.example.wattson.ui.components.DonutGauge
import com.example.wattson.ui.components.HeroStatCard
import com.example.wattson.ui.components.WattsonIcons
import com.example.wattson.ui.theme.*

@Composable
fun DashboardScreen(
    vehicle: VehicleStatus,
    trips: List<Trip>,
    charges: List<ChargingSession>,
    drains: List<OvernightDrain>,
    savingsTotal: Float,
    lifetimeEfficiency: Float,
    onNavigateToTrips: () -> Unit,
    onNavigateToCharging: () -> Unit,
    onNavigateToSavings: () -> Unit,
    onNavigateToDrain: () -> Unit,
    onToggleLiveCharge: (Boolean) -> Unit,
    onQuickAddTrip: () -> Unit
) {
    val lastDrain = drains.firstOrNull()
    val lastCharge = charges.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RivianBlack)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Hero Vehicle Header Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                // Vehicle background art
                Image(
                    painter = painterResource(id = R.drawable.rivian_hero),
                    contentDescription = "Rivian vehicle",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    RivianBlack.copy(alpha = 0.85f),
                                    RivianBlack
                                )
                            )
                        )
                )

                // Header info
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = vehicle.name,
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(RivianGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .border(1.dp, RivianGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Online",
                                        color = RivianGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                text = vehicle.model,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        // Demo pill badge
                        Box(
                            modifier = Modifier
                                .background(RivianSurfaceVariant, RoundedCornerShape(16.dp))
                                .border(1.dp, RivianBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "DEMO DATA",
                                color = RivianAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Location & Odometer row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = RivianAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (vehicle.isCharging) "Charging at ${vehicle.location}" else "Parked at ${vehicle.location}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "${vehicle.odometerMi.toInt()} mi",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Rivian Battery Gauge Section
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = RivianCard),
                border = BorderStroke(1.dp, RivianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DonutGauge(
                        soc = vehicle.soc,
                        rangeMi = vehicle.rangeMi,
                        isCharging = vehicle.isCharging,
                        chargingPowerKw = vehicle.powerKw,
                        targetSoc = vehicle.targetSoc,
                        plugInSoc = vehicle.chargingSessionStartSoc
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RATED RANGE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${vehicle.rangeMi.toInt()} mi", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TARGET LIMIT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${vehicle.targetSoc.toInt()}%", color = RivianGreen, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STATUS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(if (vehicle.isCharging) "Charging" else "Parked", color = if (vehicle.isCharging) RivianAmber else TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live charging simulation button
                    Button(
                        onClick = { onToggleLiveCharge(!vehicle.isCharging) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vehicle.isCharging) RivianRed.copy(alpha = 0.2f) else RivianGreen.copy(alpha = 0.2f),
                            contentColor = if (vehicle.isCharging) RivianRed else RivianGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (vehicle.isCharging) RivianRed else RivianGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_charge_button")
                    ) {
                        Icon(
                            imageVector = if (vehicle.isCharging) WattsonIcons.PowerOff else WattsonIcons.EvStation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (vehicle.isCharging) "Stop Charging Session" else "Simulate Plug-in Charging (11 kW)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Section Title: 4 Hero KPI Cards
        item {
            Text(
                text = "Vehicle Metrics",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // 2x2 Grid of Hero Stat Cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Efficiency Card
                    HeroStatCard(
                        title = "Efficiency",
                        value = String.format("%.2f", lifetimeEfficiency),
                        unit = "mi/kWh",
                        subtitle = "Over ${trips.sumOf { it.miles.toDouble() }.toInt()} mi across ${trips.size} drives",
                        valueColor = RivianGreen,
                        icon = WattsonIcons.Speed,
                        testTag = "hero_stat_efficiency",
                        onClick = onNavigateToTrips,
                        modifier = Modifier.weight(1f)
                    )

                    // Fuel Savings Card
                    HeroStatCard(
                        title = "Fuel Savings",
                        value = if (savingsTotal >= 0) "+$${String.format("%.2f", savingsTotal)}" else "-$${String.format("%.2f", -savingsTotal)}",
                        unit = "",
                        subtitle = "vs 25 MPG SUV at local gas prices",
                        valueColor = RivianGreen,
                        icon = WattsonIcons.Savings,
                        testTag = "hero_stat_savings",
                        onClick = onNavigateToSavings,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Overnight Drain Card
                    HeroStatCard(
                        title = "Overnight Drain",
                        value = if (lastDrain != null) String.format("%.2f", lastDrain.kwhLost) else "-",
                        unit = "kWh",
                        subtitle = if (lastDrain != null) "About ${lastDrain.wattsAvg.toInt()} W avg over 9h" else "No drain data",
                        valueColor = if (lastDrain?.isAbnormal == true) RivianRed else RivianAmber,
                        icon = WattsonIcons.Bedtime,
                        testTag = "hero_stat_drain",
                        onClick = onNavigateToDrain,
                        modifier = Modifier.weight(1f)
                    )

                    // Latest Charging Session Card
                    HeroStatCard(
                        title = "Recent Charge",
                        value = if (lastCharge != null) "+${String.format("%.1f", lastCharge.kwhAdded)}" else "-",
                        unit = "kWh",
                        subtitle = if (lastCharge != null) "${lastCharge.label} • +${lastCharge.milesAdded.toInt()} mi" else "No charges",
                        valueColor = RivianAmber,
                        icon = WattsonIcons.EvStation,
                        testTag = "hero_stat_charging",
                        onClick = onNavigateToCharging,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        // Recent Drives Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Drives",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onNavigateToTrips,
                    modifier = Modifier.testTag("see_all_trips_button")
                ) {
                    Text("See all (${trips.size})", color = RivianGreen, fontSize = 13.sp)
                }
            }
        }

        // Render first 4 recent trips
        items(trips.take(4)) { trip ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RivianCard),
                border = BorderStroke(1.dp, RivianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .clickable { onNavigateToTrips() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${trip.startLabel} → ${trip.endLabel}",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${trip.miles} mi • ${trip.avgMph.toInt()} mph avg" + if (trip.tempF != null) " • ${trip.tempF.toInt()}°F" else "",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${String.format("%.2f", trip.miPerKwh)} mi/kWh",
                            color = RivianGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${trip.kwhUsed} kWh",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onQuickAddTrip,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, RivianBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("quick_add_trip_button")
            ) {
                Icon(WattsonIcons.AddRoad, contentDescription = null, modifier = Modifier.size(18.dp), tint = RivianGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate / Log New Drive", fontSize = 13.sp)
            }
        }
    }
}
