package com.jcb.passbook.presentation.ui.screens.vault

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategoryEnum
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcb.passbook.presentation.ui.components.AccessiblePasswordField
import com.jcb.passbook.presentation.ui.util.WindowHeightSizeClass
import com.jcb.passbook.presentation.ui.util.rememberWindowSizeClasses
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.vault.SaveState

/**
 * ItemDetailsScreen - For ADDING NEW password items
 *
 * NOTE: Editing existing items is handled via modal bottom sheet in ItemListScreen
 *
 * FIXED:
 * - Constructs complete Item object before calling ViewModel
 * - Respects Room schema with proper Item entity structure
 * - Handles save state through ViewModel StateFlow to survive composition reuse
 * - Type-safe with PasswordCategoryEnum
 * - No inline property changes - all state managed through ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailsScreen(
    itemId: Long = 0L,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit = {},
    viewModel: ItemViewModel = hiltViewModel()
) {
    // ✅ Form State - Local UI state for form fields
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PasswordCategoryEnum.OTHER) }
    var isFavorite by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    // ✅ Window Size Classes for responsive layout
    val (_, heightClass) = rememberWindowSizeClasses()
    val isLandscapeLike = heightClass == WindowHeightSizeClass.Compact

    // ✅ Context for Toast messages
    val context = LocalContext.current

    // ✅ CRITICAL: Observe save state from ViewModel (survives composition destruction)
    val saveState by viewModel.saveState.collectAsState()

    // ✅ Load item if editing (itemId > 0)
    LaunchedEffect(itemId) {
        if (itemId > 0L) {
            viewModel.getItemById(itemId)
        }
    }

    // Load item data for editing if available
    val selectedItem by viewModel.selectedItem.collectAsState()
    LaunchedEffect(selectedItem) {
        selectedItem?.let { item ->
            title = item.title
            username = item.username ?: ""
            password = String(item.encryptedPassword) // TODO: Replace with actual decryption
            url = item.url ?: ""
            notes = item.notes ?: ""
            selectedCategory = item.getPasswordCategoryEnum()
            isFavorite = item.isFavorite
        }
    }

    // ✅ CRITICAL: Handle save state changes through StateFlow
    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveState.Success -> {
                // Show success message
                Toast.makeText(
                    context,
                    state.message,
                    Toast.LENGTH_SHORT
                ).show()
                // Reset state BEFORE navigation to prevent duplicate triggers
                viewModel.resetSaveState()
                // Navigate back - ViewModel state survives this
                onNavigateBack()
                onSaveSuccess()
            }
            is SaveState.Error -> {
                // Show error message but don't navigate
                Toast.makeText(
                    context,
                    "Failed to save: ${state.message}",
                    Toast.LENGTH_LONG
                ).show()
                // Reset error state for retry
                viewModel.resetSaveState()
            }
            else -> {
                // SaveState.Idle or SaveState.Loading - do nothing
            }
        }
    }

    // ✅ Loading state indicator
    val isLoading = saveState is SaveState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == 0L) "Add New Password" else "Edit Password") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isLoading  // Disable during save
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // ✅ Save button with loading indicator
                    IconButton(
                        onClick = {
                            // ✅ Construct complete Item object
                            val item = Item(
                                id = itemId,
                                title = title,
                                username = username.takeIf { it.isNotBlank() },
                                encryptedPassword = password.toByteArray(),  // TODO: Replace with actual encryption via PasswordEncryptionService
                                url = url.takeIf { it.isNotBlank() },
                                notes = notes.takeIf { it.isNotBlank() },
                                passwordCategory = selectedCategory.name,  // ✅ Store as string in Room
                                isFavorite = isFavorite
                            )
                            // ✅ Call ViewModel with complete object
                            viewModel.insertOrUpdateItem(item)
                            // DO NOT call onNavigateBack() here - let LaunchedEffect handle it
                        },
                        enabled = title.isNotBlank() && password.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            // Show loading spinner during save
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save password",
                                tint = if (title.isNotBlank() && password.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLandscapeLike) {
            // Landscape: Two-column layout for wider screens
            LandscapeLayout(
                paddingValues = paddingValues,
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
                onFavoriteChange = { isFavorite = it },
                isLoading = isLoading
            )
        } else {
            // Portrait: Single-column layout for phones
            PortraitLayout(
                paddingValues = paddingValues,
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
                onFavoriteChange = { isFavorite = it },
                isLoading = isLoading
            )
        }
    }

    // Category picker dialog
    if (showCategoryPicker) {
        CategoryPickerDialog(
            selectedCategory = selectedCategory,
            onCategorySelected = { category ->
                selectedCategory = category
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

/**
 * Landscape Layout - Two-column form layout
 *
 * Left column: Title, Username, Category, Favorite
 * Right column: Password, URL, Notes
 */
@Composable
private fun LandscapeLayout(
    paddingValues: PaddingValues,
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
    selectedCategory: PasswordCategoryEnum,
    onCategoryClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left column
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                isError = title.isBlank(),
                supportingText = if (title.isBlank()) {{ Text("Required") }} else null
            )

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username / Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                enabled = !isLoading
            )

            CategoryCard(
                selectedCategory = selectedCategory,
                onClick = onCategoryClick,
                enabled = !isLoading
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Mark as favorite",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isFavorite,
                    onCheckedChange = onFavoriteChange,
                    enabled = !isLoading
                )
            }
        }

        // Right column
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccessiblePasswordField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Password *",
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                isError = password.isBlank(),
                supportingText = if (password.isBlank()) "Required" else ""
            )

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Website URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Language, contentDescription = null)
                },
                enabled = !isLoading
            )

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Notes / Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                maxLines = 6,
                placeholder = { Text("Add context about this password...") },
                enabled = !isLoading
            )
        }
    }
}

/**
 * Portrait Layout - Single-column form layout for phones
 *
 * All fields stacked vertically with full width
 */
@Composable
private fun PortraitLayout(
    paddingValues: PaddingValues,
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
    selectedCategory: PasswordCategoryEnum,
    onCategoryClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading,
            isError = title.isBlank(),
            supportingText = if (title.isBlank()) {{ Text("Required") }} else null
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username / Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            enabled = !isLoading
        )

        AccessiblePasswordField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password *",
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            isError = password.isBlank(),
            supportingText = if (password.isBlank()) "Required" else ""
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Website URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Language, contentDescription = null)
            },
            enabled = !isLoading
        )

        CategoryCard(
            selectedCategory = selectedCategory,
            onClick = onCategoryClick,
            enabled = !isLoading
        )

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notes / Description") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5,
            placeholder = { Text("Add context about this password...") },
            enabled = !isLoading
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mark as favorite",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isFavorite,
                onCheckedChange = onFavoriteChange,
                enabled = !isLoading
            )
        }

        Spacer(modifier = Modifier.height(32.dp))  // Extra space for floating button
    }
}

/**
 * Category Picker Dialog
 *
 * Displays all PasswordCategoryEnum options for selection
 */
@Composable
private fun CategoryPickerDialog(
    selectedCategory: PasswordCategoryEnum,
    onCategorySelected: (PasswordCategoryEnum) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            LazyColumn {
                items(PasswordCategoryEnum.entries.size) { index ->
                    val category = PasswordCategoryEnum.entries[index]
                    ListItem(
                        headlineContent = { Text(category.displayName) },
                        leadingContent = { Text(category.icon, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.clickable {
                            onCategorySelected(category)
                        },
                        trailingContent = {
                            if (selectedCategory == category) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Category Card Component
 *
 * Shows currently selected category with clickable card for changing
 */
@Composable
private fun CategoryCard(
    selectedCategory: PasswordCategoryEnum,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCategory.icon,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = selectedCategory.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Open category picker",
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}