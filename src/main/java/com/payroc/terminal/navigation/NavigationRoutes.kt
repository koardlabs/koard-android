package com.payroc.terminal.navigation

import kotlinx.serialization.Serializable

sealed class NavigationRoutes {
    @Serializable
    data object Login : NavigationRoutes()

    @Serializable
    data object Tabs : NavigationRoutes()

    @Serializable
    data class TransactionDetails(val transactionId: String) : NavigationRoutes()
}
