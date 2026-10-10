package com.example.wattson.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.data.rivian.RivianSession
import com.example.wattson.model.AppSettings
import com.example.wattson.model.PollingMode
import com.example.wattson.ui.theme.*
import java.util.Locale

@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    rivianSession: RivianSession?,
    isSyncing: Boolean,
    needsReauth: Boolean,
    lastPollTs: Long?,
    onSaveSettings: (AppSettings) -> Unit,
    onSetHomeHere: () -> Boolean,
    onSyncRivian: () -> Unit,
    onUnlinkRivian: () -> Unit,
    onResetDemoData: () -> Unit
) {
    var vehicleName by remember { mutableStateOf(currentSettings.vehicleName) }
    var vehicleModel by remember { mutableStateOf(currentSettings.vehicleModel) }
    // Editable numbers use Locale.US so the text field always shows a parseable value.
    var homeRateText by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentSettings.homeKwhRate)) }
    var gasMpgText by remember { mutableStateOf(currentSettings.gasMpg.toInt().toString()) }
    var gasPriceText by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentSettings.gasPrice)) }
    var usableKwhText by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentSettings.usableKwh)) }
    var gasStateText by remember { mutableStateOf(currentSettings.gasState) }
    var gasMetroText by remember { mutableStateOf(currentSettings.gasMetro) }
    var pollingMode by remember { mutableStateOf(currentSettings.pollingMode) }
    // Keyed so "use car's location" (which saves immediately) refreshes these fields.
    var homeLatText by remember(currentSettings.homeLat) { mutableStateOf(currentSettings.homeLat?.let { String.format(Locale.US, "%.5f", it) } ?: "") }
    var homeLonText by remember(currentSettings.homeLon) { mutableStateOf(currentSettings.homeLon?.let { String.format(Locale.US, "%.5f", it) } ?: "") }
    var homeMessage by remember { mutableStateOf<String?>(null) }
    var showSavedSnackbar by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RivianBlack)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp)
    ) {
        item {
            Text(
                text = "Settings & Configuration",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Rivian GraphQL account, rates, fuel comparison baseline.",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Rivian Account Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RivianCard),
                border = BorderStroke(1.dp, if (rivianSession != null) RivianGreen else RivianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RIVIAN ACCOUNT CREDENTIALS",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Box(
                            modifier = Modifier
                                .background(
                                    if (rivianSession != null) RivianGreen.copy(alpha = 0.2f) else RivianAmber.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (rivianSession != null) "LINKED" else "DEMO MODE",
                                color = if (rivianSession != null) RivianGreen else RivianAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (rivianSession != null) {
                        if (needsReauth) {
                            Text(
                                text = "Rivian ended this session, so tracking is paused. Unlink, then sign in again.",
                                color = RivianRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Logged in as ${rivianSession.email}",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (rivianSession.vehicleVin != null) {
                            Text(
                                text = "Vehicle VIN: ${rivianSession.vehicleVin} (session encrypted on this device)",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = lastPollTs?.let { "Last poll ${relativeTime(it)}" } ?: "Waiting for the first poll",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSyncRivian,
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(color = RivianBlack, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Telemetry", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = onUnlinkRivian,
                                border = BorderStroke(1.dp, RivianRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RivianRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Unlink Account", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "Running in Demo Mode with fictional R2 telemetry. Credentials are saved only within the app when linked.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = onUnlinkRivian,
                            colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Link Real Rivian Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vehicle profile
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RivianCard),
                border = BorderStroke(1.dp, RivianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "VEHICLE INFORMATION",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = vehicleName,
                        onValueChange = { vehicleName = it },
                        label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RivianGreen,
                            unfocusedBorderColor = RivianBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vehicleModel,
                        onValueChange = { vehicleModel = it },
                        label = { Text("Vehicle Model") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RivianGreen,
                            unfocusedBorderColor = RivianBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingsField(usableKwhText, { usableKwhText = it }, "Usable battery (kWh)",
                        "Converts battery % to kWh. R2: 87.9. Check Rivian's figure for your pack.")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Home location: decides which charges are costed at the home rate
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RivianCard),
                border = BorderStroke(1.dp, RivianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "HOME CHARGING LOCATION",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Charges within 250 m of home, or at a place the car calls HOME, are costed at your home rate.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsField(homeLatText, { homeLatText = it }, "Latitude", null, Modifier.weight(1f))
                        SettingsField(homeLonText, { homeLonText = it }, "Longitude", null, Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = {
                            homeMessage = if (onSetHomeHere()) "Home set to the car's last reported position." else if (rivianSession == null) "Link your Rivian account first." else "No GPS position from the car yet."
                        },
                        border = BorderStroke(1.dp, RivianGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RivianGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use the car's current location", fontSize = 13.sp)
                    }
                    homeMessage?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fuel Savings & Rate parameters
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RivianCard),
                border = BorderStroke(1.dp, RivianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "FUEL SAVINGS & ELECTRICITY RATES",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = homeRateText,
                        onValueChange = { homeRateText = it },
                        label = { Text("Home Electricity Rate ($/kWh)") },
                        supportingText = { Text("Used for home charging cost (default $0.30)", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RivianGreen,
                            unfocusedBorderColor = RivianBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = gasMpgText,
                        onValueChange = { gasMpgText = it },
                        label = { Text("Comparison Gas SUV MPG") },
                        supportingText = { Text("Efficiency of gas SUV to compare against (default 25 MPG)", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RivianGreen,
                            unfocusedBorderColor = RivianBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = gasPriceText,
                        onValueChange = { gasPriceText = it },
                        label = { Text("Gasoline Price ($/gallon)") },
                        supportingText = { Text("Regional gas price baseline (default $4.50)", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RivianGreen,
                            unfocusedBorderColor = RivianBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingsField(gasStateText, { gasStateText = it }, "AAA gas price state (e.g. CA)",
                        "Daily regular-gas average scraped from gasprices.aaa.com")
                    SettingsField(gasMetroText, { gasMetroText = it }, "AAA metro area",
                        "Exactly as listed on the AAA state page, e.g. Los Angeles-Long Beach")

                    if (rivianSession != null) {
                        Text(
                            text = "BACKGROUND POLLING",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        PollingOption(
                            selected = pollingMode == PollingMode.CONTINUOUS,
                            title = "Continuous (recommended)",
                            detail = "Every 30 s while driving, 5 min parked, 30 min asleep. Best trips, routes and drain data. Shows a status notification.",
                            onSelect = { pollingMode = PollingMode.CONTINUOUS }
                        )
                        PollingOption(
                            selected = pollingMode == PollingMode.BATTERY_SAVER,
                            title = "Battery saver",
                            detail = "Every 15 min, 30 s for a few minutes when a drive is detected. No notification; coarser trips, and Android may delay polls.",
                            onSelect = { pollingMode = PollingMode.BATTERY_SAVER }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val rate = homeRateText.toDecimalOrNull()?.takeIf { it >= 0f } ?: currentSettings.homeKwhRate
                            val mpg = gasMpgText.toDecimalOrNull()?.takeIf { it > 0f } ?: currentSettings.gasMpg
                            val gas = gasPriceText.toDecimalOrNull()?.takeIf { it >= 0f } ?: currentSettings.gasPrice
                            val usable = usableKwhText.toDecimalOrNull()?.takeIf { it in 20f..250f } ?: currentSettings.usableKwh
                            val homeLat = homeLatText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
                            val homeLon = homeLonText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                            val homeSet = homeLat != null && homeLon != null
                            onSaveSettings(
                                currentSettings.copy(
                                    vehicleName = vehicleName,
                                    vehicleModel = vehicleModel,
                                    homeKwhRate = rate,
                                    gasMpg = mpg,
                                    gasPrice = gas,
                                    usableKwh = usable,
                                    homeLat = if (homeSet) homeLat else null,
                                    homeLon = if (homeSet) homeLon else null,
                                    gasState = gasStateText.trim().uppercase().ifEmpty { currentSettings.gasState },
                                    gasMetro = gasMetroText.trim().ifEmpty { currentSettings.gasMetro },
                                    pollingMode = pollingMode
                                )
                            )
                            showSavedSnackbar = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_settings_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Configuration", fontWeight = FontWeight.Bold)
                    }

                    if (showSavedSnackbar) {
                        Text(
                            text = "Settings saved successfully!",
                            color = RivianGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Demo data reset (demo mode only)
            if (rivianSession == null) Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RivianCard),
                border = BorderStroke(1.dp, RivianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "DATA MANAGEMENT",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Reload the 10-day Venice/Disneyland/Newport fictional driving history, fast charges, and overnight drain events.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = onResetDemoData,
                        border = BorderStroke(1.dp, RivianAmber),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RivianAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_demo_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reload Fictional Demo Dataset", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // App footer info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Wattson for Rivian",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Gary watches your Rivian. Wattson knows it.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Android Edition • Open Source MIT",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = if (hint != null) { { Text(hint, color = TextTertiary) } } else null,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = RivianGreen,
            unfocusedBorderColor = RivianBorder
        ),
        modifier = modifier
    )
}

@Composable
private fun PollingOption(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = RivianGreen)
        )
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

/** Parses user-entered decimals, accepting a comma as the decimal separator. */
internal fun String.toDecimalOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()
