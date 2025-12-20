package com.jcb.passbook.presentation.ui.screens.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jcb.passbook.R
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel


/**
 * LoginScreen - User authentication UI with state-driven validation
 *
 * @param userViewModel Manages authentication state and user session
 * @param onLoginSuccess Callback with userId (Long) on successful authentication
 * @param onNavigateToRegister Callback to navigate to registration screen
 */
@Composable
fun LoginScreen(
    userViewModel: UserViewModel,
    onLoginSuccess: (userId: Long) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val authState by userViewModel.authState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Handle successful login - trigger navigation with userId
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val userId = (authState as AuthState.Success).userId
            onLoginSuccess(userId)
            userViewModel.clearAuthState()
        }
    }

    LoginScreenContent(
        username = username,
        onUsernameChange = {
            username = it
            if (authState is AuthState.Error) userViewModel.clearAuthState()
        },
        password = password,
        onPasswordChange = {
            password = it
            if (authState is AuthState.Error) userViewModel.clearAuthState()
        },
        authState = authState,
        onLoginClick = {
            userViewModel.login(username.trim(), password)
        },
        onRegisterClick = onNavigateToRegister
    )
}

@Composable
private fun LoginScreenContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    authState: AuthState,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    // Validation state
    val usernameError = when {
        authState is AuthState.Error && username.isBlank() -> R.string.error_empty_username
        else -> null
    }

    val passwordError = when {
        authState is AuthState.Error && password.isBlank() -> R.string.error_empty_password
        else -> null
    }

    val generalError = when {
        authState is AuthState.Error && username.isNotBlank() && password.isNotBlank() ->
            authState.messageId
        else -> null
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App branding
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Secure Password Manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Input fields
            UsernameField(
                value = username,
                onValueChange = onUsernameChange,
                errorMessage = usernameError,
                enabled = authState !is AuthState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordField(
                value = password,
                onValueChange = onPasswordChange,
                errorMessage = passwordError,
                enabled = authState !is AuthState.Loading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // General error message
            generalError?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action buttons
            AuthButtons(
                onLoginClick = onLoginClick,
                onRegisterClick = onRegisterClick,
                authState = authState,
                loginEnabled = username.isNotBlank() && password.isNotBlank()
            )

            // Loading indicator
            if (authState is AuthState.Loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes errorMessage: Int?,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.username)) },
        placeholder = { Text("Enter your username") },
        isError = errorMessage != null,
        supportingText = {
            errorMessage?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes errorMessage: Int?,
    enabled: Boolean
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.password)) },
        placeholder = { Text("Enter your password") },
        isError = errorMessage != null,
        supportingText = {
            errorMessage?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        visualTransformation = if (passwordVisible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible)
                        Icons.Filled.VisibilityOff
                    else
                        Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible)
                        "Hide password"
                    else
                        "Show password"
                )
            }
        },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AuthButtons(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    authState: AuthState,
    loginEnabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = loginEnabled && authState !is AuthState.Loading
        ) {
            Text(
                text = stringResource(R.string.login),
                style = MaterialTheme.typography.labelLarge
            )
        }

        OutlinedButton(
            onClick = onRegisterClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = authState !is AuthState.Loading
        ) {
            Text(
                text = stringResource(R.string.register),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
