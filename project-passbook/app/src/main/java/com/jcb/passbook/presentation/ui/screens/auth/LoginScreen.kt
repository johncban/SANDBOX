// @/app/src/main/java/com/jcb/passbook/presentation/ui/screens/auth/LoginScreen.kt

package com.jcb.passbook.presentation.ui.screens.auth

import android.content.res.Resources
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
    val authState by userViewModel.authState.collectAsState()
    val context = LocalContext.current

    // ✅ CRITICAL FIX: Safely get error message with proper fallback
    val errorMessage = remember(authState) {
        when (val state = authState) {
            is AuthState.Error -> {
                try {
                    // Attempt to get the string resource
                    context.getString(state.messageId)
                } catch (e: Resources.NotFoundException) {
                    // Log the specific resource ID that failed
                    Timber.tag(TAG).e(
                        e,
                        "String resource not found for ID: ${state.messageId} (0x${Integer.toHexString(state.messageId)})"
                    )
                    "Authentication failed. Please check your credentials."
                } catch (e: Exception) {
                    // Catch any other exceptions
                    Timber.tag(TAG).e(
                        e,
                        "Unexpected error loading string resource: ${state.messageId}"
                    )
                    "Login failed. Please try again."
                }
            }
            else -> null
        }
    }

    // ✅ Input validation error messages
    val usernameError = if (authState is AuthState.Error && username.isBlank()) {
        "Username cannot be empty"
    } else null

    val passwordError = if (authState is AuthState.Error && password.isBlank()) {
        "Password cannot be empty"
    } else null

    // ✅ Enhanced authentication state handling
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                val successUserId = state.userId
                Timber.tag(TAG).i("✓ Login successful, userId: $successUserId")

                if (successUserId != -1L) {
                    Timber.tag(TAG).i("Setting ItemViewModel userId to: $successUserId")
                    itemViewModel.setUserId(successUserId)

                    // Verify userId was set correctly
                    delay(150)
                    val verifiedUserId = itemViewModel.userId.value

                    if (verifiedUserId == successUserId) {
                        Timber.tag(TAG).i("✓ ItemViewModel userId verified: $verifiedUserId")
                        onLoginSuccess()
                        userViewModel.clearAuthState()
                    } else {
                        // Retry once
                        Timber.tag(TAG).w(
                            "UserId verification failed! Expected: $successUserId, Got: $verifiedUserId. Retrying..."
                        )
                        itemViewModel.setUserId(successUserId)
                        delay(100)

                        val retryUserId = itemViewModel.userId.value
                        if (retryUserId == successUserId) {
                            Timber.tag(TAG).i("✓ ItemViewModel userId set on retry: $retryUserId")
                            onLoginSuccess()
                            userViewModel.clearAuthState()
                        } else {
                            Timber.tag(TAG).e(
                                "CRITICAL: UserId still not set after retry! Expected: $successUserId, Got: $retryUserId"
                            )
                            // Navigate anyway to prevent deadlock
                            onLoginSuccess()
                            userViewModel.clearAuthState()
                        }
                    }
                } else {
                    Timber.tag(TAG).e("Invalid userId received: -1")
                }
            }

            is AuthState.Error -> {
                // Log detailed error information
                Timber.tag(TAG).e(
                    "Login failed with resource ID: ${state.messageId} (0x${Integer.toHexString(state.messageId)}). " +
                            "Error message: $errorMessage"
                )
            }

            is AuthState.Loading -> {
                Timber.tag(TAG).d("Authentication in progress...")
            }

            is AuthState.Idle -> {
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
                if (authState is AuthState.Error) {
                    userViewModel.clearAuthState()
                }
            },
            label = { Text(stringResource(R.string.username)) },
            modifier = Modifier.fillMaxWidth(),
            isError = usernameError != null,
            supportingText = usernameError?.let { { Text(it) } },
            singleLine = true,
            enabled = authState !is AuthState.Loading
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (authState is AuthState.Error) {
                    userViewModel.clearAuthState()
                }
            },
            label = { Text(stringResource(R.string.password)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            singleLine = true,
            enabled = authState !is AuthState.Loading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ✅ FIXED: Display error message with proper validation
        if (authState is AuthState.Error && errorMessage != null) {
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
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = {
                // ✅ Enhanced validation before login
                when {
                    username.isBlank() -> {
                        Timber.tag(TAG).w("Login attempt with blank username")
                    }
                    password.isBlank() -> {
                        Timber.tag(TAG).w("Login attempt with blank password")
                    }
                    else -> {
                        Timber.tag(TAG).d("Login button clicked for username: '$username'")
                        userViewModel.login(username, password)
                    }
                }
            },
            enabled = authState !is AuthState.Loading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Authenticating...")
            } else {
                Text(stringResource(R.string.login))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToRegister,
            enabled = authState !is AuthState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.register))
        }

        // ✅ Debug info (remove in production)
        if (authState is AuthState.Error) {
            Text(
                text = "Debug: Resource ID = ${(authState as AuthState.Error).messageId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
