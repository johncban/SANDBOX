package com.jcb.passbook.presentation.ui.screens.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategory
import com.jcb.passbook.presentation.ui.components.AccessiblePasswordField

/**
 * ItemBottomSheetContent - Modal bottom sheet for viewing/editing password items
 *
 * Features:
 * - View mode: Shows password with copy buttons
 * - Edit mode: Full editing capabilities
 * - Edit button toggles between modes
 * - Delete confirmation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemBottomSheetContent(
    item: Item,
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSave: (Item) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember(item) { mutableStateOf(item.title) }
    var username by remember(item) { mutableStateOf(item.username ?: "") }
    var password by remember(item) { mutableStateOf(String(item.encryptedPassword)) }
    var url by remember(item) { mutableStateOf(item.url ?: "") }
    var notes by remember(item) { mutableStateOf(item.notes ?: "") }
    var selectedCategory by remember(item) { mutableStateOf(item.getPasswordCategoryEnum()) }
    var isFavorite by remember(item) { mutableStateOf(item.isFavorite) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Header with close, title, and action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }

            Text(
                text = if (isEditMode) "Edit Password" else "View Password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row {
                if (isEditMode) {
                    // Save button in edit mode
                    IconButton(
                        onClick = {
                            onSave(
                                item.copy(
                                    title = title,
                                    username = username.takeIf { it.isNotBlank() },
                                    encryptedPassword = password.toByteArray(), // TODO: Encrypt
                                    url = url.takeIf { it.isNotBlank() },
                                    notes = notes.takeIf { it.isNotBlank() },
                                    passwordCategory = selectedCategory.name,
                                    isFavorite = isFavorite
                                )
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // Edit button in view mode
                    IconButton(onClick = { onEditModeChange(true) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Delete button (always visible)
                // Line 109-121: Replace the IconButton onClick handler
                IconButton(
                    onClick = {
                        // ✅ FIXED: Ensure all fields are validated before save
                        if (title.isBlank()) {
                            // Show error snackbar
                            return@IconButton
                        }

                        if (password.isBlank()) {
                            // Show error snackbar
                            return@IconButton
                        }

                        // ✅ Save with proper data
                        onSave(
                            item.copy(
                                title = title.trim(),
                                username = username.takeIf { it.isNotBlank() }?.trim(),
                                encryptedPassword = password.toByteArray(), // TODO: Add actual encryption
                                url = url.takeIf { it.isNotBlank() }?.trim(),
                                notes = notes.takeIf { it.isNotBlank() }?.trim(),
                                passwordCategory = selectedCategory.name,
                                isFavorite = isFavorite,
                                updatedAt = System.currentTimeMillis() // ✅ Update timestamp
                            )
                        )
                    }
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

            }
        }

        Divider(modifier = Modifier.padding(bottom = 16.dp))

        // Content area (scrollable)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isEditMode) {
                // EDIT MODE: Editable fields
                EditModeContent(
                    title = title,
                    onTitleChange = { title = it },
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    url = url,
                    onUrlChange = { url = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    selectedCategory = selectedCategory,
                    onCategoryClick = { showCategoryPicker = true },
                    isFavorite = isFavorite,
                    onFavoriteChange = { isFavorite = it }
                )
            } else {
                // VIEW MODE: Read-only with copy buttons
                ViewModeContent(
                    title = title,
                    username = username,
                    password = password,
                    url = url,
                    notes = notes,
                    category = selectedCategory,
                    isFavorite = isFavorite
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Category picker dialog
    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Select Category") },
            text = {
                LazyColumn {
                    items(PasswordCategory.entries.size) { index ->
                        val category = PasswordCategory.entries[index]
                        ListItem(
                            headlineContent = { Text(category.displayName) },
                            leadingContent = { Text(category.icon) },
                            modifier = Modifier.clickable {
                                selectedCategory = category
                                showCategoryPicker = false
                            },
                            trailingContent = {
                                if (selectedCategory == category) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Password?") },
            text = { Text("Are you sure you want to delete \"$title\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ViewModeContent(
    title: String,
    username: String,
    password: String,
    url: String,
    notes: String,
    category: PasswordCategory,
    isFavorite: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Title field
    ViewField(
        label = "Title",
        value = title,
        icon = Icons.Default.Label
    )

    // Username field with copy button
    if (username.isNotBlank()) {
        ViewField(
            label = "Username / Email",
            value = username,
            icon = Icons.Default.Person,
            onCopy = {
                clipboardManager.setText(AnnotatedString(username))
            }
        )
    }

    // Password field with show/hide and copy
    ViewPasswordField(
        label = "Password",
        password = password,
        isVisible = isPasswordVisible,
        onVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
        onCopy = {
            clipboardManager.setText(AnnotatedString(password))
        }
    )

    // URL field with copy button
    if (url.isNotBlank()) {
        ViewField(
            label = "Website URL",
            value = url,
            icon = Icons.Default.Language,
            onCopy = {
                clipboardManager.setText(AnnotatedString(url))
            }
        )
    }

    // Category
    ViewField(
        label = "Category",
        value = "${category.icon} ${category.displayName}",
        icon = Icons.Default.Category
    )

    // Notes
    if (notes.isNotBlank()) {
        ViewField(
            label = "Notes",
            value = notes,
            icon = Icons.Default.Note,
            maxLines = Int.MAX_VALUE
        )
    }

    // Favorite indicator
    if (isFavorite) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Marked as favorite",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ViewField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    maxLines: Int = 1,
    onCopy: (() -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = maxLines
                )
            }

            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewPasswordField(
    label: String,
    password: String,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onCopy: () -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isVisible) password else "••••••••",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row {
                IconButton(onClick = onVisibilityToggle) {
                    Icon(
                        if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isVisible) "Hide" else "Show",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditModeContent(
    title: String,
    onTitleChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    selectedCategory: PasswordCategory,
    onCategoryClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Title *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Username / Email") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Person, contentDescription = null)
        }
    )

    AccessiblePasswordField(
        value = password,
        onValueChange = onPasswordChange,
        label = "Password *",
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("Website URL") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Language, contentDescription = null)
        }
    )

    CategoryCard(
        selectedCategory = selectedCategory,
        onClick = onCategoryClick
    )

    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = { Text("Notes / Description") },
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        maxLines = 5,
        placeholder = { Text("Add context about this password...") }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Mark as favorite", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = isFavorite,
            onCheckedChange = onFavoriteChange
        )
    }
}

@Composable
private fun CategoryCard(
    selectedCategory: PasswordCategory,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = selectedCategory.icon)
                    Text(
                        text = selectedCategory.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }
    }
}
