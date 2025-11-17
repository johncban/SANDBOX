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
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel

@Composable
fun LoginScreen(
    userViewModel: UserViewModel,
    itemViewModel: ItemViewModel,
    onLoginSuccess: (Int) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val authState by userViewModel.authState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Handle successful login
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val userId = (authState as AuthState.Success).userId
            itemViewModel.setUserId(userId)
            userViewModel.setUserId(userId)
            onLoginSuccess(userId.toInt())
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
        onLoginClick = { userViewModel.login(username, password) },
        onRegisterClick = onNavigateToRegister
    )
}

@Composable
fun LoginScreenContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    authState: AuthState,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    // Error message extraction
    val usernameError = when {
        authState is AuthState.Error && username.isBlank() -> R.string.error_empty_username
        else -> null
    }
    val passwordError = when {
        authState is AuthState.Error && password.isBlank() -> R.string.error_empty_password
        else -> null
    }
    val generalError = when {
        authState is AuthState.Error && username.isNotBlank() && password.isNotBlank() -> authState.messageId
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UsernameField(username, onUsernameChange, usernameError)
        Spacer(modifier = Modifier.height(8.dp))
        PasswordField(password, onPasswordChange, passwordError)
        Spacer(modifier = Modifier.height(16.dp))
        generalError?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        AuthButtons(onLoginClick, onRegisterClick, authState)
    }
}

@Composable
fun UsernameField(value: String, onValueChange: (String) -> Unit, @StringRes errorMessage: Int?) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it) },
        label = { Text(stringResource(R.string.username)) },
        isError = errorMessage != null,
        supportingText = {
            errorMessage?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, @StringRes errorMessage: Int?) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it) },
        label = { Text(stringResource(R.string.password)) },
        isError = errorMessage != null,
        supportingText = {
            errorMessage?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, contentDescription = "Toggle password visibility")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun AuthButtons(onLoginClick: () -> Unit, onRegisterClick: () -> Unit, authState: AuthState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onLoginClick,
            modifier = Modifier.weight(1f),
            enabled = authState !is AuthState.Loading
        ) {
            Text(stringResource(R.string.login))
        }
        Button(
            onClick = onRegisterClick,
            modifier = Modifier.weight(1f),
            enabled = authState !is AuthState.Loading
        ) {
            Text(stringResource(R.string.register))
        }
    }
}
