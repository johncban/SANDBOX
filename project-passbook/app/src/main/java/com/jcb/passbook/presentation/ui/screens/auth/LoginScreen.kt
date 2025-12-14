package com.jcb.passbook.presentation.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.security.crypto.SessionManager
import timber.log.Timber
import javax.inject.Inject

/**
 * ✅ FIXED: LoginScreen with proper SessionManager injection via Hilt
 *
 * Fixes applied:
 * - BUG-002: startSession() now called without parameters
 * - BUG-003: Removed SessionResult handling (doesn't exist)
 * - BUG-007: SessionManager injected via Hilt @Inject
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (Long) -> Unit,
    onNavigateToRegister: () -> Unit,
    userViewModel: UserViewModel = hiltViewModel(),
    sessionManager: SessionManager = hiltViewModel(),  // ✅ Injected via Hilt
    viewModel: UserViewModel = viewModel()
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Collect auth state
    val authState by userViewModel.authState.collectAsStateWithLifecycle()

    /**
     * ✅ FIXED (BUG-002, BUG-003): Proper session initialization
     * - startSession() is void, no return value
     * - No SessionResult handling needed
     * - Simple success callback flow
     */
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                Timber.d("✅ Login successful for user: ${state.userId}")

                // ✅ Start session (void function, no parameters)
                try {
                    sessionManager.startSession()
                    Timber.i("✅ Session started successfully")

                    // Navigate to home after session starts
                    onLoginSuccess(state.userId)
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to start session")
                    // Session start failure handling could be added here
                }
            }
            else -> { /* No action needed */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ========== LOGO ==========
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Welcome to PassBook",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Secure Password Manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ========== USERNAME FIELD ==========
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    userViewModel.clearAuthState()
                },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                enabled = authState !is AuthState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ========== PASSWORD FIELD ==========
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    userViewModel.clearAuthState()
                },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isNotBlank() && password.isNotBlank()) {
                            userViewModel.login(username, password)
                        }
                    }
                ),
                enabled = authState !is AuthState.Loading
            )

            // ========== ERROR MESSAGES ==========
            when (val state = authState) {
                is AuthState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========== LOGIN BUTTON ==========
            Button(
                onClick = {
                    userViewModel.login(username, password)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = username.isNotBlank() && password.isNotBlank() && authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== REGISTER LINK ==========
            TextButton(
                onClick = onNavigateToRegister,
                enabled = authState !is AuthState.Loading
            ) {
                Text("Don't have an account? Register")
            }
        }
    }
}
