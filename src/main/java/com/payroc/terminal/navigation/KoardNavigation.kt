package com.payroc.terminal.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.payroc.terminal.R
import com.payroc.terminal.ui.LoginScreen
import com.payroc.terminal.ui.MainScreen
import com.payroc.terminal.ui.SettingsScreen
import com.payroc.terminal.ui.TransactionDetailsScreen
import com.payroc.terminal.ui.TransactionHistoryScreen
import com.payroc.terminal.ui.theme.PayrocBlue
import com.payroc.terminal.ui.theme.PayrocDarkText
import com.payroc.terminal.ui.theme.PayrocLightGray
import com.payroc.terminal.ui.theme.PayrocMediumGray
import com.payroc.terminal.ui.theme.PayrocWhite
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
            val route = backStackEntry.toRoute<NavigationRoutes.TransactionDetails>()
            TransactionDetailsScreen(
                transactionId = route.transactionId,
                onNavigateBack = navController::popBackStack
            )
        }
    }
}

private data class TabItem(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private enum class HomeTab(val item: TabItem) {
    History(TabItem(R.string.tab_history, Icons.Filled.List, Icons.Outlined.List)),
    Home(TabItem(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home)),
    Settings(TabItem(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings))
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
        containerColor = PayrocWhite,
        bottomBar = {
            NavigationBar(
                containerColor = PayrocWhite,
                tonalElevation = 0.dp,
            ) {
                HomeTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.item.selectedIcon else tab.item.unselectedIcon,
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(tab.item.labelRes),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PayrocBlue,
                            selectedTextColor = PayrocBlue,
                            indicatorColor = PayrocBlue.copy(alpha = 0.1f),
                            unselectedIconColor = PayrocMediumGray,
                            unselectedTextColor = PayrocMediumGray
                        )
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
