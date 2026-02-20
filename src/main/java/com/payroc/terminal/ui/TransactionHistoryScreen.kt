package com.payroc.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.payroc.terminal.R
import com.payroc.terminal.ui.theme.PayrocBlue
import com.payroc.terminal.ui.theme.PayrocDarkText
import com.payroc.terminal.ui.theme.PayrocLightGray
import com.payroc.terminal.ui.theme.PayrocLightestGray
import com.payroc.terminal.ui.theme.PayrocMediumGray
import com.payroc.terminal.ui.theme.PayrocNavy
import com.payroc.terminal.ui.theme.PayrocRed
import com.payroc.terminal.ui.theme.PayrocWhite
import com.payroc.terminal.ui.theme.StatusAmber
import com.payroc.terminal.ui.theme.StatusGray
import com.payroc.terminal.ui.theme.StatusGreen
import com.payroc.terminal.ui.theme.StatusOrange
import com.payroc.terminal.ui.theme.StatusRed
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

    LaunchedEffect(Unit) { viewModel.onDispatch(TransactionHistoryIntent.LoadTransactions) }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) { is TransactionHistoryEffect.ShowError -> Unit }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = PayrocLightestGray,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.transaction_history),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = if (showBackButton && onNavigateBack != null) {
                    {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                } else {
                    { }
                },
                actions = {
                    IconButton(onClick = { viewModel.onDispatch(TransactionHistoryIntent.LoadTransactions) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PayrocNavy,
                    navigationIconContentColor = PayrocWhite,
                    titleContentColor = PayrocWhite,
                    actionIconContentColor = PayrocWhite.copy(alpha = 0.8f)
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
                CircularProgressIndicator(color = PayrocBlue)
            }

            uiState.error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Unable to Load",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayrocDarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error,
                        fontSize = 14.sp,
                        color = PayrocMediumGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            uiState.transactions.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(PayrocBlue.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PayrocBlue.copy(alpha = 0.4f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Transactions Yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayrocDarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Transactions you process will appear here.",
                        fontSize = 14.sp,
                        color = PayrocMediumGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(uiState.transactions) { _, transaction ->
                        TransactionCard(transaction = transaction, onTransactionClick = onTransactionClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionUI,
    onTransactionClick: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy  h:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.date))
    val formattedAmount = "$%.2f".format(transaction.amount / 100.0)

    val statusColor = statusColorFor(transaction.status)
    val statusBg = statusColor.copy(alpha = 0.1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PayrocWhite)
            .clickable { onTransactionClick(transaction.id) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Card info
            Column {
                Text(
                    text = transaction.card,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PayrocDarkText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = PayrocMediumGray
                )
            }

            // Amount
            Text(
                text = formattedAmount,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PayrocNavy
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = transaction.status.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }

            // Chevron hint
            Text("›", fontSize = 18.sp, color = PayrocLightGray, fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusColorFor(status: KoardTransactionStatus): Color = when (status) {
    KoardTransactionStatus.PENDING -> StatusAmber
    KoardTransactionStatus.AUTHORIZED -> StatusOrange
    KoardTransactionStatus.CAPTURED -> StatusGreen
    KoardTransactionStatus.SETTLED -> StatusGreen
    KoardTransactionStatus.DECLINED -> StatusRed
    KoardTransactionStatus.REFUNDED -> StatusOrange
    KoardTransactionStatus.REVERSED -> StatusOrange
    KoardTransactionStatus.CANCELED -> StatusGray
    KoardTransactionStatus.ERROR -> StatusRed
    KoardTransactionStatus.SURCHARGE_PENDING -> StatusAmber
    KoardTransactionStatus.UNKNOWN -> StatusGray
}
