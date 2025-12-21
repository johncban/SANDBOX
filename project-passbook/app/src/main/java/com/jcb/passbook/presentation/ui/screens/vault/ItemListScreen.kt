package com.jcb.passbook.presentation.ui.screens.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategory
import com.jcb.passbook.presentation.ui.components.AccessibleCard
import com.jcb.passbook.presentation.ui.components.AccessibleIconButton
import com.jcb.passbook.presentation.ui.util.WindowWidthSizeClass
import com.jcb.passbook.presentation.ui.util.horizontalGap
import com.jcb.passbook.presentation.ui.util.rememberWindowSizeClasses
import com.jcb.passbook.presentation.ui.util.screenPadding
import com.jcb.passbook.presentation.viewmodel.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.ItemUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    modifier: Modifier = Modifier,
    viewModel: ItemViewModel = hiltViewModel(),
    onItemClick: (Item) -> Unit,
    onAddClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val (widthClass, _) = rememberWindowSizeClasses()
    val outerPadding = screenPadding()
    val gap = horizontalGap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Vault") },
                actions = {
                    AccessibleIconButton(
                        onClick = onAddClick,
                        contentDescription = "Add password"
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add password")
            }
        }
    ) { paddingValues ->
        when (widthClass) {
            WindowWidthSizeClass.Expanded -> {
                // Tablet / large devices: two-pane layout
                Row(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(outerPadding),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Left: search + filters
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                    ) {
                        SearchBar(
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::updateSearchQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        CategoryFilterRow(
                            selectedCategory = uiState.selectedCategory,
                            onCategorySelected = viewModel::filterByCategory,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Right: list content
                    ItemListContent(
                        uiState = uiState,
                        onItemClick = onItemClick,
                        onClearError = viewModel::clearError,
                        modifier = Modifier.weight(0.65f)
                    )
                }
            }

            else -> {
                // Phone / compact layout: vertical stack
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = outerPadding, vertical = outerPadding / 2)
                ) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    CategoryFilterRow(
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = viewModel::filterByCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    ItemListContent(
                        uiState = uiState,
                        onItemClick = onItemClick,
                        onClearError = viewModel::clearError,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemListContent(
    uiState: ItemUiState,
    onItemClick: (Item) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.items.isEmpty() -> {
                EmptyVaultMessage(modifier = Modifier.fillMaxSize())
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.items,
                        key = { it.id }
                    ) { item ->
                        ItemCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }

        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onClearError) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search passwords...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true
    )
}

@Composable
fun CategoryFilterRow(
    selectedCategory: PasswordCategory?,
    onCategorySelected: (PasswordCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") },
                leadingIcon = {
                    Icon(Icons.Default.List, contentDescription = null)
                }
            )
        }

        items(PasswordCategory.entries.size) { index ->
            val category = PasswordCategory.entries[index]
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category.displayName) },
                leadingIcon = {
                    Text(category.icon)
                }
            )
        }
    }
}

@Composable
fun ItemCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AccessibleCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.getPasswordCategoryEnum().icon,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite item",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                item.username?.let { username ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!item.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.getPasswordCategoryEnum().displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyVaultMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No passwords yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap + to add your first password",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
