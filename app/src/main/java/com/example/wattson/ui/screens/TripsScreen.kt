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
import com.example.wattson.model.Trip
import com.example.wattson.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TripsScreen(
    trips: List<Trip>,
    onAddTrip: (start: String, end: String, miles: Float, avgMph: Float) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val totalMiles = trips.sumOf { it.miles.toDouble() }.toFloat()
    val totalKwh = trips.sumOf { it.kwhUsed.toDouble() }.toFloat()
    val avgEfficiency = if (totalKwh > 0) totalMiles / totalKwh else 0f
    val avgWhPerMi = if (totalMiles > 0) (totalKwh * 1000f) / totalMiles else 0f

    val filteredTrips = if (searchQuery.isBlank()) {
        trips
    } else {
        trips.filter {
            it.startLabel.contains(searchQuery, ignoreCase = true) ||
            it.endLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = RivianBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = RivianGreen,
                contentColor = RivianBlack,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_add_trip")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log Trip")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("trips_screen"),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Drive History",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "The record Rivian never kept. Per-trip mi/kWh and energy.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats summary card
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
                                Text("TOTAL MILES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${totalMiles.toInt()} mi", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("EFFICIENCY", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${String.format("%.2f", avgEfficiency)} mi/kWh", color = RivianGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ENERGY USED", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${totalKwh.toInt()} kWh", color = RivianAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AVG WH/MI", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${avgWhPerMi.toInt()}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search / Filter
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search places (e.g. Venice, Newport, LAX)", color = TextTertiary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RivianGreen,
                            unfocusedBorderColor = RivianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = RivianDarkSurface,
                            unfocusedContainerColor = RivianDarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trips_search_field")
                    )
                }
            }

            items(filteredTrips, key = { it.id }) { trip ->
                TripRowItem(trip = trip)
            }
        }
    }

    if (showAddDialog) {
        AddTripDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { start, end, miles, speed ->
                onAddTrip(start, end, miles, speed)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TripRowItem(trip: Trip) {
    val sdf = SimpleDateFormat("h:mm a", Locale.US)
    val startTime = sdf.format(Date(trip.startTs * 1000)).lowercase()
    val endTime = sdf.format(Date(trip.endTs * 1000)).lowercase()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RivianCard),
        border = BorderStroke(1.dp, RivianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("trip_item_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${trip.startLabel} → ${trip.endLabel}",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .background(RivianGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${String.format("%.2f", trip.miPerKwh)} mi/kWh",
                        color = RivianGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${trip.miles} mi",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(" • ", color = TextTertiary)
                    Text(
                        text = "${trip.kwhUsed} kWh",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(" • ", color = TextTertiary)
                    Text(
                        text = "${trip.whPerMi.toInt()} Wh/mi",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "$startTime – $endTime",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            if (trip.tempF != null || trip.avgMph > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    if (trip.avgMph > 0) {
                        Text(
                            text = "${trip.avgMph.toInt()} mph avg",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                    if (trip.tempF != null) {
                        Text(
                            text = " • ${trip.tempF.toInt()}°F ambient",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (start: String, end: String, miles: Float, avgMph: Float) -> Unit
) {
    var start by remember { mutableStateOf("Home") }
    var end by remember { mutableStateOf("") }
    var milesText by remember { mutableStateOf("12.5") }
    var speedText by remember { mutableStateOf("35") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log New Drive", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Starting Location") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("Destination Location") },
                    placeholder = { Text("e.g. Santa Monica Pier") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = milesText,
                    onValueChange = { milesText = it },
                    label = { Text("Distance (miles)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = speedText,
                    onValueChange = { speedText = it },
                    label = { Text("Average Speed (mph)") },
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
                    val miles = milesText.toFloatOrNull() ?: 10f
                    val speed = speedText.toFloatOrNull() ?: 30f
                    if (end.isNotBlank()) {
                        onConfirm(start, end, miles, speed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack)
            ) {
                Text("Save Drive")
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
