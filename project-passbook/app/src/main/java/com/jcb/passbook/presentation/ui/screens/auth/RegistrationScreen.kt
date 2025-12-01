// @/app/src/main/java/com/jcb/passbook/presentation/ui/screens/auth/RegistrationScreen.kt

package com.jcb.passbook.presentation.ui.screens.auth

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcb.passbook.R
import com.jcb.passbook.presentation.viewmodel.shared.RegistrationState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import kotlinx.coroutines.delay

private const val TAG = "RegistrationScreen"

@Composable
fun RegistrationScreen(
    userViewModel: UserViewModel,
    itemViewModel: ItemViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val registrationState by userViewModel.registrationState.collectAsState()
    val context = LocalContext.current

    // ✅ FIXED: Changed from messageId to message property
    val errorMessage = remember(registrationState) {
        when (val state = registrationState) {
            is RegistrationState.Error -> {
                // Use the message string directly instead of trying to resolve resource ID
                state.message ?: "Registration failed. Please try again."
            }
            else -> null
        }
    }

    val usernameError = if (registrationState is RegistrationState.Error && username.isBlank()) {
        errorMessage
    } else null

    val passwordError = if (registrationState is RegistrationState.Error && password.isBlank()) {
        errorMessage
    } else null

    LaunchedEffect(registrationState) {
        when (registrationState) {
            is RegistrationState.Success -> {
                val userId = userViewModel.userId.value
                Log.i(TAG, "Registration successful, userId from UserViewModel: $userId")

                if (userId != -1L) {
                    Log.i(TAG, "Setting ItemViewModel userId to: $userId")
                    itemViewModel.setUserId(userId)
                    delay(150)

                    val verifiedUserId = itemViewModel.userId.value
                    if (verifiedUserId == userId) {
                        Log.i(TAG, "✓ ItemViewModel userId verified: $verifiedUserId")
                        onRegisterSuccess()
                        userViewModel.clearRegistrationState()
                    } else {
                        Log.e(TAG, "UserId verification failed! Expected: $userId, Got: $verifiedUserId")
                        // Retry once
                        itemViewModel.setUserId(userId)
                        delay(100)
                        val retryUserId = itemViewModel.userId.value
                        if (retryUserId == userId) {
                            Log.i(TAG, "✓ ItemViewModel userId set on retry: $retryUserId")
                            onRegisterSuccess()
                            userViewModel.clearRegistrationState()
                        } else {
                            Log.e(TAG, "CRITICAL: UserId still not set after retry!")
                            // Proceed anyway to avoid blocking user
                            onRegisterSuccess()
                            userViewModel.clearRegistrationState()
                        }
                    }
                } else {
                    Log.e(TAG, "Registration succeeded but userId is still -1L")
                }
            }
            is RegistrationState.Error -> {
                val error = registrationState as RegistrationState.Error
                Log.e(TAG, "Registration failed: ${error.message}")
            }
            else -> {
                Log.d(TAG, "Registration state: $registrationState")
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
            label = { Text(stringResource(R.string.username)) },
            modifier = Modifier.fillMaxWidth(),
            isError = usernameError != null,
            supportingText = usernameError?.let { { Text(it) } },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (registrationState is RegistrationState.Error) {
                    userViewModel.clearRegistrationState()
                }
            },
            label = { Text(stringResource(R.string.password)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show error message if there is one and fields are filled
        if (registrationState is RegistrationState.Error &&
            errorMessage != null &&
            username.isNotBlank() &&
            password.isNotBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                if (username.isNotBlank() && password.isNotBlank()) {
                    Log.d(TAG, "Register button clicked for username: $username")
                    userViewModel.register(username, password)
                }
            },
            enabled = registrationState !is RegistrationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (registrationState is RegistrationState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.register))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToLogin,
            enabled = registrationState !is RegistrationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back))
        }
    }
}
