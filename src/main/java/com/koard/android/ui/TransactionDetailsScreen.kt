package com.koard.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koard.android.ui.theme.KoardAndroidSDKTheme
import com.koard.android.ui.theme.KoardGreen800
import com.koard.android.ui.theme.KoardRed500
import com.koardlabs.merchant.sdk.domain.KoardTransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsScreen(
    transactionId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionDetailsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showRefundDialog by remember { mutableStateOf(false) }
    var showEmvRefundDialog by remember { mutableStateOf(false) }
    var emvRefundAmountInput by remember { mutableStateOf("") }
    var emvRefundErrorMessage by remember { mutableStateOf<String?>(null) }
    var transientErrorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(transactionId) {
        viewModel.onDispatch(TransactionDetailsIntent.LoadTransaction(transactionId))
    }

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionDetailsEffect.ShowError -> {
                    if (showEmvRefundDialog) {
                        emvRefundErrorMessage = effect.message
                    } else {
                        transientErrorMessage = effect.message
                    }
                }
                TransactionDetailsEffect.ShowRefundDialog -> {
                    showRefundDialog = true
                }
                TransactionDetailsEffect.ShowEmvRefundDialog -> {
                    showEmvRefundDialog = true
                    emvRefundAmountInput = uiState.transaction?.remainingRefundableAmount?.toString().orEmpty()
                    emvRefundErrorMessage = null
                }
                TransactionDetailsEffect.RefundSuccess -> {
                    // Handle refund success - could show a success message or navigate back
                }
                TransactionDetailsEffect.ReceiptSentSuccess -> {
                    // Handle receipt sent success - could show a success message
                }
            }
        }
    }

    LaunchedEffect(uiState.showTransactionSheet) {
        if (uiState.showTransactionSheet) {
            showEmvRefundDialog = false
            emvRefundErrorMessage = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Transaction Details", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = KoardGreen800,
                    titleContentColor = KoardGreen800
                )
            )
        }
    ) { paddingValues ->
        TransactionDetailsContent(modifier.padding(paddingValues), uiState, viewModel::onDispatch)
    }

    // Operation Modal
    uiState.operationModalState?.let { modalState ->
        PaymentOperationModal(
            modalState = modalState,
            onClose = { viewModel.onDispatch(TransactionDetailsIntent.OnCloseOperationModal) },
            onSubmit = { amount, tipType, tipPercentage ->
                viewModel.onDispatch(
                    TransactionDetailsIntent.OnProcessOperation(
                        operation = modalState.operation,
                        amount = amount,
                        tipType = tipType,
                        tipPercentage = tipPercentage,
                        activity = activity
                    )
                )
            }
        )
    }

    // Transaction processing sheet — same decoupled component as MainScreen
    if (uiState.showTransactionSheet) {
        TransactionProcessingSheet(
            title = uiState.activeTransactionTitle ?: "Transaction",
            amountLabel = uiState.activeTransactionAmountLabel,
            operationType = when (uiState.activeTransactionTitle) {
                "EMV Refund" -> TransactionSheetOperationType.REFUND
                "Complete Partial Auth" -> TransactionSheetOperationType.PARTIAL_AUTH_COMPLETION
                else -> TransactionSheetOperationType.UNKNOWN
            },
            transactionFlow = uiState.activeTransactionFlow,
            onClose = { result ->
                viewModel.onTransactionComplete(result)
            },
            onCancel = { viewModel.cancelPartialAuth() },
            onCancelButtonMetrics = { x, y, w, h ->
                viewModel.updateCancelButtonMetrics(x, y, w, h)
            }
        )
    }

    if (showRefundDialog) {
        Dialog(
            onDismissRequest = { showRefundDialog = false },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(.85f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 16.dp, horizontal = 32.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Refund ${uiState.transaction?.remainingRefundableFormatted.orEmpty()} ?",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                showRefundDialog = false
                                uiState.transaction?.let { transaction ->
                                    viewModel.onDispatch(TransactionDetailsIntent.OnProcessRefund)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Refund", color = KoardGreen800, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showRefundDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Cancel", color = KoardRed500, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showEmvRefundDialog) {
        Dialog(
            onDismissRequest = {
                showEmvRefundDialog = false
                emvRefundErrorMessage = null
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(.85f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 16.dp, horizontal = 32.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "EMV Refund",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Enter the amount to refund, then tap the card again to process it.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = emvRefundAmountInput,
                        onValueChange = {
                            emvRefundAmountInput = it
                            emvRefundErrorMessage = null
                        },
                        label = { Text("Refund Amount (cents)") },
                        placeholder = { Text(uiState.transaction?.remainingRefundableAmount?.toString().orEmpty()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = emvRefundErrorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    emvRefundErrorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val act = context.findActivity()
                                if (act != null) {
                                    val refundAmountCents = emvRefundAmountInput.trim().toIntOrNull()
                                    val maxAmount = uiState.transaction?.remainingRefundableAmount ?: 0

                                    when {
                                        refundAmountCents == null || refundAmountCents <= 0 -> {
                                            emvRefundErrorMessage = "Enter a valid refund amount in cents."
                                        }
                                        refundAmountCents > maxAmount -> {
                                            emvRefundErrorMessage = "Refund amount cannot exceed the remaining refundable amount in cents."
                                        }
                                        else -> {
                                            emvRefundErrorMessage = null
                                            viewModel.onDispatch(
                                                TransactionDetailsIntent.OnProcessEmvRefund(
                                                    activity = act,
                                                    amount = refundAmountCents
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Refund (EMV)", color = KoardGreen800, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                showEmvRefundDialog = false
                                emvRefundErrorMessage = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Cancel", color = KoardRed500, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    transientErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { transientErrorMessage = null },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { transientErrorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun TransactionDetailsContent(
    modifier: Modifier = Modifier,
    uiState: TransactionDetailsUiState,
    onDispatch: (TransactionDetailsIntent) -> Unit
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
                        text = "Error",
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

            uiState.transaction != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
//                    TransactionOverviewCard(uiState.transaction)
//                    PaymentDetailsCard(uiState.transaction)
//                    ProcessingDetailsCard(uiState.transaction)
//                    LocationDetailsCard(uiState.transaction)

                    TransactionDetailsLayout(
                        transactionDetailsUI = uiState.transaction,
                        receiptInputType = uiState.receiptInputType,
                        isSendEnabled = uiState.isSendEnabled,
                        isSmsSendEnabled = uiState.isSmsSendEnabled,
                        isSendingReceipt = uiState.isSendingReceipt,
                        onDispatch = onDispatch
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionOverviewCard(transaction: TransactionDetailsUI) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.date))
    val formattedAmount = String.format(Locale.getDefault(), "%.2f", transaction.amount / 100.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            DetailRow("Transaction ID", transaction.id)
            DetailRow("Amount", "$$formattedAmount ${transaction.currency}")
            DetailRow(
                "Status",
                transaction.status.displayName,
                valueColor = when (transaction.status) {
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
            DetailRow("Date", formattedDate)
            DetailRow("Merchant", transaction.merchant)
        }
    }
}

@Composable
private fun PaymentDetailsCard(transaction: TransactionDetailsUI) {
    val subtotalFormatted = String.format(Locale.getDefault(), "%.2f", transaction.subtotal / 100.0)
    val taxFormatted = String.format(Locale.getDefault(), "%.2f", transaction.taxAmount / 100.0)
    val tipFormatted = String.format(Locale.getDefault(), "%.2f", transaction.tipAmount / 100.0)
    val surchargeFormatted = String.format(Locale.getDefault(), "%.2f", transaction.surchargeAmount)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Payment Details",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            DetailRow("Card", transaction.card)
            DetailRow("Card Brand", transaction.cardBrand)
            DetailRow("Card Type", transaction.cardType)
            DetailRow("Payment Method", transaction.paymentMethod)

            if (transaction.subtotal > 0 || transaction.taxAmount > 0 || transaction.tipAmount > 0 || transaction.surchargeAmount > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (transaction.subtotal > 0) {
                    DetailRow("Subtotal", "$$subtotalFormatted")
                }
                if (transaction.taxAmount > 0) {
                    DetailRow("Tax", "$$taxFormatted (${transaction.taxRate}%)")
                }
                if (transaction.tipAmount > 0) {
                    DetailRow("Tip", "$$tipFormatted")
                }
                if (transaction.surchargeAmount > 0) {
                    DetailRow("Surcharge", "$$surchargeFormatted")
                }
            }

            if (transaction.refunded > 0 || transaction.reversed > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (transaction.refunded > 0) {
                    val refundedFormatted =
                        String.format(Locale.getDefault(), "%.2f", transaction.refunded / 100.0)
                    DetailRow("Refunded", "$$refundedFormatted", valueColor = Color(0xFFF6693E))
                }
                if (transaction.reversed > 0) {
                    val reversedFormatted =
                        String.format(Locale.getDefault(), "%.2f", transaction.reversed / 100.0)
                    DetailRow("Reversed", "$$reversedFormatted", valueColor = Color(0xFFF6693E))
                }
            }
        }
    }
}

@Composable
private fun ProcessingDetailsCard(transaction: TransactionDetailsUI) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Processing Details",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            DetailRow("Gateway", transaction.gateway)
            DetailRow("Processor", transaction.processor)
            if (transaction.approvalCode.isNotEmpty()) {
                DetailRow("Approval Code", transaction.approvalCode)
            }
            DetailRow("Response Code", transaction.processorResponseCode)
            if (transaction.processorResponseMessage.isNotEmpty()) {
                DetailRow("Response Message", transaction.processorResponseMessage)
            }
            if (transaction.gatewayResponseMessage.isNotEmpty()) {
                DetailRow("Gateway Response", transaction.gatewayResponseMessage)
            }
            if (transaction.statusReason.isNotEmpty()) {
                DetailRow("Status Reason", transaction.statusReason)
            }
        }
    }
}

@Composable
private fun LocationDetailsCard(transaction: TransactionDetailsUI) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Location & Device",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            DetailRow("Location ID", transaction.locationId)
            DetailRow("Device ID", transaction.deviceId)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun PaymentOperationModal(
    modalState: OperationModalState,
    onClose: () -> Unit,
    onSubmit: (amount: Int?, tipType: com.koardlabs.merchant.sdk.domain.AmountType?, tipPercentage: Double?) -> Unit
) {
    var amountInput by remember { mutableStateOf(modalState.suggestedAmount?.toString() ?: "") }
    var tipType by remember { mutableStateOf(com.koardlabs.merchant.sdk.domain.AmountType.FIXED) }
    var tipPercentageInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!modalState.isProcessing && modalState.result != null) onClose() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = modalState.operation.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KoardGreen800
                )

                if (modalState.result != null) {
                    // Show result
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(
                                if (modalState.result.success) android.R.drawable.ic_dialog_info
                                else android.R.drawable.ic_dialog_alert
                            ),
                            contentDescription = null,
                            tint = if (modalState.result.success) KoardGreen800 else KoardRed500,
                            modifier = Modifier.padding(8.dp)
                        )

                        Text(
                            text = if (modalState.result.success) "Success" else "Failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (modalState.result.success) KoardGreen800 else KoardRed500
                        )

                        Text(
                            text = modalState.result.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        modalState.result.transaction?.let { txn ->
                            HorizontalDivider()
                            Text(
                                text = "Updated Transaction",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            DetailRow("Status", txn.status.displayName)
                            DetailRow("Total", txn.totalFormatted)
                        }

                        Button(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KoardGreen800)
                        ) {
                            Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (modalState.isProcessing) {
                    // Show loading
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = KoardGreen800)
                        Text("Processing ${modalState.operation.displayName}...")
                    }
                } else {
                    // Show input form
                    when (modalState.operation) {
                        PaymentOperation.INCREMENTAL_AUTH -> {
                            Text("Enter additional amount to authorize (required):")
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                label = { Text("Amount (cents)") },
                                placeholder = { Text("e.g., 1000 for $10.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        PaymentOperation.COMPLETE_PARTIAL_AUTH -> {
                            Text("Enter the amount to authorize with a new card tap (defaults to remaining amount):")
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                label = { Text("Amount (cents)") },
                                placeholder = { Text("e.g., 2000 for $20.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        PaymentOperation.CAPTURE, PaymentOperation.REVERSE, PaymentOperation.REFUND -> {
                            Text("Enter amount (optional, leave empty for full amount):")
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                label = { Text("Amount (cents)") },
                                placeholder = { Text("e.g., 1000 for $10.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        PaymentOperation.ADJUST_TIP -> {
                            Text("Select tip type and enter value:")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { tipType = com.koardlabs.merchant.sdk.domain.AmountType.FIXED },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.FIXED)
                                            KoardGreen800 else Color.Gray
                                    )
                                ) {
                                    Text("Fixed ($)", fontWeight = if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.FIXED)
                                        FontWeight.Bold else FontWeight.Normal)
                                }
                                TextButton(
                                    onClick = { tipType = com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE)
                                            KoardGreen800 else Color.Gray
                                    )
                                ) {
                                    Text("Percentage (%)", fontWeight = if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE)
                                        FontWeight.Bold else FontWeight.Normal)
                                }
                            }

                            if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.FIXED) {
                                OutlinedTextField(
                                    value = amountInput,
                                    onValueChange = { amountInput = it },
                                    label = { Text("Tip Amount (cents)") },
                                    placeholder = { Text("e.g., 200 for $2.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                OutlinedTextField(
                                    value = tipPercentageInput,
                                    onValueChange = { tipPercentageInput = it },
                                    label = { Text("Tip Percentage") },
                                    placeholder = { Text("e.g., 15.5 for 15.5%") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KoardGreen800)
                        ) {
                            Text("Cancel", color = KoardGreen800, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val amount = amountInput.toIntOrNull()
                                val tipPercentage = tipPercentageInput.toDoubleOrNull()
                                onSubmit(amount, tipType, tipPercentage)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KoardGreen800),
                            enabled = when (modalState.operation) {
                                PaymentOperation.INCREMENTAL_AUTH, PaymentOperation.COMPLETE_PARTIAL_AUTH -> amountInput.isNotEmpty()
                                PaymentOperation.ADJUST_TIP -> if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.FIXED)
                                    amountInput.isNotEmpty() else tipPercentageInput.isNotEmpty()
                                else -> true
                            }
                        ) {
                            Text("Submit", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TransactionDetailsContentPreview() {
    val sampleTransaction = TransactionDetailsUI(
        id = "TXN-001-SAMPLE",
        amount = 2750,
        status = KoardTransactionStatus.CAPTURED,
        merchant = "Coffee Shop Downtown",
        card = "**** **** **** 1234",
        cardBrand = "Visa",
        cardType = "Credit",
        date = System.currentTimeMillis(),
        currency = "USD",
        subtotal = 2500,
        taxAmount = 200,
        taxRate = 8.0,
        tipAmount = 50,
        surchargeAmount = 0.0,
        gateway = "Square",
        processor = "Chase Paymentech",
        processorResponseCode = "00",
        processorResponseMessage = "Approved",
        statusReason = "Transaction completed successfully",
        paymentMethod = "Apple Pay",
        deviceId = "DEVICE-123",
        locationId = "LOC-456",
        refunded = 0,
        reversed = 0,
        approvalCode = "123456",
        gatewayResponseMessage = "Transaction approved",
        transactionType = "sale",
        simplifiedStatus = "Approved"
    )

    val sampleUiState = TransactionDetailsUiState(
        isLoading = false,
        transaction = sampleTransaction,
        error = null
    )

    KoardAndroidSDKTheme {
        TransactionDetailsContent(uiState = sampleUiState) {}
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
