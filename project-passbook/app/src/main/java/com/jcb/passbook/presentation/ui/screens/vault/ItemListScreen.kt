package com.jcb.passbook.presentation.ui.screens.vault

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import timber.log.Timber

private const val TAG = "ItemListScreen"

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onLogout: () -> Unit,
    onAddItem: () -> Unit,
    onItemClick: (Item) -> Unit,
    itemViewModel: ItemViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel()
) {
    // ✅ FIX: Get currentUserId from UserViewModel
    val currentUserId by userViewModel.currentUserId.collectAsStateWithLifecycle()
    val items by itemViewModel.items.collectAsStateWithLifecycle()

    // ✅ FIX: Set userId IMMEDIATELY when screen loads
    LaunchedEffect(currentUserId) {
        if (currentUserId != -1L) {
            Timber.tag(TAG).d("✅ Setting userId to ItemViewModel: $currentUserId")
            itemViewModel.setUserId(currentUserId)
        } else {
            Timber.tag(TAG).w("⚠️ currentUserId is -1 in ItemListScreen!")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Passwords") },
                actions = {
                    IconButton(onClick = {
                        userViewModel.logout()
                        itemViewModel.clearAllItems()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // ✅ FIX: Only allow navigation if user ID is set
                    if (currentUserId != -1L) {
                        Timber.tag(TAG).d("✅ Navigating to AddItemScreen with userId: $currentUserId")
                        onAddItem()
                    } else {
                        Timber.tag(TAG).e("❌ Cannot add item - user ID not set!")
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Password")
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No passwords yet",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap + to add your first password",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ItemCard(
    item: Item,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium
            )
            item.username?.let { username ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.url?.let { url ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
