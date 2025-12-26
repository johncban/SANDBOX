package com.jcb.passbook.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemDetailsScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen

/**
 * Main navigation graph for Passbook app
 * Uses actual existing screens from the repository
 */
@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // Authentication screens
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("itemList") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("registration")
                }
            )
        }

        composable("registration") {
            RegistrationScreen(
                onRegistrationSuccess = {
                    navController.navigate("itemList") {
                        popUpTo("registration") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // Vault screens - using actual existing screens
        composable("itemList") {
            ItemListScreen(
                onItemClick = { itemId ->
                    navController.navigate("itemDetails/$itemId")
                },
                onAddNewItem = {
                    navController.navigate("itemDetails/0") // 0 for new item
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "itemDetails/{itemId}",
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
            ItemDetailsScreen(
                itemId = itemId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveSuccess = {
                    navController.popBackStack()
                }
            )
        }
    }
}
