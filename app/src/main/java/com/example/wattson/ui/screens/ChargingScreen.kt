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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.model.*
import com.example.wattson.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChargingScreen(
    vehicle: VehicleStatus,
    charges: List<ChargingSession>,
    settings: AppSettings,
    onToggleLiveCharge: (Boolean) -> Unit,
    onUpdateChargeCost: (chargeId: Long, costPerKwh: Float?) -> Unit,
    onAddChargingSession: (socStart: Float, socEnd: Float, kwhAdded: Float, type: ChargerType, label: String, cost: Float?) -> Unit
) {
    var showCostDialogForSession by remember { mutableStateOf<ChargingSession?>(null) }
    var showAddSessionDialog by remember { mutableStateOf(false) }

    val totalKwhAdded = charges.sumOf { it.kwhAdded.toDouble() }.toFloat()
    val totalMilesAdded = charges.sumOf { it.milesAdded.toDouble() }.toFloat()

    Scaffold(
        containerColor = RivianBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSessionDialog = true },
                containerColor = RivianAmber,
                contentColor = RivianBlack,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_add_charge")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log Charge Session")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("charging_screen"),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Charging Log",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sessions, kWh added, real home electricity costs, and DC fast charging.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live charging card if active
                    if (vehicle.isCharging) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = RivianSurfaceVariant),
                            border = BorderStroke(1.5.dp, RivianAmber),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_charging_card")
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(RivianAmber, RoundedCornerShape(5.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Charging in Progress",
                                            color = RivianAmber,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "${vehicle.powerKw} kW",
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Current Battery", color = TextSecondary, fontSize = 11.sp)
                                        Text("${vehicle.soc.toInt()}% (${vehicle.rangeMi.toInt()} mi)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Target Limit", color = TextSecondary, fontSize = 11.sp)
                                        Text("${vehicle.targetSoc.toInt()}%", color = RivianGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { onToggleLiveCharge(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RivianRed, contentColor = TextPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Unplug / Complete Session")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Rollup summary
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = RivianCard),
                        border = BorderStroke(1.dp, RivianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SESSIONS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${charges.size}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL KWH", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${totalKwhAdded.toInt()} kWh", color = RivianAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MILES ADDED", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("+${totalMilesAdded.toInt()} mi", color = RivianGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("HOME RATE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$${String.format("%.2f", settings.homeKwhRate)}/kWh", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(charges, key = { it.id }) { session ->
                ChargeRowItem(
                    session = session,
                    settings = settings,
                    onEditCost = { showCostDialogForSession = session }
                )
            }
        }
    }

    // DC Fast Charge Cost Dialog
    showCostDialogForSession?.let { session ->
        ChargeCostDialog(
            session = session,
            onDismiss = { showCostDialogForSession = null },
            onSave = { cost ->
                onUpdateChargeCost(session.id, cost)
                showCostDialogForSession = null
            }
        )
    }

    if (showAddSessionDialog) {
        AddSessionDialog(
            settings = settings,
            onDismiss = { showAddSessionDialog = false },
            onConfirm = { socStart, socEnd, kwh, type, label, cost ->
                onAddChargingSession(socStart, socEnd, kwh, type, label, cost)
                showAddSessionDialog = false
            }
        )
    }
}

@Composable
fun ChargeRowItem(
    session: ChargingSession,
    settings: AppSettings,
    onEditCost: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    val dateStr = sdf.format(Date(session.startTs * 1000))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RivianCard),
        border = BorderStroke(1.dp, RivianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("charge_item_${session.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (session.isDc) RivianBlue.copy(alpha = 0.2f) else RivianGreen.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (session.isDc) "DC FAST" else "HOME AC",
                            color = if (session.isDc) RivianBlue else RivianGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = session.label,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = dateStr,
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "+${session.kwhAdded} kWh  •  +${session.milesAdded.toInt()} mi",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${session.socStart.toInt()}% → ${session.socEnd.toInt()}%  •  ${session.minutes.toInt()} min @ ${String.format("%.1f", session.avgKw)} kW avg",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Cost section
                Column(horizontalAlignment = Alignment.End) {
                    if (session.isHome) {
                        val cost = session.kwhAdded * settings.homeKwhRate
                        Text(
                            text = "$${String.format("%.2f", cost)}",
                            color = RivianAmber,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "home rate",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    } else {
                        // DC Fast Charge cost
                        if (session.userCostPerKwh != null) {
                            val cost = session.kwhAdded * session.userCostPerKwh
                            Text(
                                text = "$${String.format("%.2f", cost)}",
                                color = RivianAmber,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onEditCost() }
                            )
                            Text(
                                text = "($${String.format("%.2f", session.userCostPerKwh)}/kWh • edit)",
                                color = RivianGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { onEditCost() }
                            )
                        } else {
                            TextButton(
                                onClick = onEditCost,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("? add cost", color = RivianAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChargeCostDialog(
    session: ChargingSession,
    onDismiss: () -> Unit,
    onSave: (Float?) -> Unit
) {
    var rateText by remember {
        mutableStateOf(session.userCostPerKwh?.let { String.format("%.2f", it) } ?: "0.42")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DC Fast Charge Cost", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Record what you paid per kWh at ${session.label} (+${session.kwhAdded} kWh added). Feeds fuel savings vs gas calculations.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Price per kWh ($)") },
                    placeholder = { Text("e.g. 0.42 or 0.00 for free") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = rateText.toFloatOrNull()
                    onSave(rate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack)
            ) {
                Text("Save Cost")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = RivianCardElevated
    )
}

@Composable
fun AddSessionDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onConfirm: (socStart: Float, socEnd: Float, kwh: Float, type: ChargerType, label: String, cost: Float?) -> Unit
) {
    var isDc by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("Home") }
    var socStartText by remember { mutableStateOf("45") }
    var socEndText by remember { mutableStateOf("85") }
    var kwhText by remember { mutableStateOf("35.2") }
    var costText by remember { mutableStateOf("0.45") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Charging Session", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !isDc,
                        onClick = {
                            isDc = false
                            label = "Home"
                        },
                        label = { Text("Home AC") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = isDc,
                        onClick = {
                            isDc = true
                            label = "Rivian Adventure Network"
                        },
                        label = { Text("DC Fast Charger") }
                    )
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Charger Location / Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = socStartText,
                        onValueChange = { socStartText = it },
                        label = { Text("Start %") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = socEndText,
                        onValueChange = { socEndText = it },
                        label = { Text("End %") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                OutlinedTextField(
                    value = kwhText,
                    onValueChange = { kwhText = it },
                    label = { Text("kWh Added") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (isDc) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("DC Cost per kWh ($)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sStart = socStartText.toFloatOrNull() ?: 50f
                    val sEnd = socEndText.toFloatOrNull() ?: 85f
                    val kwh = kwhText.toFloatOrNull() ?: 30f
                    val cost = if (isDc) costText.toFloatOrNull() else null
                    onConfirm(sStart, sEnd, kwh, if (isDc) ChargerType.DC_FAST else ChargerType.HOME_AC, label, cost)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RivianAmber, contentColor = RivianBlack)
            ) {
                Text("Save Session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = RivianCardElevated
    )
}
