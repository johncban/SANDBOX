package com.jcb.passbook.presentation.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.screens.home.HomeScreen
import com.jcb.passbook.presentation.ui.screens.settings.SettingsScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemDetailScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel

/**
 * ✅ FIXED: Removed unused sessionManager parameter
 * Navigation is now fully managed by NavHost with proper Hilt injection
 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val ITEM_LIST = "item_list"
    const val ITEM_DETAIL = "item_detail"
    const val SETTINGS = "settings"
}

@Composable
fun PassbookNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    userViewModel: UserViewModel = hiltViewModel()
) {
    val authState by userViewModel.authState.collectAsStateWithLifecycle()

    val startDestination = if (authState is AuthState.Success) {
        Routes.HOME
    } else {
        Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // ========== AUTHENTICATION ROUTES ==========

        /**
         * ✅ FIXED (BUG-001): Removed sessionManager parameter
         * SessionManager is now injected via Hilt into LoginScreen
         */
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { userId ->
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        /**
         * ✅ FIXED (BUG-006): Removed unused sessionManager parameter
         * Registration flow simplified
         */
        composable(Routes.REGISTER) {
            RegistrationScreen(
                onRegistrationSuccess = { userId ->
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // ========== MAIN APP ROUTES ==========

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToItemList = {
                    navController.navigate(Routes.ITEM_LIST)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onLogout = {
                    userViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ITEM_LIST) {
            ItemListScreen(
                onItemClick = { item ->
                    navController.navigate("${Routes.ITEM_DETAIL}/${item.id}")
                },
                onAddItem = {
                    navController.navigate("${Routes.ITEM_DETAIL}/0")
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onLogout = {
                    userViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("${Routes.ITEM_DETAIL}/{itemId}") { backStackEntry ->
            val itemIdString = backStackEntry.arguments?.getString("itemId")
            val itemId = itemIdString?.toLongOrNull()
            ItemDetailScreen(
                itemId = if (itemId == 0L) null else itemId,
                onSaveSuccess = {
                    navController.popBackStack()
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onLogout = {
                    userViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
