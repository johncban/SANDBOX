package com.jcb.passbook.presentation.ui.screens.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.jcb.passbook.presentation.ui.components.AccessiblePasswordField
import com.jcb.passbook.presentation.ui.responsive.isLandscape
import com.jcb.passbook.presentation.ui.responsive.rememberScreenInfo
import com.jcb.passbook.presentation.ui.theme.ResponsiveDimensions
import com.jcb.passbook.presentation.ui.theme.getResponsiveDimensions
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel

/**
 * LoginScreen - Responsive authentication UI
 *
 * Supports:
 * - Phones (portrait & landscape)
 * - Tablets (portrait & landscape)
 * - Foldable devices
 * - All window size classes
 * - WCAG 2.1 AA accessibility
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
    val screenInfo = rememberScreenInfo()
    val isLandscapeMode = isLandscape()

    // Navigate on successful login
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val userId = (authState as AuthState.Success).userId
            onLoginSuccess(userId)
            userViewModel.clearAuthState()
        }
    }

    if (isLandscapeMode && screenInfo.width < 840) {
        // Compact landscape: horizontal layout
        LoginScreenHorizontal(
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
            onLoginClick = { userViewModel.login(username.trim(), password) },
            onRegisterClick = onNavigateToRegister
        )
    } else {
        // Default vertical layout
        LoginScreenVertical(
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
            onLoginClick = { userViewModel.login(username.trim(), password) },
            onRegisterClick = onNavigateToRegister
        )
    }
}

@Composable
private fun LoginScreenVertical(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    authState: AuthState,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val screenInfo = rememberScreenInfo()
    val dimensions: ResponsiveDimensions = getResponsiveDimensions(screenInfo.widthSizeClass)

    val usernameError: Int? = when {
        authState is AuthState.Error && username.isBlank() -> R.string.error_empty_username
        else -> null
    }

    val passwordError: Int? = when {
        authState is AuthState.Error && password.isBlank() -> R.string.error_empty_password
        else -> null
    }

    val generalError: Int? = when {
        authState is AuthState.Error && username.isNotBlank() && password.isNotBlank() ->
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
                contentDescription = stringResource(R.string.content_desc_app_lock_icon),
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
                modifier = Modifier.padding(bottom = dimensions.spacingXXL)
            )

            // Input fields
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.username)) },
                placeholder = { Text("Enter your username") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                },
                isError = usernameError != null,
                supportingText = {
                    usernameError?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                enabled = authState !is AuthState.Loading,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.textFieldHeight)
                    .padding(bottom = dimensions.paddingMedium)
            )

            AccessiblePasswordField(
                value = password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.password),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.textFieldHeight)
                    .padding(bottom = dimensions.paddingMedium),
                enabled = authState !is AuthState.Loading,
                isError = passwordError != null,
                supportingText = {
                    passwordError?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            // Error card
            generalError?.let { errorRes ->
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
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(dimensions.iconMedium)
                        )
                        Text(
                            text = stringResource(errorRes),
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
                    enabled = username.isNotBlank() &&
                            password.isNotBlank() &&
                            authState !is AuthState.Loading
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.login),
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
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    authState: AuthState,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val screenInfo = rememberScreenInfo()
    val dimensions: ResponsiveDimensions = getResponsiveDimensions(screenInfo.widthSizeClass)

    val usernameError: Int? = when {
        authState is AuthState.Error && username.isBlank() -> R.string.error_empty_username
        else -> null
    }

    val passwordError: Int? = when {
        authState is AuthState.Error && password.isBlank() -> R.string.error_empty_password
        else -> null
    }

    Scaffold { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensions.paddingLarge),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingXXL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding column
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.content_desc_app_lock_icon),
                    modifier = Modifier.size(dimensions.iconExtraLarge),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Input column
            Column(
                modifier = Modifier
                    .weight(0.6f)
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
                        .height(dimensions.textFieldHeight)
                        .padding(bottom = dimensions.paddingMedium),
                    enabled = authState !is AuthState.Loading,
                    singleLine = true,
                    isError = usernameError != null,
                    supportingText = {
                        usernameError?.let {
                            Text(
                                text = stringResource(it),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                AccessiblePasswordField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.textFieldHeight)
                        .padding(bottom = dimensions.paddingMedium),
                    enabled = authState !is AuthState.Loading,
                    isError = passwordError != null,
                    supportingText = {
                        passwordError?.let {
                            Text(
                                text = stringResource(it),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                Button(
                    onClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.standardButtonHeight),
                    enabled = username.isNotBlank() && password.isNotBlank()
                ) {
                    Text(stringResource(R.string.login))
                }

                Spacer(modifier = Modifier.height(dimensions.paddingSmall))

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
