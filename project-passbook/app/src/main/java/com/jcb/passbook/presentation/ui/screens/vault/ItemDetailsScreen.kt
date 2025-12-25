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
import com.jcb.passbook.data.local.database.entities.PasswordCategoryEnum
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcb.passbook.presentation.ui.components.AccessiblePasswordField
import com.jcb.passbook.presentation.ui.util.WindowHeightSizeClass
import com.jcb.passbook.presentation.ui.util.rememberWindowSizeClasses
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.vault.SaveState

/**
 * ItemDetailScreen - For ADDING NEW password items only
 *
 * NOTE: Editing existing items is handled via modal bottom sheet in ItemListScreen
 *
 * FIXED: Properly handles save state through ViewModel to prevent compose session cancellation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ItemViewModel = hiltViewModel()
) {
    // ✅ Form State
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PasswordCategoryEnum.OTHER) }
    var isFavorite by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    // ✅ Window Size Classes
    val (_, heightClass) = rememberWindowSizeClasses()
    val isLandscapeLike = heightClass == WindowHeightSizeClass.Compact

    // ✅ Context for Toast messages
    val context = LocalContext.current

    // ✅ CRITICAL FIX: Observe save state from ViewModel
    val saveState by viewModel.saveState.collectAsState()

    // ✅ CRITICAL FIX: Handle save state changes
    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveState.Success -> {
                Toast.makeText(
                    context,
                    "Password saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.resetSaveState() // Reset state before navigation
                onNavigateBack()
            }
            is SaveState.Error -> {
                Toast.makeText(
                    context,
                    "Failed to save: ${state.message}",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.resetSaveState() // Reset error state
            }
            else -> {
                // SaveState.Idle or SaveState.Loading - do nothing
            }
        }
    }

    // ✅ Loading state for UI feedback
    val isLoading = saveState is SaveState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Password") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isLoading // Disable back during save
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // ✅ CRITICAL FIX: Save button with proper state handling
                    IconButton(
                        onClick = {
                            // ✅ Call ViewModel save - it survives composable destruction
                            viewModel.insertOrUpdateItem(
                                id = 0, // New item
                                title = title,
                                username = username.takeIf { it.isNotBlank() },
                                encryptedPassword = password.toByteArray(), // TODO: Replace with actual encryption
                                url = url.takeIf { it.isNotBlank() },
                                notes = notes.takeIf { it.isNotBlank() },
                                passwordCategory = selectedCategory,
                                isFavorite = isFavorite
                            )
                            // ✅ DO NOT call onNavigateBack() here - let LaunchedEffect handle it
                        },
                        enabled = title.isNotBlank() && password.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            // ✅ Show loading indicator during save
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save",
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
            // Landscape: Two-column layout
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
            // Portrait: Single-column layout
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

// ✅ Landscape Layout Component
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
                enabled = !isLoading
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mark as favorite", style = MaterialTheme.typography.bodyLarge)
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
                enabled = !isLoading
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

// ✅ Portrait Layout Component
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
            enabled = !isLoading
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
            enabled = !isLoading
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mark as favorite", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = isFavorite,
                onCheckedChange = onFavoriteChange,
                enabled = !isLoading
            )
        }
    }
}

// ✅ Category Picker Dialog Component
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
                        leadingContent = { Text(category.icon) },
                        modifier = Modifier.clickable {
                            onCategorySelected(category)
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ✅ Category Card Component
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
