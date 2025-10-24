package com.jcb.passbook.presentation.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.jcb.passbook.R
import com.jcb.passbook.presentation.viewmodel.shared.RegistrationState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import timber.log.Timber

@Composable
fun RegistrationScreen(
    userViewModel: UserViewModel,
    onRegistrationSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val registrationState by userViewModel.registrationState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Biometric onboarding state
    var showBiometricOptIn by remember { mutableStateOf(false) }
    var pendingUserId by remember { mutableStateOf<Int?>(null) }
    var biometricAvailable by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    // Check biometric availability on first composition
    LaunchedEffect(Unit) {
        try {
            // Simple availability check without BiometricHelper for now
            biometricAvailable = activity != null
            Timber.d("Biometric availability checked: $biometricAvailable")
        } catch (e: Exception) {
            Timber.w(e, "Error checking biometric availability")
            biometricAvailable = false
        }
    }

    // Extract error message resource ID if present
    val errorMessageId = (registrationState as? RegistrationState.Error)?.messageId

    // Field-level error logic
    val usernameError = if (registrationState is RegistrationState.Error && username.isBlank()) errorMessageId else null
    val passwordError = if (registrationState is RegistrationState.Error && password.isBlank()) errorMessageId else null

    // Handle successful registration - show biometric opt-in if available
    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationState.Success) {
            // For now, we'll use a placeholder userId until your ViewModel is updated
            // You'll need to modify RegistrationState.Success to include userId
            val newUserId = 1 // TODO: Get actual userId from registrationState.userId
            pendingUserId = newUserId

            if (biometricAvailable) {
                // Show biometric opt-in dialog
                showBiometricOptIn = true
            } else {
                // No biometrics available, proceed directly
                onRegistrationSuccess()
                userViewModel.clearRegistrationState()
            }
        }
    }

    // Main registration form
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App title or logo space
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                if (registrationState is RegistrationState.Error) {
                    userViewModel.clearRegistrationState()
                }
            },
            label = { Text("Username") }, // Fallback text instead of stringResource
            modifier = Modifier.fillMaxWidth(),
            isError = usernameError != null,
            supportingText = {
                if (usernameError != null) {
                    Text(
                        text = "Username is required", // Fallback error message
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (registrationState is RegistrationState.Error) {
                    userViewModel.clearRegistrationState()
                }
            },
            label = { Text("Password") }, // Fallback text instead of stringResource
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = {
                if (passwordError != null) {
                    Text(
                        text = "Password is required", // Fallback error message
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // General error message
        if (registrationState is RegistrationState.Error && errorMessageId != null &&
            username.isNotBlank() && password.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Registration failed. Please try again.", // Fallback error message
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Biometric error display
        biometricError?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Register button
        Button(
            onClick = {
                if (username.isNotBlank() && password.isNotBlank()) {
                    userViewModel.register(username, password)
                }
            },
            enabled = registrationState !is RegistrationState.Loading &&
                    username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (registrationState is RegistrationState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Create Account")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Back button
        OutlinedButton(
            onClick = onBackClick,
            enabled = registrationState !is RegistrationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Login")
        }
    }

    // Biometric opt-in dialog
    if (showBiometricOptIn && pendingUserId != null) {
        BiometricOptInDialog(
            onEnableBiometric = {
                val userId = pendingUserId!!
                // Simplified biometric setup for now
                try {
                    // TODO: Implement actual biometric provisioning
                    // BiometricLoginManager.provisionAfterRegistration(context, userId) { success -> ... }

                    // For now, simulate success
                    Timber.d("Biometric setup initiated for user $userId")
                    showBiometricOptIn = false
                    onRegistrationSuccess()
                    userViewModel.clearRegistrationState()
                } catch (e: Exception) {
                    Timber.e(e, "Biometric setup failed")
                    biometricError = "Failed to set up biometric login. You can enable it later in settings."
                    showBiometricOptIn = false
                    onRegistrationSuccess()
                    userViewModel.clearRegistrationState()
                }
            },
            onSkip = {
                Timber.d("User skipped biometric setup")
                showBiometricOptIn = false
                onRegistrationSuccess()
                userViewModel.clearRegistrationState()
            },
            onDismiss = {
                // Allow dismissing by treating it as skip
                showBiometricOptIn = false
                onRegistrationSuccess()
                userViewModel.clearRegistrationState()
            }
        )
    }
}

@Composable
private fun BiometricOptInDialog(
    onEnableBiometric: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit = onSkip
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enable Biometric Login?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Use your fingerprint or face to sign in securely without entering your password.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Your biometric data stays on your device and is protected by hardware security.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEnableBiometric
            ) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip
            ) {
                Text("Not Now")
            }
        }
    )
}