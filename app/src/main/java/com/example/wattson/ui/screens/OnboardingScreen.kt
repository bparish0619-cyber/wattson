package com.example.wattson.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wattson.data.rivian.*
import com.example.wattson.ui.components.WattsonIcons
import com.example.wattson.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    apiClient: RivianApiClient,
    onSuccess: (email: String, uSess: String, access: String?, refresh: String?, vehicleId: String?, vehicleName: String?, vin: String?) -> Unit,
    onSkipToDemo: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var step by remember { mutableIntStateOf(1) } // 1 = Login, 2 = OTP, 3 = Vehicle Selection

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var otpToken by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }

    var userSessionToken by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf<String?>(null) }

    var vehiclesList by remember { mutableStateOf<List<RivianVehicle>>(emptyList()) }
    var selectedVehicle by remember { mutableStateOf<RivianVehicle?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RivianBlack)
            .padding(24.dp)
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(RivianSurfaceVariant)
                    .border(2.dp, RivianGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = WattsonIcons.Bolt,
                    contentDescription = null,
                    tint = RivianGreen,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Wattson",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = "Gary watches your Rivian. Wattson knows it.",
                color = RivianAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step 1: Login Form
            if (step == 1) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "LINK RIVIAN ACCOUNT",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Connect directly to Rivian's GraphQL service to automatically fetch trips, charging curves, and battery telemetry.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = null
                            },
                            label = { Text("Rivian Account Email") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RivianGreen,
                                unfocusedBorderColor = RivianBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_email_field")
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = { Text("Rivian Account Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                            trailingIcon = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(
                                        text = if (passwordVisible) "HIDE" else "SHOW",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RivianGreen,
                                unfocusedBorderColor = RivianBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_password_field")
                        )

                        // Privacy notice box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RivianSurfaceVariant, RoundedCornerShape(10.dp))
                                .border(1.dp, RivianBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = RivianGreen,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Your credentials are used exclusively to negotiate session tokens directly with Rivian's official endpoints. They remain stored strictly inside this app on your device.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = RivianRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter both email and password."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    when (val res = apiClient.login(email.trim(), password)) {
                                        is LoginResult.RequiresOtp -> {
                                            otpToken = res.otpToken
                                            isLoading = false
                                            step = 2
                                        }
                                        is LoginResult.Success -> {
                                            userSessionToken = res.userSessionToken
                                            accessToken = res.accessToken
                                            refreshToken = res.refreshToken
                                            // Fetch vehicles
                                            val list = apiClient.getVehicles(res.userSessionToken)
                                            isLoading = false
                                            if (list.isNotEmpty()) {
                                                vehiclesList = list
                                                selectedVehicle = list.first()
                                                step = 3
                                            } else {
                                                onSuccess(email.trim(), res.userSessionToken, res.accessToken, res.refreshToken, null, "My Rivian", null)
                                            }
                                        }
                                        is LoginResult.Error -> {
                                            isLoading = false
                                            errorMessage = res.message
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = RivianBlack, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Continue with Rivian Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Skip to Demo Option
                OutlinedButton(
                    onClick = onSkipToDemo,
                    border = BorderStroke(1.dp, RivianBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skip_demo_button")
                ) {
                    Text("Or Explore with Demo Vehicle (No Account Required)", fontSize = 12.sp)
                }
            }

            // Step 2: OTP Two-Factor Code
            if (step == 2) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "SECURITY VERIFICATION",
                            color = RivianAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Enter the 6-digit verification code sent by Rivian via SMS or your authenticator app to complete login.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = {
                                if (it.length <= 6) otpCode = it
                                errorMessage = null
                            },
                            label = { Text("6-Digit Code") },
                            placeholder = { Text("123456") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RivianGreen,
                                unfocusedBorderColor = RivianBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_otp_field")
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = RivianRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = {
                                if (otpCode.length < 4) {
                                    errorMessage = "Please enter the verification code."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    when (val res = apiClient.verifyOtp(email.trim(), otpToken, otpCode)) {
                                        is OtpResult.Success -> {
                                            userSessionToken = res.userSessionToken
                                            accessToken = res.accessToken
                                            refreshToken = res.refreshToken
                                            val list = apiClient.getVehicles(res.userSessionToken)
                                            isLoading = false
                                            if (list.isNotEmpty()) {
                                                vehiclesList = list
                                                selectedVehicle = list.first()
                                                step = 3
                                            } else {
                                                onSuccess(email.trim(), res.userSessionToken, res.accessToken, res.refreshToken, null, "My Rivian", null)
                                            }
                                        }
                                        is OtpResult.Error -> {
                                            isLoading = false
                                            errorMessage = res.message
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("verify_otp_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = RivianBlack, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Verify & Connect", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        TextButton(
                            onClick = { step = 1 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Login", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Step 3: Vehicle Selection
            if (step == 3) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RivianCard),
                    border = BorderStroke(1.dp, RivianGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "SELECT VEHICLE",
                            color = RivianGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Found ${vehiclesList.size} vehicle(s) associated with your Rivian account. Select the car you wish to track:",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        vehiclesList.forEach { v ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedVehicle?.id == v.id) RivianGreen.copy(alpha = 0.15f) else RivianDarkSurface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedVehicle?.id == v.id) RivianGreen else RivianBorder
                                ),
                                onClick = { selectedVehicle = v },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = v.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "${v.model} • VIN ending in ${v.vin.takeLast(6)}", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    if (selectedVehicle?.id == v.id) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = RivianGreen)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val v = selectedVehicle ?: vehiclesList.firstOrNull()
                                onSuccess(
                                    email.trim(),
                                    userSessionToken,
                                    accessToken,
                                    refreshToken,
                                    v?.id,
                                    v?.name ?: "My Rivian",
                                    v?.vin
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RivianGreen, contentColor = RivianBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("finish_onboarding_button")
                        ) {
                            Text("Launch Wattson Dashboard", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
