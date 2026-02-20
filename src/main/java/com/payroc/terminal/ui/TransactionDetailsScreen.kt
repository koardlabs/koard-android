package com.payroc.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.payroc.terminal.ui.theme.PayrocBlue
import com.payroc.terminal.ui.theme.PayrocDarkText
import com.payroc.terminal.ui.theme.PayrocLightGray
import com.payroc.terminal.ui.theme.PayrocLightestGray
import com.payroc.terminal.ui.theme.PayrocMediumGray
import com.payroc.terminal.ui.theme.PayrocNavy
import com.payroc.terminal.ui.theme.PayrocRed
import com.payroc.terminal.ui.theme.PayrocWhite
import com.payroc.terminal.ui.theme.StatusGreen
import com.koardlabs.merchant.sdk.domain.KoardTransactionStatus

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

    LaunchedEffect(transactionId) {
        viewModel.onDispatch(TransactionDetailsIntent.LoadTransaction(transactionId))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionDetailsEffect.ShowError -> Unit
                TransactionDetailsEffect.ShowRefundDialog -> showRefundDialog = true
                TransactionDetailsEffect.RefundSuccess -> Unit
                TransactionDetailsEffect.ReceiptSentSuccess -> Unit
            }
        }
    }

    Scaffold(
        containerColor = PayrocLightestGray,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Transaction Details", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PayrocNavy,
                    navigationIconContentColor = PayrocWhite,
                    titleContentColor = PayrocWhite
                )
            )
        }
    ) { paddingValues ->
        TransactionDetailsContent(modifier.padding(paddingValues), uiState, viewModel::onDispatch)
    }

    // Operation modal
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
                        tipPercentage = tipPercentage
                    )
                )
            }
        )
    }

    // Refund confirm dialog
    if (showRefundDialog) {
        Dialog(onDismissRequest = { showRefundDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(.9f),
                shape = RoundedCornerShape(20.dp),
                color = PayrocWhite
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(PayrocRed.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↩", fontSize = 24.sp, color = PayrocRed)
                    }

                    Text(
                        "Refund ${uiState.transaction?.totalFormatted.orEmpty()}?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PayrocDarkText,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        "This action cannot be undone.",
                        fontSize = 14.sp,
                        color = PayrocMediumGray,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRefundDialog = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PayrocLightGray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PayrocMediumGray)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                showRefundDialog = false
                                viewModel.onDispatch(TransactionDetailsIntent.OnProcessRefund)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PayrocRed, contentColor = PayrocWhite)
                        ) {
                            Text("Refund", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
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
                CircularProgressIndicator(color = PayrocBlue)
            }

            uiState.error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("Unable to Load", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PayrocDarkText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error, fontSize = 14.sp, color = PayrocMediumGray, textAlign = TextAlign.Center)
                }
            }

            uiState.transaction != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status header card
                    StatusHeaderCard(transaction = uiState.transaction!!)

                    // Transaction details layout (existing)
                    TransactionDetailsLayout(
                        transactionDetailsUI = uiState.transaction!!,
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
private fun StatusHeaderCard(transaction: TransactionDetailsUI) {
    val isSuccess = when (transaction.status) {
        KoardTransactionStatus.CAPTURED, KoardTransactionStatus.SETTLED -> true
        else -> false
    }
    val isDeclined = when (transaction.status) {
        KoardTransactionStatus.DECLINED, KoardTransactionStatus.ERROR -> true
        else -> false
    }

    val iconBg = when {
        isSuccess -> StatusGreen
        isDeclined -> PayrocRed
        else -> PayrocBlue
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PayrocWhite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = PayrocWhite,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = transaction.totalFormatted,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = PayrocNavy
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = transaction.status.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = iconBg
            )
        }
    }
}

@Composable
private fun PaymentOperationModal(
    modalState: OperationModalState,
    onClose: () -> Unit,
    onSubmit: (amount: Int?, tipType: com.koardlabs.merchant.sdk.domain.AmountType?, tipPercentage: Double?) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var tipType by remember { mutableStateOf(com.koardlabs.merchant.sdk.domain.AmountType.FIXED) }
    var tipPercentageInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!modalState.isProcessing && modalState.result != null) onClose() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = PayrocWhite
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = modalState.operation.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PayrocNavy
                )

                if (modalState.result != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (modalState.result.success) StatusGreen else PayrocRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (modalState.result.success) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = PayrocWhite,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = if (modalState.result.success) "Success" else "Failed",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (modalState.result.success) StatusGreen else PayrocRed
                        )

                        Text(
                            text = modalState.result.message,
                            fontSize = 14.sp,
                            color = PayrocMediumGray,
                            textAlign = TextAlign.Center
                        )

                        modalState.result.transaction?.let { txn ->
                            HorizontalDivider(color = PayrocLightGray)
                            DetailRow("Status", txn.status.displayName)
                            DetailRow("Total", txn.totalFormatted)
                        }

                        Button(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PayrocBlue, contentColor = PayrocWhite)
                        ) {
                            Text("Close", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (modalState.isProcessing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = PayrocBlue)
                        Text("Processing ${modalState.operation.displayName}...", color = PayrocMediumGray)
                    }
                } else {
                    when (modalState.operation) {
                        PaymentOperation.INCREMENTAL_AUTH -> {
                            Text("Additional amount to authorize:", fontSize = 14.sp, color = PayrocMediumGray)
                            OperationTextField(value = amountInput, onValueChange = { amountInput = it }, label = "Amount (cents)", placeholder = "e.g. 1000 for \$10.00")
                        }
                        PaymentOperation.CAPTURE, PaymentOperation.REVERSE, PaymentOperation.REFUND -> {
                            Text("Amount (optional — leave blank for full amount):", fontSize = 14.sp, color = PayrocMediumGray)
                            OperationTextField(value = amountInput, onValueChange = { amountInput = it }, label = "Amount (cents)", placeholder = "e.g. 1000 for \$10.00")
                        }
                        PaymentOperation.ADJUST_TIP -> {
                            Text("Select tip type:", fontSize = 14.sp, color = PayrocMediumGray)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isFixed = tipType == com.koardlabs.merchant.sdk.domain.AmountType.FIXED
                                TipTypeChip("Fixed ($)", isFixed) { tipType = com.koardlabs.merchant.sdk.domain.AmountType.FIXED }
                                TipTypeChip("Percentage (%)", !isFixed) { tipType = com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE }
                            }
                            if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.FIXED) {
                                OperationTextField(amountInput, { amountInput = it }, "Tip Amount (cents)", "e.g. 200 for \$2.00")
                            } else {
                                OperationTextField(tipPercentageInput, { tipPercentageInput = it }, "Tip Percentage", "e.g. 15.5 for 15.5%", keyboardType = KeyboardType.Decimal)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PayrocLightGray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PayrocMediumGray)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                onSubmit(
                                    amountInput.toIntOrNull(),
                                    tipType,
                                    tipPercentageInput.toDoubleOrNull()
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PayrocBlue, contentColor = PayrocWhite),
                            enabled = when (modalState.operation) {
                                PaymentOperation.INCREMENTAL_AUTH -> amountInput.isNotEmpty()
                                PaymentOperation.ADJUST_TIP ->
                                    if (tipType == com.koardlabs.merchant.sdk.domain.AmountType.FIXED) amountInput.isNotEmpty()
                                    else tipPercentageInput.isNotEmpty()
                                else -> true
                            }
                        ) {
                            Text("Submit", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipTypeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isSelected) PayrocBlue else PayrocMediumGray
        )
    ) {
        Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
    }
}

@Composable
private fun OperationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = PayrocLightGray) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PayrocBlue,
            unfocusedBorderColor = PayrocLightGray,
            focusedLabelColor = PayrocBlue,
            unfocusedLabelColor = PayrocMediumGray,
            cursorColor = PayrocBlue,
            focusedTextColor = PayrocDarkText,
            unfocusedTextColor = PayrocDarkText
        )
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 14.sp, color = PayrocMediumGray, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PayrocDarkText,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}
