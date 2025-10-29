package com.jcb.passbook.presentation.ui.screens.auth

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.jcb.passbook.presentation.viewmodel.auth.UnlockViewModel
import com.jcb.passbook.security.biometric.BiometricHelper // updated package

@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(
    unlockViewModel: UnlockViewModel,
    activity: FragmentActivity,
    onUnlockSuccess: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by unlockViewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val passwordFocusRequester = remember { FocusRequester() }

    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showPasswordFallback by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlockSuccess()
    }

    LaunchedEffect(uiState.biometricAvailable) {
        if (uiState.biometricAvailable && !uiState.biometricPrompted && !showPasswordFallback) {
            promptBiometric(unlockViewModel, activity)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Vault Locked",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("Unlock Your Vault", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your vault is locked for security. Please authenticate to continue.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        if (uiState.biometricAvailable && !showPasswordFallback) {
            Button(
                onClick = { promptBiometric(unlockViewModel, activity) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock with Biometric")
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showPasswordFallback = true }, enabled = !uiState.isLoading) {
                Text("Use Password Instead")
            }
        }

        if (!uiState.biometricAvailable || showPasswordFallback) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Master Password") },
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    unlockWithPassword(unlockViewModel, password) { password = "" }
                }),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                enabled = !uiState.isLoading,
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    keyboardController?.hide()
                    unlockWithPassword(unlockViewModel, password) { password = "" }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && password.isNotBlank()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Unlock")
            }
            if (uiState.biometricAvailable) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showPasswordFallback = false }, enabled = !uiState.isLoading) {
                    Text("Use Biometric Instead")
                }
            }
        }

        uiState.errorMessage?.let { error ->
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
            Text("Exit App")
        }
    }

    LaunchedEffect(showPasswordFallback) {
        if (showPasswordFallback) passwordFocusRequester.requestFocus()
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun promptBiometric(
    unlockViewModel: UnlockViewModel,
    activity: FragmentActivity
) {
    val biometricPrompt = BiometricHelper.buildPrompt(
        activity = activity,
        title = "Unlock Vault",
        subtitle = "Use your biometric credential to unlock your password vault",
        negative = "Cancel",
        onSuccess = { result ->
            Log.d("UnlockScreen", "Biometric authentication successful")
            unlockViewModel.unlockWithBiometric(result.cryptoObject?.cipher)
        },
        onError = { errorCode, errString ->
            Log.w("UnlockScreen", "Biometric error: $errorCode - $errString")
            unlockViewModel.handleBiometricError(errorCode, errString.toString())
        }
    )

    val promptInfo = BiometricHelper.buildPromptInfo(
        title = "Unlock Vault",
        subtitle = "Use your biometric credential to unlock your password vault",
        negative = "Use Password"
    )

    // Prefer cryptographic prompt; fallback to non-crypto prompt if cipher cannot be created
    unlockViewModel.createBiometricCipher()?.let { cipher ->
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } ?: biometricPrompt.authenticate(promptInfo)
}

private fun unlockWithPassword(
    unlockViewModel: UnlockViewModel,
    password: String,
    clearPassword: () -> Unit
) {
    unlockViewModel.unlockWithPassword(password)
    clearPassword()
}
