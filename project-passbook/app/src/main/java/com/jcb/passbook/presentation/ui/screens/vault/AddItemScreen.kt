package com.jcb.passbook.presentation.ui.screens.vault

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemOperationState
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import timber.log.Timber

private const val TAG = "AddItemScreen"

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: ItemViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel()
) {
    // ✅ FIXED: Explicit Int type for currentUserId
    val userIdLong by userViewModel.userId.collectAsStateWithLifecycle()
    val currentUserId: Int = userIdLong.toInt()
    val itemViewModelUserIdState by viewModel.userId.collectAsStateWithLifecycle(initialValue = -1L)
    val itemViewModelUserId: Long = itemViewModelUserIdState
    val operationState by viewModel.operationState.collectAsStateWithLifecycle(initialValue = ItemOperationState.Idle)

    var itemName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // ✅ Set userId when currentUserId changes (convert Int to Long)
    LaunchedEffect(currentUserId) {
        if (currentUserId > 0) {
            Timber.tag(TAG).d("✅ Setting userId to ItemViewModel: $currentUserId")
            viewModel.setUserId(currentUserId.toLong())
        } else {
            Timber.tag(TAG).e("❌ currentUserId is invalid: $currentUserId")
            showErrorDialog = true
            errorMessage = "No user ID set. Please logout and login again."
        }
    } // ✅ ADDED: Missing closing brace

    // ✅ Monitor ItemViewModel userId changes
    LaunchedEffect(itemViewModelUserId) {
        Timber.tag(TAG).d("ItemViewModel userId changed to: $itemViewModelUserId")
    }

    // ✅ Monitor operation state for success/error
    LaunchedEffect(operationState) {
        Timber.tag(TAG).d("operationState changed: $operationState")
        when (operationState) {
            is ItemOperationState.Success -> {
                Timber.tag(TAG).i("✓ Item added successfully!")
                viewModel.clearOperationState()
                onSuccess()
            }
            is ItemOperationState.Error -> {
                errorMessage = (operationState as ItemOperationState.Error).message
                showErrorDialog = true
                Timber.tag(TAG).e("Item add failed: $errorMessage")
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Password") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ✅ DEBUG: Show user ID status with proper type
            if (itemViewModelUserId <= 0L) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ User ID not set\nCurrent: $currentUserId | ViewModel: $itemViewModelUserId",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Service Name Field
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text("Service/Website Name") },
                placeholder = { Text("e.g., Gmail, Facebook") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = operationState !is ItemOperationState.Loading,
                isError = itemName.isBlank() && itemName.isNotEmpty()
            )

            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username/Email") },
                placeholder = { Text("(Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = operationState !is ItemOperationState.Loading
            )

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = operationState !is ItemOperationState.Loading,
                isError = password.isBlank() && password.isNotEmpty(),
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                }
            )

            // URL Field
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("https://... (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = operationState !is ItemOperationState.Loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            // Notes Field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Additional notes... (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                enabled = operationState !is ItemOperationState.Loading,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    Timber.tag(TAG).d(
                        "Save button clicked - currentUserId: $currentUserId, itemViewModelUserId: $itemViewModelUserId"
                    )

                    when {
                        itemViewModelUserId <= 0L -> {
                            showErrorDialog = true
                            errorMessage = "No user ID set. Please logout and login again."
                            Timber.tag(TAG).e("❌ Save blocked - userId is invalid: $itemViewModelUserId")
                        }
                        itemName.isBlank() -> {
                            showErrorDialog = true
                            errorMessage = "Please enter a service/website name"
                        }
                        password.isBlank() -> {
                            showErrorDialog = true
                            errorMessage = "Please enter a password"
                        }
                        else -> {
                            Timber.tag(TAG).d(
                                "✅ Calling viewModel.insert: itemName=$itemName, userId=$itemViewModelUserId"
                            )
                            viewModel.insert(
                                itemName = itemName,
                                plainTextPassword = password,
                                username = username.takeIf { it.isNotBlank() },
                                url = url.takeIf { it.isNotBlank() },
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = operationState !is ItemOperationState.Loading && itemViewModelUserId > 0L
            ) {
                when (operationState) {
                    is ItemOperationState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    else -> Text("Save Password")
                }
            }

            // Cancel Button
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = operationState !is ItemOperationState.Loading
            ) {
                Text("Cancel")
            }
        }
    }

    // Error Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
