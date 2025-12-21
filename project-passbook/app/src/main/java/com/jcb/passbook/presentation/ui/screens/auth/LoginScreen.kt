package com.jcb.passbook.presentation.ui.screens.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jcb.passbook.R
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.ui.components.AccessiblePasswordField

/**
 * LoginScreen - User authentication UI with state-driven validation
 *
 * Refactored with:
 * - AccessiblePasswordField for enhanced accessibility
 * - Responsive layout support
 * - Improved error handling and user feedback
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
            // App branding section
            AppBranding()

            Spacer(modifier = Modifier.height(48.dp))

            // Input fields section
            InputFieldsSection(
                username = username,
                onUsernameChange = onUsernameChange,
                usernameError = usernameError,
                password = password,
                onPasswordChange = onPasswordChange,
                passwordError = passwordError,
                enabled = authState !is AuthState.Loading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // General error message display
            generalError?.let {
                ErrorCard(errorMessageId = it)
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
private fun AppBranding() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Secure Password Manager",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InputFieldsSection(
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameError: Int?,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordError: Int?,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UsernameField(
            value = username,
            onValueChange = onUsernameChange,
            errorMessage = usernameError,
            enabled = enabled
        )

        PasswordField(
            value = password,
            onValueChange = onPasswordChange,
            errorMessage = passwordError,
            enabled = enabled
        )
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
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null
            )
        },
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
    AccessiblePasswordField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.password),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        isError = errorMessage != null,
        supportingText = {
            errorMessage?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
private fun ErrorCard(
    @StringRes errorMessageId: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(errorMessageId),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
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
