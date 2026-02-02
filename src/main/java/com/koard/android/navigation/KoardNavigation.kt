package com.koard.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.koard.android.R
import com.koard.android.ui.LoginScreen
import com.koard.android.ui.MainScreen
import com.koard.android.ui.SettingsScreen
import com.koard.android.ui.TransactionDetailsScreen
import com.koard.android.ui.TransactionHistoryScreen
import com.koardlabs.merchant.sdk.KoardMerchantSdk

@Composable
fun KoardNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val sdk = KoardMerchantSdk.getInstance()
    val isAuthenticated = sdk.isAuthenticated

    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated) NavigationRoutes.Tabs else NavigationRoutes.Login,
        modifier = modifier
    ) {
        composable<NavigationRoutes.Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavigationRoutes.Tabs) {
                        popUpTo(NavigationRoutes.Login) { inclusive = true }
                    }
                }
            )
        }

        composable<NavigationRoutes.Tabs> {
            TabsRoot(
                onTransactionSelected = { transactionId ->
                    navController.navigate(NavigationRoutes.TransactionDetails(transactionId))
                },
                onLogout = {
                    navController.navigate(NavigationRoutes.Login) {
                        popUpTo(NavigationRoutes.Tabs) { inclusive = true }
                    }
                }
            )
        }

        composable<NavigationRoutes.TransactionDetails> { backStackEntry ->
            val transactionDetails = backStackEntry.toRoute<NavigationRoutes.TransactionDetails>()
            TransactionDetailsScreen(
                transactionId = transactionDetails.transactionId,
                onNavigateBack = navController::popBackStack
            )
        }
    }
}

private enum class HomeTab(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    History(R.string.tab_history, Icons.Default.List),
    Home(R.string.tab_home, Icons.Default.Home),
    Settings(R.string.tab_settings, Icons.Default.Settings)
}

@Composable
private fun TabsRoot(
    onTransactionSelected: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Home) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                HomeTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            HomeTab.History -> TransactionHistoryScreen(
                modifier = Modifier.padding(paddingValues),
                showBackButton = false,
                onTransactionClick = onTransactionSelected
            )

            HomeTab.Home -> MainScreen(
                modifier = Modifier.padding(paddingValues)
            )

            HomeTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(paddingValues),
                onLogout = onLogout
            )
        }
    }
}
