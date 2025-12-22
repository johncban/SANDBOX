package com.jcb.passbook.presentation.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue  // ✅ ADD THIS
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jcb.passbook.R
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.ui.responsive.isLandscape
import com.jcb.passbook.presentation.ui.responsive.rememberScreenInfo
import com.jcb.passbook.presentation.ui.theme.getResponsiveDimensions

@Composable
fun LoginScreen(
    userViewModel: UserViewModel,
    onLoginSuccess: (userId: Long) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val authState by userViewModel.authState.collectAsState()
    val isBiometricAvailable by userViewModel.isBiometricAvailable.collectAsState()

    // ✅ FIX: Use TextFieldValue instead of String
    var username by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var passwordVisible by remember { mutableStateOf(false) }

    val screenInfo = rememberScreenInfo()
    val isLandscapeMode = isLandscape()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val userId = (authState as AuthState.Success).userId
            onLoginSuccess(userId)
            userViewModel.clearAuthState()
        }
    }

    if (isLandscapeMode && screenInfo.width < 840) {
        LoginScreenHorizontal(
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            passwordVisible = passwordVisible,
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            authState = authState,
            isBiometricAvailable = isBiometricAvailable,
            onLoginClick = { userViewModel.login(username.text.trim(), password.text) },
            onBiometricLoginClick = { /* TODO: Implement actual biometric auth trigger */ },
            onRegisterClick = onNavigateToRegister
        )
    } else {
        LoginScreenVertical(
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            passwordVisible = passwordVisible,
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            authState = authState,
            isBiometricAvailable = isBiometricAvailable,
            onLoginClick = { userViewModel.login(username.text.trim(), password.text) },
            onBiometricLoginClick = { /* TODO: Implement actual biometric auth trigger */ },
            onRegisterClick = onNavigateToRegister
        )
    }
}

@Composable
private fun LoginScreenVertical(
    username: TextFieldValue,
    onUsernameChange: (TextFieldValue) -> Unit,
    password: TextFieldValue,
    onPasswordChange: (TextFieldValue) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    authState: AuthState,
    isBiometricAvailable: Boolean,
    onLoginClick: () -> Unit,
    onBiometricLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val screenInfo = rememberScreenInfo()
    val dimensions = getResponsiveDimensions(screenInfo.widthSizeClass)

    // ✅ Responsive height with remember
    val textFieldHeight = remember(screenInfo.width) {
        when {
            screenInfo.width < 360 -> 48.dp
            screenInfo.width < 600 -> 52.dp
            else -> 56.dp
        }
    }

    val usernameError = when {
        authState is AuthState.Error && username.text.isBlank() -> R.string.error_empty_username
        else -> null
    }

    val passwordError = when {
        authState is AuthState.Error && password.text.isBlank() -> R.string.error_empty_password
        else -> null
    }

    val generalError = when {
        authState is AuthState.Error && username.text.isNotBlank() && password.text.isNotBlank() ->
            authState.messageId
        else -> null
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensions.paddingLarge),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Branding
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Passbook secure vault icon",
                modifier = Modifier
                    .size(dimensions.iconExtraLarge)
                    .padding(bottom = dimensions.paddingMedium),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = dimensions.paddingSmall)
            )

            Text(
                text = "Secure Password Manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = dimensions.paddingXXL)
            )

            // ✅ USERNAME FIELD - Fixed with TextFieldValue
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.username)) },
                placeholder = { Text("Enter username") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Username icon",
                        modifier = Modifier.size(dimensions.iconMedium)
                    )
                },
                isError = usernameError != null,
                supportingText = {
                    usernameError?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                enabled = authState !is AuthState.Loading,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = textFieldHeight)  // ✅ Use heightIn
                    .padding(bottom = dimensions.paddingMedium)
            )

            // ✅ PASSWORD FIELD - Fixed with TextFieldValue
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                placeholder = { Text("Enter password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password icon",
                        modifier = Modifier.size(dimensions.iconMedium)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = onPasswordVisibilityToggle,
                        modifier = Modifier.size(dimensions.touchTargetSize)
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError != null,
                supportingText = {
                    passwordError?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                enabled = authState !is AuthState.Loading,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = textFieldHeight)  // ✅ Use heightIn
                    .padding(bottom = dimensions.paddingMedium)
            )

            // Error message
            if (generalError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = dimensions.paddingMedium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensions.paddingMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error icon",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(dimensions.iconMedium)
                        )
                        Text(
                            text = stringResource(generalError),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
            ) {
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.standardButtonHeight),
                    enabled = username.text.isNotBlank() && password.text.isNotBlank() && authState !is AuthState.Loading
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.login),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Biometric Login Button
                if (isBiometricAvailable) {
                    OutlinedButton(
                        onClick = onBiometricLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.standardButtonHeight),
                        enabled = authState !is AuthState.Loading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(dimensions.paddingSmall))
                        Text(
                            text = stringResource(R.string.biometric_login),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRegisterClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.standardButtonHeight),
                    enabled = authState !is AuthState.Loading
                ) {
                    Text(
                        text = stringResource(R.string.register),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreenHorizontal(
    username: TextFieldValue,
    onUsernameChange: (TextFieldValue) -> Unit,
    password: TextFieldValue,
    onPasswordChange: (TextFieldValue) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    authState: AuthState,
    isBiometricAvailable: Boolean,
    onLoginClick: () -> Unit,
    onBiometricLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val screenInfo = rememberScreenInfo()
    val dimensions = getResponsiveDimensions(screenInfo.widthSizeClass)
    val textFieldHeight = 48.dp

    Scaffold { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensions.paddingLarge),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingXXL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Passbook secure vault icon",
                    modifier = Modifier.size(dimensions.iconLarge),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            // Inputs
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.username)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = textFieldHeight)
                        .padding(bottom = dimensions.paddingMedium),
                    enabled = authState !is AuthState.Loading,
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.password)) },
                    trailingIcon = {
                        IconButton(
                            onClick = onPasswordVisibilityToggle,
                            modifier = Modifier.size(dimensions.touchTargetSize)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = textFieldHeight)
                        .padding(bottom = dimensions.paddingMedium),
                    enabled = authState !is AuthState.Loading
                )

                Button(
                    onClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.standardButtonHeight)
                        .padding(bottom = dimensions.paddingSmall),
                    enabled = username.text.isNotBlank() && password.text.isNotBlank()
                ) {
                    Text(stringResource(R.string.login))
                }

                // Biometric Login Button
                if (isBiometricAvailable) {
                    OutlinedButton(
                        onClick = onBiometricLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.standardButtonHeight)
                            .padding(bottom = dimensions.paddingSmall),
                        enabled = authState !is AuthState.Loading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(dimensions.paddingSmall))
                        Text(
                            text = stringResource(R.string.biometric_login),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRegisterClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.standardButtonHeight),
                    enabled = authState !is AuthState.Loading
                ) {
                    Text(stringResource(R.string.register))
                }
            }
        }
    }
}
