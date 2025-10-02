package com.jcb.passbook.composables.firstscreen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcb.passbook.R
import com.jcb.passbook.viewmodel.RegistrationState
import com.jcb.passbook.viewmodel.UserViewModel

@Composable
fun RegistrationScreen(
    userViewModel: UserViewModel,
    onRegistrationSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val registrationState by userViewModel.registrationState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Extract error message resource ID if present
    val errorMessageId = (registrationState as? RegistrationState.Error)?.messageId

    // Field-level error logic
    val usernameError = if (registrationState is RegistrationState.Error && username.isBlank()) errorMessageId else null
    val passwordError = if (registrationState is RegistrationState.Error && password.isBlank()) errorMessageId else null

    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationState.Success) {
            onRegistrationSuccess()
            userViewModel.clearRegistrationState()
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
                if (registrationState is RegistrationState.Error) userViewModel.clearRegistrationState()
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
                if (registrationState is RegistrationState.Error) userViewModel.clearRegistrationState()
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
        if (registrationState is RegistrationState.Error && errorMessageId != null && username.isNotBlank() && password.isNotBlank()) {
            Text(
                text = stringResource(errorMessageId),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Button(
            onClick = { userViewModel.register(username, password) },
            enabled = registrationState !is RegistrationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.register))
        }
        OutlinedButton(
            onClick = onBackClick,
            enabled = registrationState !is RegistrationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back))
        }
    }
}
