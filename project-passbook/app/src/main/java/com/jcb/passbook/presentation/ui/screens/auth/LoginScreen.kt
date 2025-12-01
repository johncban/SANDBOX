// @/app/src/main/java/com/jcb/passbook/presentation/ui/screens/auth/LoginScreen.kt

package com.jcb.passbook.presentation.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcb.passbook.R
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

private const val TAG = "LoginScreen"

@Composable
fun LoginScreen(
    userViewModel: UserViewModel,
    itemViewModel: ItemViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val authState by userViewModel.authState.collectAsState()

    // ✅ FIXED: Handle error messages properly
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                val userId = state.userId
                Timber.tag(TAG).i("Login successful, userId: $userId")
                Timber.tag(TAG).i("Setting ItemViewModel userId to: $userId")

                itemViewModel.setUserId(userId)

                // Verify userId was set correctly
                delay(100)
                val verifiedUserId = itemViewModel.userId.value
                Timber.tag(TAG).i("ItemViewModel userId verified: $verifiedUserId")

                if (verifiedUserId == userId) {
                    userViewModel.clearAuthState()
                    onLoginSuccess()
                } else {
                    Timber.tag(TAG).e("Failed to set ItemViewModel userId correctly")
                    errorMessage = "Internal error: Failed to initialize session"
                    showError = true
                }
            }
            is AuthState.Error -> {
                // ✅ FIXED: Directly use error message from state (no resource lookup)
                errorMessage = state.message
                showError = true
                Timber.tag(TAG).e("Login failed: $errorMessage")
            }
            is AuthState.Loading -> {
                showError = false
                Timber.tag(TAG).d("Authentication in progress...")
            }
            is AuthState.Idle -> {
                showError = false
                Timber.tag(TAG).d("Auth state: Idle")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PassBook Login",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                if (showError) {
                    showError = false
                    userViewModel.clearAuthState()
                }
            },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            enabled = authState !is AuthState.Loading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (showError) {
                    showError = false
                    userViewModel.clearAuthState()
                }
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (username.isNotBlank() && password.isNotBlank()) {
                        Timber.tag(TAG).d("Login button clicked for username: $username")
                        userViewModel.login(username, password)
                    }
                }
            ),
            enabled = authState !is AuthState.Loading
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ✅ FIXED: Display error message
        if (showError && errorMessage.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Button(
            onClick = {
                when {
                    username.isBlank() -> {
                        errorMessage = "Please enter username"
                        showError = true
                        Timber.tag(TAG).w("Login attempt with blank username")
                    }
                    password.isBlank() -> {
                        errorMessage = "Please enter password"
                        showError = true
                        Timber.tag(TAG).w("Login attempt with blank password")
                    }
                    else -> {
                        Timber.tag(TAG).d("Login button clicked for username: $username")
                        userViewModel.login(username, password)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthState.Loading
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Authenticating...")
            } else {
                Text("Login")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToRegister,
            enabled = authState !is AuthState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }
    }
}
