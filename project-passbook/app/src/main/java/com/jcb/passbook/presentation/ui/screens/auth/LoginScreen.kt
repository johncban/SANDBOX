// @/app/src/main/java/com/jcb/passbook/presentation/ui/screens/auth/LoginScreen.kt

package com.jcb.passbook.presentation.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // ✅ CRITICAL FIX: Use remember to prevent recomposition on every keystroke
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val authState by userViewModel.authState.collectAsState()

    val errorMessageId = (authState as? AuthState.Error)?.messageId

    val usernameError = if (authState is AuthState.Error && username.isBlank()) errorMessageId else null
    val passwordError = if (authState is AuthState.Error && password.isBlank()) errorMessageId else null

    // ✅ FIXED: Only handle auth state changes, not field changes
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                val successUserId = state.userId
                Timber.tag(TAG).i("Login successful, userId: $successUserId")

                if (successUserId != -1L) {
                    Timber.tag(TAG).i("Setting ItemViewModel userId to: $successUserId")
                    itemViewModel.setUserId(successUserId)

                    delay(150)

                    val verifiedUserId = itemViewModel.userId.value
                    if (verifiedUserId == successUserId) {
                        Timber.tag(TAG).i("✓ ItemViewModel userId verified: $verifiedUserId")
                        onLoginSuccess()
                        userViewModel.clearAuthState()
                    } else {
                        Timber.tag(TAG).e("UserId verification failed! Expected: $successUserId, Got: $verifiedUserId")
                        itemViewModel.setUserId(successUserId)
                        delay(100)
                        val retryUserId = itemViewModel.userId.value
                        if (retryUserId == successUserId) {
                            Timber.tag(TAG).i("✓ ItemViewModel userId set on retry: $retryUserId")
                            onLoginSuccess()
                            userViewModel.clearAuthState()
                        } else {
                            Timber.tag(TAG).e("CRITICAL: UserId still not set after retry!")
                            onLoginSuccess()
                            userViewModel.clearAuthState()
                        }
                    }
                }
            }
            is AuthState.Error -> {
                Timber.tag(TAG).e("Login failed: ${state.messageId}")
            }
            else -> {
                Timber.tag(TAG).d("Auth state: $authState")
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
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                if (authState is AuthState.Error) userViewModel.clearAuthState()
            },
            label = { Text(stringResource(R.string.username)) },
            modifier = Modifier.fillMaxWidth(),
            isError = usernameError != null,
            supportingText = {
                if (usernameError != null) {
                    Text(stringResource(usernameError))
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (authState is AuthState.Error) userViewModel.clearAuthState()
            },
            label = { Text(stringResource(R.string.password)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = {
                if (passwordError != null) {
                    Text(stringResource(passwordError))
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (authState is AuthState.Error && errorMessageId != null && username.isNotBlank() && password.isNotBlank()) {
            Text(
                text = stringResource(errorMessageId),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                Timber.tag(TAG).d("Login button clicked for username: $username")
                userViewModel.login(username, password)
            },
            enabled = authState !is AuthState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
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
    }
}
