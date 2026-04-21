package com.koard.android.navigation

import kotlinx.serialization.Serializable

sealed class NavigationRoutes {
    @Serializable
    data object Login : NavigationRoutes()

    @Serializable
    data object Tabs : NavigationRoutes()

    @Serializable
    data class TransactionDetails(val transactionId: String) : NavigationRoutes()
}
