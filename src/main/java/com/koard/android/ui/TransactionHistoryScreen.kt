package com.koard.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koard.android.R
import com.koard.android.ui.theme.KoardAndroidSDKTheme
import com.koard.android.ui.theme.KoardGreen800
import com.koardlabs.merchant.sdk.domain.KoardTransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
    onTransactionClick: (String) -> Unit = {},
    viewModel: TransactionHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onDispatch(TransactionHistoryIntent.LoadTransactions)
    }

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionHistoryEffect.ShowError -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.transaction_history), fontWeight = FontWeight.Bold)
                },
                navigationIcon = if (showBackButton && onNavigateBack != null) {
                    {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                } else {
                    { }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onDispatch(TransactionHistoryIntent.LoadTransactions) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = KoardGreen800,
                    titleContentColor = KoardGreen800,
                    actionIconContentColor = KoardGreen800
                )
            )
        }
    ) { paddingValues ->
        TransactionHistoryContent(
            modifier = Modifier.padding(paddingValues),
            uiState = uiState,
            onTransactionClick = onTransactionClick
        )
    }
}

@Composable
private fun TransactionHistoryContent(
    modifier: Modifier = Modifier,
    uiState: TransactionHistoryUiState,
    onTransactionClick: (String) -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
            }

            uiState.error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.error),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            uiState.transactions.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_transactions),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.no_transaction_history_available),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.transactions) { index, transaction ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp
                        ) {
                            TransactionItem(transaction, onTransactionClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: TransactionUI,
    onTransactionClick: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.date))
    val formattedAmount = String.format(Locale.getDefault(), "%.2f", transaction.amount / 100.0)

    Column(
        modifier = Modifier
            .clickable { onTransactionClick(transaction.id) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transaction.card,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = transaction.status.displayName,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = when (transaction.status) {
                    KoardTransactionStatus.PENDING -> Color(0xFFFFA000)
                    KoardTransactionStatus.AUTHORIZED -> Color(0xFFF6693E)
                    KoardTransactionStatus.CAPTURED -> KoardGreen800
                    KoardTransactionStatus.SETTLED -> KoardGreen800
                    KoardTransactionStatus.DECLINED -> Color(0xFFD32F2F)
                    KoardTransactionStatus.REFUNDED -> Color(0xFFF6693E)
                    KoardTransactionStatus.REVERSED -> Color(0xFFF6693E)
                    KoardTransactionStatus.CANCELED -> Color(0xFF616161)
                    KoardTransactionStatus.ERROR -> Color(0xFFD32F2F)
                    KoardTransactionStatus.SURCHARGE_PENDING -> Color(0xFFFFA000)
                    KoardTransactionStatus.UNKNOWN -> Color(0xFF9E9E9E)
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.amount_label, formattedAmount),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private class TransactionHistoryUiStateProvider : PreviewParameterProvider<TransactionHistoryUiState> {
    override val values = sequenceOf(
        // Loading state
        TransactionHistoryUiState(
            isLoading = true,
            transactions = emptyList(),
            error = null
        ),
        // Success state with transactions
        TransactionHistoryUiState(
            isLoading = false,
            transactions = listOf(
                TransactionUI(
                    id = "TXN-001",
                    amount = 2750,
                    status = KoardTransactionStatus.CAPTURED,
                    merchant = "Coffee Shop",
                    card = "**** **** **** 1234",
                    date = System.currentTimeMillis() - 3600000
                ),
                TransactionUI(
                    id = "TXN-002",
                    amount = 5999,
                    status = KoardTransactionStatus.AUTHORIZED,
                    merchant = "Gas Station",
                    card = "**** **** **** 5678",
                    date = System.currentTimeMillis() - 7200000
                ),
                TransactionUI(
                    id = "TXN-003",
                    amount = 1250,
                    status = KoardTransactionStatus.DECLINED,
                    merchant = "Restaurant",
                    card = "**** **** **** 9012",
                    date = System.currentTimeMillis() - 10800000
                ),
                TransactionUI(
                    id = "TXN-004",
                    amount = 8900,
                    status = KoardTransactionStatus.REFUNDED,
                    merchant = "Electronics Store",
                    card = "**** **** **** 3456",
                    date = System.currentTimeMillis() - 14400000
                ),
                TransactionUI(
                    id = "TXN-005",
                    amount = 4500,
                    status = KoardTransactionStatus.REVERSED,
                    merchant = "Grocery Store",
                    card = "**** **** **** 7890",
                    date = System.currentTimeMillis() - 18000000
                ),
                TransactionUI(
                    id = "TXN-006",
                    amount = 15000,
                    status = KoardTransactionStatus.CAPTURED,
                    merchant = "Department Store",
                    card = "**** **** **** 2468",
                    date = System.currentTimeMillis() - 21600000
                )
            ),
            error = null
        ),
        // Error state
        TransactionHistoryUiState(
            isLoading = false,
            transactions = emptyList(),
            error = "Failed to load transactions: Network error"
        ),
        // Empty state
        TransactionHistoryUiState(
            isLoading = false,
            transactions = emptyList(),
            error = null
        )
    )
}

@PreviewLightDark
@Composable
private fun TransactionHistoryContentPreview(
    @PreviewParameter(TransactionHistoryUiStateProvider::class) uiState: TransactionHistoryUiState
) {
    KoardAndroidSDKTheme {
        TransactionHistoryContent(uiState = uiState, onTransactionClick = {})
    }
}
