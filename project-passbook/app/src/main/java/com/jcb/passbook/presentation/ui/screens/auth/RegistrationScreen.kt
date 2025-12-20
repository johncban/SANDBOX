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
import com.jcb.passbook.presentation.viewmodel.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.shared.RegistrationState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel


@Composable
fun RegistrationScreen(  // ✅ Name is correct
    userViewModel: UserViewModel,
    itemViewModel: ItemViewModel,  // ✅ ADDED: Missing parameter
    onRegisterSuccess: () -> Unit,  // ✅ FIXED: Renamed from onRegistrationSuccess
    onNavigateToLogin: () -> Unit  // ✅ FIXED: Renamed from onBackClick
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
            onRegisterSuccess()
            userViewModel.clearRegistrationState()
        }
    } // ✅ FIXED: Added missing closing brace

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
            } // ✅ FIXED: Added missing closing brace
        ) // ✅ FIXED: Added missing closing brace

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
            } // ✅ FIXED: Added missing closing brace
        ) // ✅ FIXED: Added missing closing brace

        Spacer(modifier = Modifier.height(16.dp))

        if (registrationState is RegistrationState.Error && errorMessageId != null && username.isNotBlank() && password.isNotBlank()) {
            Text(
                text = stringResource(errorMessageId),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } // ✅ FIXED: Added missing closing brace

        Button(
            onClick = { userViewModel.register(username, password) },
            enabled = registrationState !is RegistrationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.register))
        } // ✅ FIXED: Added missing closing brace

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToLogin,
            enabled = registrationState !is RegistrationState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back))
        } // ✅ FIXED: Added missing closing brace
    } // ✅ FIXED: Added missing closing brace for Column
} // ✅ FIXED: Added missing closing brace for RegistrationScreen function
