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

@Composable
fun LoginScreen(
    userViewModel: UserViewModel,
    itemViewModel: ItemViewModel, // ✅ ENSURE this parameter exists
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val authState by userViewModel.authState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val errorMessageId = (authState as? AuthState.Error)?.messageId

    val usernameError = if (authState is AuthState.Error && username.isBlank()) errorMessageId else null
    val passwordError = if (authState is AuthState.Error && password.isBlank()) errorMessageId else null

    // ✅ FIX: Set ItemViewModel userId when login succeeds
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val userId = (authState as AuthState.Success).userId
            itemViewModel.setUserId(userId) // ✅ CRITICAL FIX
            onLoginSuccess()
            userViewModel.clearAuthState()
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
            }
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
            }
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
            onClick = { userViewModel.login(username, password) },
            enabled = authState !is AuthState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.login))
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
