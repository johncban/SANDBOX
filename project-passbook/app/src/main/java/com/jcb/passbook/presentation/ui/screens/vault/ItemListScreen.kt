package com.jcb.passbook.presentation.ui.screens.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import timber.log.Timber

private const val TAG = "ItemListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onItemClick: (Long) -> Unit,
    onAddNewItem: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: ItemViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel()
) {
    val currentUserId by userViewModel.currentUserId.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }

    // ✅ Set userId when available
    LaunchedEffect(currentUserId) {
        if (currentUserId != -1L) {
            Timber.tag(TAG).d("✅ Setting userId to ItemViewModel: $currentUserId")
            viewModel.setUserId(currentUserId)
        } else {
            Timber.tag(TAG).w("⚠️ currentUserId is still -1, waiting for login...")
        }
    }

    Timber.tag(TAG).d("Rendering ItemListScreen with ${items.size} items, userId=$currentUserId")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Items") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    Timber.tag(TAG).d("FAB clicked - navigating to AddItemScreen (userId=$currentUserId)")
                    onAddNewItem()
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No items yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap the + button to add your first password",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    ItemCard(
                        title = item.title,
                        username = item.username ?: "",
                        onClick = { onItemClick(item.id) },
                        onDelete = { showDeleteDialog = item.id }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { itemId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this item? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Timber.tag(TAG).d("Delete confirmed for item: $itemId")
                        // TODO: Implement delete via viewModel
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ItemCard(
    title: String,
    username: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (username.isNotBlank()) {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
