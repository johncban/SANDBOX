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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcb.passbook.data.local.database.entities.PasswordCategory
import com.jcb.passbook.presentation.ui.components.AccessiblePasswordField
import com.jcb.passbook.presentation.ui.util.WindowHeightSizeClass
import com.jcb.passbook.presentation.ui.util.rememberWindowSizeClasses
import com.jcb.passbook.presentation.viewmodel.ItemViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: ItemViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PasswordCategory.OTHER) }
    var isFavorite by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val (_, heightClass) = rememberWindowSizeClasses()
    val isLandscapeLike = heightClass == WindowHeightSizeClass.Compact

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "Add Password" else "Edit Password") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // TODO: Encrypt password before saving
                        viewModel.insertOrUpdateItem(
                            id = itemId ?: 0,
                            title = title,
                            username = username.takeIf { it.isNotBlank() },
                            encryptedPassword = password.toByteArray(), // Replace with actual encryption
                            url = url.takeIf { it.isNotBlank() },
                            notes = notes.takeIf { it.isNotBlank() },
                            passwordCategory = selectedCategory,
                            isFavorite = isFavorite
                        )
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLandscapeLike) {
            // Landscape: Two-column layout
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
                        onValueChange = { title = it },
                        label = { Text("Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    )

                    CategoryCard(
                        selectedCategory = selectedCategory,
                        onClick = { showCategoryPicker = true }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mark as favorite", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = isFavorite,
                            onCheckedChange = { isFavorite = it }
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
                        onValueChange = { password = it },
                        label = "Password *",
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Website URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null)
                        }
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes / Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        maxLines = 6,
                        placeholder = { Text("Add context about this password...") }
                    )
                }
            }
        } else {
            // Portrait: Single-column layout
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
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                )

                AccessiblePasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password *",
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null)
                    }
                )

                CategoryCard(
                    selectedCategory = selectedCategory,
                    onClick = { showCategoryPicker = true }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    placeholder = { Text("Add context about this password...") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mark as favorite", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isFavorite,
                        onCheckedChange = { isFavorite = it }
                    )
                }
            }
        }
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
