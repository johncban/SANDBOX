package com.jcb.passbook.presentation.ui.screens.vault

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jcb.passbook.presentation.viewmodel.vault.ItemOperationState
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import kotlinx.coroutines.launch
import java.util.Arrays
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.jcb.passbook.data.local.database.entities.Item

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    itemViewModel: ItemViewModel,
    userViewModel: UserViewModel,
    navController: NavController
) {
    val items by itemViewModel.items.collectAsState()
    val operationState by itemViewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Dialog visibility states
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRecoveryConfirmDialog by remember { mutableStateOf(false) } // ✅ NEW: Recovery confirmation

    // Dialog data states
    var currentEditItem by remember { mutableStateOf<Item?>(null) }
    var selectedItemForDetails by remember { mutableStateOf<Item?>(null) }
    var itemToDelete by remember { mutableStateOf<Item?>(null) }

    // KeyRotation state
    val keyRotationState by userViewModel.keyRotationState.collectAsState()
    val context = LocalContext.current

    // Show snackbars for errors
    LaunchedEffect(operationState) {
        if (operationState is ItemOperationState.Error) {
            val message = (operationState as ItemOperationState.Error).message
            snackbarHostState.showSnackbar(message)
            itemViewModel.clearOperationState()
        }
    }

    // ✅ ENHANCED: Key rotation state handling with recovery feedback
    LaunchedEffect(keyRotationState) {
        when (keyRotationState) {
            is ItemOperationState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "Database key rotated successfully!",
                    duration = SnackbarDuration.Short
                )
                userViewModel.clearKeyRotationState()
            }
            is ItemOperationState.Error -> {
                val message = (keyRotationState as ItemOperationState.Error).message
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long
                )
                userViewModel.clearKeyRotationState()
            }
            else -> {}
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                currentEditItem = null
                showAddDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Item")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("My Items") },
                actions = {
                    // ✅ Optional: Keep manual rotation button for advanced users
                    // (Key rotation now happens automatically on logout)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // Emergency Database Recovery Button
                        IconButton(
                            onClick = { showRecoveryConfirmDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Build,
                                contentDescription = "Emergency Database Recovery",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        // Manual Key Rotation Button (optional - now auto-rotates on logout)
                        if (keyRotationState is ItemOperationState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(horizontal = 12.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = { userViewModel.rotateDatabaseKey() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VpnKey,
                                    contentDescription = "Manually Rotate Database Key (Auto-rotates on logout)"
                                )
                            }
                        }

                    }

                    // Logout Button (now triggers auto key rotation)
                    IconButton(onClick = {
                        userViewModel.logout()
                        itemViewModel.clearAllItems()
                        navController.navigate("login") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No items found. Add some!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items = items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onClick = {
                            selectedItemForDetails = item
                            showDetailsDialog = true
                        }
                    )
                }
            }
        }
    }

    // --- Dialog Composables ---

    if (showAddDialog) {
        ItemAddDialog(
            onDismiss = { showAddDialog = false },
            onAddItem = { title, password ->
                itemViewModel.insert(title, password)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && currentEditItem != null) {
        ItemEditDialog(
            item = currentEditItem!!,
            onDismiss = {
                showEditDialog = false
                currentEditItem = null
            },
            onEditItem = { originalItem, newTitle, newPassword ->
                itemViewModel.update(originalItem, newTitle, newPassword)
                showEditDialog = false
                currentEditItem = null
            }
        )
    }

    if (showDetailsDialog && selectedItemForDetails != null) {
        val decryptedPassword by remember(selectedItemForDetails) {
            derivedStateOf {
                itemViewModel.getDecryptedPassword(selectedItemForDetails!!)
            }
        }

        ItemDetailsDialog(
            item = selectedItemForDetails!!,
            decryptedPassword = decryptedPassword,
            onDismiss = {
                showDetailsDialog = false
                selectedItemForDetails = null
            },
            onItemEdit = { editItem ->
                currentEditItem = editItem
                showDetailsDialog = false
                selectedItemForDetails = null
                showEditDialog = true
            },
            onItemDelete = { deleteItem ->
                itemToDelete = deleteItem
                showDetailsDialog = false
                selectedItemForDetails = null
                showDeleteConfirmDialog = true
            }
        )
    }

    if (showDeleteConfirmDialog && itemToDelete != null) {
        ConfirmDeleteDialog(
            itemTitle = itemToDelete!!.title,
            onConfirm = {
                val toDelete = itemToDelete!!
                coroutineScope.launch {
                    itemViewModel.delete(toDelete)
                }
                showDeleteConfirmDialog = false
                itemToDelete = null
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                itemToDelete = null
            }
        )
    }

    // ✅ NEW: Emergency Database Recovery Confirmation Dialog
    if (showRecoveryConfirmDialog) {
        RecoveryConfirmDialog(
            onConfirm = {
                showRecoveryConfirmDialog = false
                coroutineScope.launch {
                    userViewModel.attemptDatabaseRecovery()
                    snackbarHostState.showSnackbar(
                        message = "Database recovery attempted. Check logs for results.",
                        duration = SnackbarDuration.Long
                    )
                }
            },
            onDismiss = {
                showRecoveryConfirmDialog = false
            }
        )
    }
}

// --- Reusable Composables ---

@Composable
fun ItemRow(item: Item, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Icon(
                Icons.Filled.Password,
                contentDescription = "Password set",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Input field reusable
@Composable
fun PasswordInputField(
    label: String,
    password: CharArray,
    onPasswordChange: (CharArray) -> Unit,
    isPasswordVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = String(password),
        onValueChange = {
            Arrays.fill(password, ' ')
            onPasswordChange(it.toCharArray())
        },
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            val image = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
            IconButton(onClick = onVisibilityToggle) {
                Icon(imageVector = image, contentDescription = "Toggle password visibility")
            }
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else null,
        modifier = modifier.fillMaxWidth()
    )
}

// Common Text input field
@Composable
fun TextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText,
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth()
    )
}

// Common Confirm/Cancel buttons Row
@Composable
fun ConfirmCancelButtons(
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    confirmEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onConfirmClick,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Text(confirmText)
        }
        OutlinedButton(
            onClick = onCancelClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(cancelText)
        }
    }
}

// --- Modular Dialogs ---

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ItemAddDialog(
    onDismiss: () -> Unit,
    onAddItem: (String, String) -> Unit
) {
    var itemTitle by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(CharArray(0)) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            Arrays.fill(password, ' ')
            onDismiss()
        },
        title = { Text("Add New Item") },
        text = {
            Column {
                TextInputField(
                    label = "Item Title",
                    value = itemTitle,
                    onValueChange = { itemTitle = it }
                )
                Spacer(Modifier.height(16.dp))
                PasswordInputField(
                    label = "Password",
                    password = password,
                    onPasswordChange = {
                        Arrays.fill(password, ' ')
                        password = it
                    },
                    isPasswordVisible = passwordVisible,
                    onVisibilityToggle = { passwordVisible = !passwordVisible }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddItem(itemTitle.trim(), String(password))
                    Arrays.fill(password, ' ')
                },
                enabled = itemTitle.isNotBlank() && password.isNotEmpty()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    Arrays.fill(password, ' ')
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ItemEditDialog(
    item: Item,
    onDismiss: () -> Unit,
    onEditItem: (Item, String?, String?) -> Unit
) {
    var itemTitle by remember { mutableStateOf(item.title) }
    var newPassword by remember { mutableStateOf(CharArray(0)) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            Arrays.fill(newPassword, ' ')
            onDismiss()
        },
        title = { Text("Edit Item") },
        text = {
            Column {
                TextInputField(
                    label = "Item Title",
                    value = itemTitle,
                    onValueChange = { itemTitle = it }
                )
                Spacer(Modifier.height(16.dp))
                PasswordInputField(
                    label = "New Password (optional)",
                    password = newPassword,
                    onPasswordChange = {
                        Arrays.fill(newPassword, ' ')
                        newPassword = it
                    },
                    isPasswordVisible = passwordVisible,
                    onVisibilityToggle = { passwordVisible = !passwordVisible },
                    placeholder = "Leave blank to keep current"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = itemTitle.trim().takeIf { it != item.title }
                    val passwordStr = if (newPassword.isNotEmpty()) String(newPassword) else null
                    onEditItem(item, finalTitle, passwordStr)
                    Arrays.fill(newPassword, ' ')
                },
                enabled = itemTitle.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    Arrays.fill(newPassword, ' ')
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ItemDetailsDialog(
    item: Item,
    decryptedPassword: String?,
    onDismiss: () -> Unit,
    onItemEdit: (Item) -> Unit,
    onItemDelete: (Item) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = when {
                        decryptedPassword == null -> "[Decryption Failed]"
                        passwordVisible -> decryptedPassword
                        else -> "••••••••••"
                    },
                    onValueChange = {},
                    label = { Text("Password") },
                    readOnly = true,
                    visualTransformation = if (passwordVisible || decryptedPassword == null) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        if (decryptedPassword != null) {
                            val icon = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { onItemEdit(item) }) {
                    Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onItemDelete(item) }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    itemTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Delete") },
        text = { Text("Are you sure you want to delete '$itemTitle'?") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Yes")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}

// ✅ NEW: Emergency Database Recovery Confirmation Dialog
@Composable
fun RecoveryConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = "Recovery",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                "Emergency Database Recovery",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚠️ This will attempt to recover database access by restoring the backup passphrase.")
                Text("Only use this if you cannot login after a failed key rotation.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Do you want to proceed?",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Yes, Attempt Recovery")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
