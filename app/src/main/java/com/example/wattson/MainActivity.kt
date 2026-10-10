package com.example.wattson

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.data.WattsonRepository
import com.example.wattson.ui.components.WattsonIcons
import com.example.wattson.ui.screens.*
import com.example.wattson.ui.theme.*

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Overview", Icons.Default.Home),
    TRIPS("Drives", WattsonIcons.Route),
    CHARGING("Charging", WattsonIcons.EvStation),
    SAVINGS("Savings", WattsonIcons.Savings),
    DRAIN("Battery", WattsonIcons.BatteryCharging)
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: WattsonRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WattsonRepository.getInstance(applicationContext)
        enableEdgeToEdge()

        setContent {
            WattsonTheme {
                MainApp(repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(repository: WattsonRepository) {
    val isOnboardingCompleted by repository.isOnboardingCompleted.collectAsState()
    val session by repository.session.collectAsState()
    val isSyncing by repository.isSyncing.collectAsState()

    if (!isOnboardingCompleted) {
        OnboardingScreen(
            apiClient = repository.apiClient,
            onSuccess = { email, uSess, access, refresh, vehicleId, vehicleName, vin ->
                repository.saveRivianLogin(
                    email = email,
                    userSessionToken = uSess,
                    accessToken = access,
                    refreshToken = refresh,
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    vehicleVin = vin
                )
            },
            onSkipToDemo = {
                repository.completeOnboardingAsDemo()
            }
        )
        return
    }

    var currentDestination by rememberSaveable { mutableStateOf(NavDestination.DASHBOARD) }
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }
    var showQuickAddTripDialog by rememberSaveable { mutableStateOf(false) }

    val vehicle by repository.vehicle.collectAsState()
    val trips by repository.trips.collectAsState()
    val charges by repository.charges.collectAsState()
    val drains by repository.drains.collectAsState()
    val packHealth by repository.packHealth.collectAsState()
    val batteryTrend by repository.batteryTrend.collectAsState()
    val settings by repository.settings.collectAsState()

    // Lifetime efficiency
    val totalMiles = trips.sumOf { it.miles.toDouble() }.toFloat()
    val totalKwh = trips.sumOf { it.kwhUsed.toDouble() }.toFloat()
    val lifetimeEfficiency = if (totalKwh > 0) totalMiles / totalKwh else 3.1f

    // Savings breakdown
    val savingsRows = remember(charges, settings, lifetimeEfficiency) {
        repository.calculateFuelSavings(charges, settings, lifetimeEfficiency)
    }
    val savingsTotal = savingsRows.sumOf { it.saved.toDouble() }.toFloat()

    // Back button handling
    BackHandler(enabled = showSettingsScreen || currentDestination != NavDestination.DASHBOARD) {
        if (showSettingsScreen) {
            showSettingsScreen = false
        } else {
            currentDestination = NavDestination.DASHBOARD
        }
    }

    Scaffold(
        containerColor = RivianBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RivianSurfaceVariant)
                                .border(1.dp, RivianGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = WattsonIcons.Bolt,
                                contentDescription = null,
                                tint = RivianGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Wattson",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (session != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(RivianGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "GQL",
                                            color = RivianGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = vehicle.name,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(
                            onClick = { repository.refreshLiveTelemetry() },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = RivianGreen, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync from Rivian",
                                    tint = RivianGreen
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showSettingsScreen = !showSettingsScreen },
                        modifier = Modifier.testTag("top_bar_settings_button")
                    ) {
                        Icon(
                            imageVector = if (showSettingsScreen) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (showSettingsScreen) RivianGreen else TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RivianBlack,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = RivianDarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination && !showSettingsScreen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            showSettingsScreen = false
                            currentDestination = destination
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RivianBlack,
                            selectedTextColor = RivianGreen,
                            indicatorColor = RivianGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showSettingsScreen) {
                SettingsScreen(
                    currentSettings = settings,
                    rivianSession = session,
                    isSyncing = isSyncing,
                    onSaveSettings = {
                        repository.updateSettings(it)
                        showSettingsScreen = false
                    },
                    onSyncRivian = {
                        repository.refreshLiveTelemetry()
                    },
                    onUnlinkRivian = {
                        repository.logout()
                        showSettingsScreen = false
                    },
                    onResetDemoData = {
                        repository.resetToDemoData()
                        showSettingsScreen = false
                    }
                )
            } else {
                when (currentDestination) {
                    NavDestination.DASHBOARD -> DashboardScreen(
                        vehicle = vehicle,
                        trips = trips,
                        charges = charges,
                        drains = drains,
                        savingsTotal = savingsTotal,
                        lifetimeEfficiency = lifetimeEfficiency,
                        gasMpg = settings.gasMpg,
                        isDemo = session == null,
                        onNavigateToTrips = { currentDestination = NavDestination.TRIPS },
                        onNavigateToCharging = { currentDestination = NavDestination.CHARGING },
                        onNavigateToSavings = { currentDestination = NavDestination.SAVINGS },
                        onNavigateToDrain = { currentDestination = NavDestination.DRAIN },
                        onToggleLiveCharge = { repository.toggleLiveCharge(it) },
                        onQuickAddTrip = { showQuickAddTripDialog = true }
                    )

                    NavDestination.TRIPS -> TripsScreen(
                        trips = trips,
                        onAddTrip = { start, end, miles, speed ->
                            repository.addTrip(start, end, miles, speed)
                        }
                    )

                    NavDestination.CHARGING -> ChargingScreen(
                        vehicle = vehicle,
                        charges = charges,
                        settings = settings,
                        onToggleLiveCharge = { repository.toggleLiveCharge(it) },
                        onUpdateChargeCost = { id, cost ->
                            repository.updateChargeCost(id, cost)
                        },
                        onAddChargingSession = { sStart, sEnd, kwh, type, label, cost ->
                            repository.addChargingSession(sStart, sEnd, kwh, type, label, cost)
                        }
                    )

                    NavDestination.SAVINGS -> SavingsScreen(
                        savingsRows = savingsRows,
                        savingsTotal = savingsTotal,
                        settings = settings,
                        lifetimeEfficiency = lifetimeEfficiency,
                        onNavigateToSettings = { showSettingsScreen = true }
                    )

                    NavDestination.DRAIN -> DrainScreen(
                        drains = drains,
                        batteryTrend = batteryTrend,
                        packHealth = packHealth
                    )
                }
            }
        }
    }

    if (showQuickAddTripDialog) {
        AddTripDialog(
            onDismiss = { showQuickAddTripDialog = false },
            onConfirm = { start, end, miles, speed ->
                repository.addTrip(start, end, miles, speed)
                showQuickAddTripDialog = false
            }
        )
    }
}
