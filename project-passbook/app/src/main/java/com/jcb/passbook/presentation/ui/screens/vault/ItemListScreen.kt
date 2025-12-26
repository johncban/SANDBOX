package com.jcb.passbook.presentation.ui.screens.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategoryEnum
import com.jcb.passbook.presentation.ui.components.AccessibleCard
import com.jcb.passbook.presentation.ui.components.AccessibleIconButton
import com.jcb.passbook.presentation.ui.responsive.getRecommendedColumnCount
import com.jcb.passbook.presentation.ui.responsive.isTabletMode
import com.jcb.passbook.presentation.ui.responsive.rememberScreenInfo
import com.jcb.passbook.presentation.ui.theme.getResponsiveDimensions
import com.jcb.passbook.presentation.viewmodel.vault.ItemUiState
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import kotlinx.coroutines.launch

/**
 * ItemListScreen - Refactored with Modal Bottom Sheet
 *
 * Features:
 * - Modal bottom sheet for viewing/editing passwords
 * - Show/hide password functionality in list view
 * - Logout button in top app bar
 * - Responsive grid/list layout
 * - Search and category filtering
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    modifier: Modifier = Modifier,
    viewModel: ItemViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onAddClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val screenInfo = rememberScreenInfo()
    val dimensions = getResponsiveDimensions(screenInfo.widthSizeClass)
    val isTablet = isTabletMode()
    val columnCount = getRecommendedColumnCount()

    // Modal bottom sheet state
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf<Item?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Vault") },
                actions = {
                    // Logout button
                    AccessibleIconButton(
                        onClick = onLogout,
                        contentDescription = "Logout"
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.size(dimensions.touchTargetSize)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add password")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = dimensions.paddingMedium,
                    vertical = dimensions.paddingSmall
                ),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingM)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth()
            )

            CategoryFilterRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = viewModel::filterByCategory,
                modifier = Modifier.fillMaxWidth()
            )

            // Adaptive layout: grid for tablets, list for phones
            if (isTablet && columnCount > 1) {
                ItemListContentGrid(
                    uiState = uiState,
                    columns = columnCount,
                    onItemClick = { item ->
                        selectedItem = item
                        isEditMode = false
                        showBottomSheet = true
                    },
                    onClearError = viewModel::clearError,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ItemListContentList(
                    uiState = uiState,
                    onItemClick = { item ->
                        selectedItem = item
                        isEditMode = false
                        showBottomSheet = true
                    },
                    onClearError = viewModel::clearError,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Modal Bottom Sheet for viewing/editing password
    if (showBottomSheet && selectedItem != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                isEditMode = false
                selectedItem = null
            },
            sheetState = sheetState,
            modifier = Modifier.fillMaxHeight(0.95f)
        ) {
            ItemBottomSheetContent(
                item = selectedItem!!,
                isEditMode = isEditMode,
                onEditModeChange = { isEditMode = it },
                onSave = { updatedItem ->
                    // ✅ FIXED: Changed from updateItem to insertOrUpdateItem
                    viewModel.insertOrUpdateItem(updatedItem)
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        showBottomSheet = false
                        isEditMode = false
                        selectedItem = null
                    }
                },
                onDelete = {
                    viewModel.deleteItem(selectedItem!!.id)
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        showBottomSheet = false
                        isEditMode = false
                        selectedItem = null
                    }
                },
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        showBottomSheet = false
                        isEditMode = false
                        selectedItem = null
                    }
                }
            )
        }
    }
}

@Composable
private fun ItemListContentList(
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
                        ItemCardWithPassword(
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
private fun ItemListContentGrid(
    uiState: ItemUiState,
    columns: Int,
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.items,
                        key = { it.id }
                    ) { item ->
                        ItemCardWithPassword(
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
    selectedCategory: PasswordCategoryEnum?,
    onCategorySelected: (PasswordCategoryEnum?) -> Unit,
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
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                }
            )
        }

        items(PasswordCategoryEnum.entries.size) { index ->
            val category = PasswordCategoryEnum.entries[index]
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

/**
 * ItemCardWithPassword - Shows password with show/hide toggle
 */
@Composable
fun ItemCardWithPassword(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Decrypt password (simplified - use actual decryption in production)
    val decryptedPassword = remember(item) {
        String(item.encryptedPassword)
    }

    AccessibleCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentDescription = "Password item: ${item.title}"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with icon, title, and favorite star
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite item",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Username/Email
            item.username?.let { username ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Password field with show/hide toggle
            Spacer(modifier = Modifier.height(12.dp))
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
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isPasswordVisible) decryptedPassword else "••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Show/Hide toggle button
                IconButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Notes preview (if available)
            if (!item.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Category label
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = item.getPasswordCategoryEnum().displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
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
