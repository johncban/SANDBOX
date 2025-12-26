package com.jcb.passbook.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemDetailsScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // ✅ FIXED: Authentication screens with correct parameters
        composable("login") {
            LoginScreen(
                userViewModel = hiltViewModel(), // ✅ Added missing parameter
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
                userViewModel = hiltViewModel(), // ✅ Added missing parameter
                onRegisterSuccess = { // ✅ Fixed parameter name
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

        // ✅ FIXED: Vault screens with correct parameters
        composable("itemList") {
            ItemListScreen(
                onLogout = { // ✅ Fixed parameter name
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onAddClick = { // ✅ Fixed parameter name
                    navController.navigate("itemDetails/0")
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
