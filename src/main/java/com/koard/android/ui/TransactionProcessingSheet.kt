package com.koard.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koard.android.ui.theme.KoardGreen800
import com.koard.android.ui.theme.KoardRed500
import com.koardlabs.merchant.sdk.domain.KoardReaderStatus
import com.koardlabs.merchant.sdk.domain.KoardTransaction
import com.koardlabs.merchant.sdk.domain.KoardTransactionActionStatus
import com.koardlabs.merchant.sdk.domain.KoardTransactionFinalStatus
import com.koardlabs.merchant.sdk.domain.KoardTransactionResponse
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Result passed back when the sheet closes.
 */
data class TransactionResult(
    val transaction: KoardTransaction? = null,
    val finalStatus: String? = null,
    val cancelled: Boolean = false,
    val isPartialApproval: Boolean = false,
    val partialTransactionId: String? = null,
    val partialRemainingAmount: Int = 0
)

enum class TransactionSheetOperationType {
    SALE,
    PREAUTH,
    REFUND,
    PARTIAL_AUTH_COMPLETION,
    UNKNOWN
}

/**
 * Self-contained transaction processing sheet.
 *
 * The ViewModel starts the SDK call (sale/preauth/completePartialAuth) and passes the
 * resulting Flow here. The sheet collects the Flow and handles ALL event processing:
 * retries, cancellation, completion, partial approval detection, display.
 *
 * @param transactionFlow The Flow from koardSdk.sale(), preauth(), or completePartialAuth()
 * @param onClose Called when the user taps Close with the final result
 * @param onCancel Called when the user taps the cancel X — should call koardSdk.cancelTransaction()
 * @param onTapAnotherCard Called when partial approval detected and user wants to tap again
 * @param onDismissPartialApproval Called when user cancels the partial approval
 * @param onCancelButtonMetrics Reports cancel button position for Visa kernel overlay (legacy)
 */
@Composable
fun TransactionProcessingSheet(
    title: String = "Transaction",
    amountLabel: String? = null,
    operationType: TransactionSheetOperationType = TransactionSheetOperationType.UNKNOWN,
    transactionFlow: Flow<KoardTransactionResponse>?,
    onClose: (TransactionResult) -> Unit,
    onCancel: () -> Unit,
    onTapAnotherCard: ((String, Int) -> Unit)? = null,
    onDismissPartialApproval: (() -> Unit)? = null,
    onCancelButtonMetrics: ((Int, Int, Int, Int) -> Unit)? = null
) {
    val density = LocalDensity.current
    val isRefundOperation = operationType == TransactionSheetOperationType.REFUND

    // The sheet owns ALL state
    var isProcessing by remember { mutableStateOf(true) }
    var tapAttempts by remember { mutableIntStateOf(0) }
    var readerStatus by remember { mutableStateOf<KoardReaderStatus?>(null) }
    var finalStatus by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var transaction by remember { mutableStateOf<KoardTransaction?>(null) }
    var lastResponse by remember { mutableStateOf<KoardTransactionResponse?>(null) }
    var isPartialApproval by remember { mutableStateOf(false) }
    var partialRemainingAmount by remember { mutableIntStateOf(0) }
    var partialTransactionId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) {}

    // Collect the Flow when available
    if (transactionFlow != null) {
    LaunchedEffect(transactionFlow) {
        isProcessing = true
        tapAttempts = 0
        readerStatus = null
        finalStatus = null
        errorMessage = null
        transaction = null
        lastResponse = null
        isPartialApproval = false
        partialRemainingAmount = 0
        partialTransactionId = null

        try {
            transactionFlow.collect { response ->
                lastResponse = response
                Timber.d("Sheet: action=%s reader=%s statusCode=%s final=%s",
                    response.actionStatus, response.readerStatus, response.statusCode, response.finalStatus)

                when (response.actionStatus) {
                    KoardTransactionActionStatus.OnProgress -> {
                        val isRetry = readerStatus == KoardReaderStatus.cardDetected &&
                            response.readerStatus == KoardReaderStatus.readyForTap
                        if (isRetry) tapAttempts++
                        readerStatus = response.readerStatus
                    }

                    KoardTransactionActionStatus.OnFailure -> {
                        transaction = response.transaction
                        val txn = response.transaction
                        val txnStatus = txn?.status
                        val isRefundSuccess = isRefundOperation && (
                            txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.CAPTURED ||
                                txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.AUTHORIZED ||
                                txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.REFUNDED
                            ) &&
                            txn?.statusReason.equals("approved", ignoreCase = true)
                        val isRetryable = response.statusCode == 12 ||
                            response.statusCode == null ||
                            response.statusCode == 53 ||
                            response.statusCode == 42
                        if (isRetryable) {
                            tapAttempts++
                            readerStatus = KoardReaderStatus.readyForTap
                            return@collect
                        }

                        val isCancellation = isExplicitCancellation(response)
                        val isDeclined = response.finalStatus is KoardTransactionFinalStatus.Decline ||
                            txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.DECLINED
                        val failureMessage = response.displayMessage ?: "Transaction failed"

                        isProcessing = false
                        if (isCancellation) {
                            finalStatus = "Cancelled"
                            errorMessage = null
                        } else if (isRefundSuccess) {
                            finalStatus = "Refunded"
                            errorMessage = null
                        } else if (isDeclined) {
                            finalStatus = "Declined"
                            errorMessage = response.displayMessage
                        } else {
                            finalStatus = "Failed"
                            errorMessage = failureMessage
                        }
                    }

                    KoardTransactionActionStatus.OnComplete -> {
                        isProcessing = false
                        transaction = response.transaction
                        val txn = response.transaction
                        val txnStatus = txn?.status
                        val isRefundSuccess = isRefundOperation && (
                            txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.CAPTURED ||
                                txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.AUTHORIZED ||
                                txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.REFUNDED
                            ) &&
                            txn?.statusReason.equals("approved", ignoreCase = true)

                        when {
                            isExplicitCancellation(response) -> {
                                finalStatus = "Cancelled"
                                errorMessage = null
                            }
                            txn != null && txn.statusReason == "partial_approval" &&
                                txn.gatewayTransactionResponse.authorizedAmount < txn.totalAmount -> {
                                isPartialApproval = true
                                partialRemainingAmount = txn.totalAmount - txn.gatewayTransactionResponse.authorizedAmount
                                partialTransactionId = txn.transactionId
                                finalStatus = "Partial Approval"
                            }
                            txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.ERROR -> {
                                finalStatus = "Failed"
                                errorMessage = response.displayMessage ?: "Transaction failed"
                            }
                            isRefundSuccess -> {
                                finalStatus = "Refunded"
                                errorMessage = null
                            }
                            response.finalStatus is KoardTransactionFinalStatus.Decline ||
                                txnStatus == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.DECLINED -> {
                                finalStatus = "Declined"
                                errorMessage = response.displayMessage
                            }
                            response.finalStatus is KoardTransactionFinalStatus.Failure -> {
                                finalStatus = "Failed"
                                errorMessage = response.displayMessage
                            }
                            txn == null -> {
                                finalStatus = "Failed"
                                errorMessage = response.displayMessage ?: "Transaction completed without backend transaction data."
                            }
                            else -> {
                                finalStatus = txn?.status?.displayName ?: "Approved"
                                errorMessage = null
                            }
                        }
                    }

                    else -> {
                        readerStatus = response.readerStatus
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Transaction flow failed")
            isProcessing = false
            finalStatus = "Failed"
            errorMessage = e.message ?: "Unknown error"
        }
    }
    }

    // --- UI ---
    val statusTitle = resolveStatusTitle(isProcessing, tapAttempts, readerStatus, finalStatus, errorMessage)
    val statusSubtitle = resolveStatusSubtitle(isProcessing, tapAttempts, errorMessage)
    val statusColor = resolveStatusColor(isProcessing, finalStatus, errorMessage)
    val resolvedRefundAmount = if (isRefundOperation) {
        transaction?.refunded?.takeIf { it > 0 } ?: parseAmountLabelToCents(amountLabel)
    } else {
        null
    }
    val resolvedAmountLabel = if (isRefundOperation && resolvedRefundAmount != null) {
        formatCentsToUSD(resolvedRefundAmount)
    } else {
        amountLabel
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProcessing && finalStatus == null && errorMessage == null) {
                    // Cancel icon — the Visa kernel places a transparent overlay here
                    // that handles the actual tap. We still call cancel for consistency.
                    IconButton(
                        onClick = {
                            onCancel()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionOnScreen()
                                val size: IntSize = coordinates.size
                                val xDp = with(density) { pos.x.toDp().value.roundToInt() }
                                val yDp = with(density) { pos.y.toDp().value.roundToInt() }
                                val widthDp = with(density) { size.width.toDp().value.roundToInt() }
                                val heightDp = with(density) { size.height.toDp().value.roundToInt() }
                                onCancelButtonMetrics?.invoke(xDp, yDp, widthDp, heightDp)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel transaction",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            onClose(TransactionResult(
                                transaction = transaction,
                                finalStatus = finalStatus,
                                cancelled = finalStatus == "Cancelled",
                                isPartialApproval = isPartialApproval,
                                partialTransactionId = partialTransactionId,
                                partialRemainingAmount = partialRemainingAmount
                            ))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close transaction sheet",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Amount label
            resolvedAmountLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }

            // Spinner
            if (isProcessing && finalStatus == null) {
                CircularProgressIndicator(
                    color = KoardGreen800,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Status
            Text(
                text = statusTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = statusColor,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            statusSubtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
            }

            // Transaction details
            if (finalStatus != null || errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF7F7F7)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        transaction?.let { txn ->
                            val responseCode = txn.processorResponseCode
                                .ifBlank { txn.gatewayTransactionResponse.responseCode }
                                .ifBlank { txn.gatewayTransactionResponse.processorResponseCode }
                            val responseMessage = txn.processorResponseMessage
                                .ifBlank { txn.gatewayTransactionResponse.responseMessage }

                            if (isRefundOperation && resolvedRefundAmount != null) {
                                SheetDetailRow("Refunded", formatCentsToUSD(resolvedRefundAmount), valueBold = true)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                            SheetDetailRow("Subtotal", formatCentsToUSD(txn.subtotal))
                            if (txn.tipAmount > 0) SheetDetailRow("Tip", formatCentsToUSD(txn.tipAmount))
                            if (txn.taxAmount > 0) SheetDetailRow("Tax", formatCentsToUSD(txn.taxAmount))
                            if (txn.surchargeAmount > 0) SheetDetailRow("Surcharge", formatCentsToUSD(txn.surchargeAmount.toInt()))

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                                Text(formatCentsToUSD(txn.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            SheetDetailRow("Status", txn.status.displayName, valueBold = true)
                            if (txn.statusReason.isNotBlank()) SheetDetailRow("Reason", txn.statusReason)
                            if (responseCode.isNotBlank()) SheetDetailRow("Response Code", responseCode)
                            if (responseMessage.isNotBlank()) SheetDetailRow("Response", responseMessage)
                            if (txn.gatewayTransactionResponse.approvalCode.isNotBlank()) SheetDetailRow("Approval Code", txn.gatewayTransactionResponse.approvalCode)
                        }

                        if (transaction == null) {
                            SheetDetailRow("Status", finalStatus ?: "Failed", valueBold = true)
                            errorMessage?.takeIf { it.isNotBlank() }?.let { SheetDetailRow("Message", it) }
                        }

                        lastResponse?.let { response ->
                            val hasTransactionDetails = transaction != null
                            val hasSdkDetails = response.statusCode != null ||
                                !response.statusCodeDescription.isNullOrBlank() ||
                                response.actionStatus != null ||
                                response.finalStatus != null ||
                                response.readerStatus != KoardReaderStatus.unknown ||
                                !response.displayMessage.isNullOrBlank()

                            if (hasSdkDetails) {
                                if (hasTransactionDetails || transaction == null) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                                SheetDetailRow("Reader Status", response.readerStatus.name)
                                response.actionStatus?.let { SheetDetailRow("Action", it.displayName()) }
                                response.finalStatus?.let { SheetDetailRow("Final Status", it.displayName()) }
                                response.statusCode?.let { SheetDetailRow("Status Code", it.toString()) }
                                response.statusCodeDescription
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { SheetDetailRow("Status Description", it) }
                                response.displayMessage
                                    ?.takeIf { it.isNotBlank() && it != errorMessage }
                                    ?.let { SheetDetailRow("Display Message", it) }
                            }
                        }
                    }
                }
            }

            // Partial approval
            if (isPartialApproval && !isProcessing) {
                Text(
                    text = "Remaining: ${formatCentsToUSD(partialRemainingAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KoardRed500
                )
                Text(
                    text = "Tap another card to pay the remaining amount.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
                if (onTapAnotherCard != null && partialTransactionId != null) {
                    Button(
                        onClick = { onTapAnotherCard(partialTransactionId!!, partialRemainingAmount) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KoardGreen800)
                    ) {
                        Text("Tap Another Card", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (onDismissPartialApproval != null) {
                    OutlinedButton(
                        onClick = onDismissPartialApproval,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, KoardRed500)
                    ) {
                        Text("Cancel", color = KoardRed500)
                    }
                }
            }

            // Close button
            if (!isProcessing && !isPartialApproval && (errorMessage != null || finalStatus != null)) {
                Button(
                    onClick = {
                        onClose(TransactionResult(
                            transaction = transaction,
                            finalStatus = finalStatus,
                            cancelled = finalStatus == "Cancelled",
                            isPartialApproval = isPartialApproval,
                            partialTransactionId = partialTransactionId,
                            partialRemainingAmount = partialRemainingAmount
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KoardGreen800)
                ) {
                    Text("Close")
                }
            }
        }
        }
    }
}

// --- Helpers ---

private fun resolveStatusTitle(
    isProcessing: Boolean, tapAttempts: Int, readerStatus: KoardReaderStatus?,
    finalStatus: String?, errorMessage: String?
): String {
    if (errorMessage != null && finalStatus == null) return "Transaction failed"
    if (finalStatus != null) return finalStatus
    if (isProcessing) {
        return when (readerStatus) {
            KoardReaderStatus.preparing -> "Preparing"
            KoardReaderStatus.readyForTap -> if (tapAttempts > 0) "Please tap again" else "Ready for tap"
            KoardReaderStatus.cardDetected -> "Card detected"
            KoardReaderStatus.pinEntryRequested -> "Enter PIN"
            KoardReaderStatus.pinEntryCompleted -> "PIN captured"
            KoardReaderStatus.readCompleted -> "Read complete"
            KoardReaderStatus.readNotCompleted -> "Read failed"
            KoardReaderStatus.readCancelled -> "Cancelled"
            KoardReaderStatus.readRetry -> "Retry tap"
            KoardReaderStatus.processing -> "Processing"
            KoardReaderStatus.complete -> "Completing"
            KoardReaderStatus.developerModeEnabled -> "Blocked"
            KoardReaderStatus.unknown -> "Processing"
            null -> "Preparing"
        }
    }
    return "Transaction"
}

private fun resolveStatusSubtitle(isProcessing: Boolean, tapAttempts: Int, errorMessage: String?): String? {
    if (tapAttempts > 0 && isProcessing) return "Attempt ${tapAttempts + 1} — hold card steady on phone"
    return null
}

private fun isExplicitCancellation(response: KoardTransactionResponse): Boolean {
    if (response.statusCode == 46 || response.statusCode == 102) return true
    if (response.finalStatus != KoardTransactionFinalStatus.Abort) return false

    val message = listOfNotNull(
        response.displayMessage,
        response.statusCodeDescription
    ).joinToString(" ").lowercase()

    return message.contains("cancel") ||
        message.contains("cancelled") ||
        message.contains("canceled") ||
        message.contains("user abort")
}

private fun parseAmountLabelToCents(amountLabel: String?): Int? {
    if (amountLabel.isNullOrBlank()) return null
    val match = Regex("""(\d+(?:\.\d{1,2})?)""").find(amountLabel) ?: return null
    val dollars = match.groupValues[1].toDoubleOrNull() ?: return null
    return (dollars * 100).roundToInt()
}

private fun resolveStatusColor(isProcessing: Boolean, finalStatus: String?, errorMessage: String?): Color {
    return when {
        errorMessage != null && finalStatus == null -> KoardRed500
        finalStatus?.contains("Partial", ignoreCase = true) == true -> Color(0xFFE65100)
        finalStatus?.contains("Cancel", ignoreCase = true) == true -> Color.Black
        finalStatus?.contains("Approve", ignoreCase = true) == true ||
            finalStatus?.contains("Refund", ignoreCase = true) == true ||
            finalStatus?.contains("Captured", ignoreCase = true) == true ||
            finalStatus?.contains("Settled", ignoreCase = true) == true -> KoardGreen800
        isProcessing -> KoardGreen800
        else -> Color.Black
    }
}

private fun KoardTransactionActionStatus.displayName(): String {
    return when (this) {
        KoardTransactionActionStatus.OnProgress -> "Progress"
        KoardTransactionActionStatus.OnFailure -> "Failure"
        KoardTransactionActionStatus.OnComplete -> "Complete"
    }
}

private fun KoardTransactionFinalStatus.displayName(): String {
    return when (this) {
        KoardTransactionFinalStatus.Approve -> "Approve"
        KoardTransactionFinalStatus.Abort -> "Abort"
        KoardTransactionFinalStatus.Decline -> "Decline"
        KoardTransactionFinalStatus.Failure -> "Failure"
        KoardTransactionFinalStatus.AltService -> "Alt Service"
        is KoardTransactionFinalStatus.Unknown -> rawStatus
    }
}

@Composable
private fun SheetDetailRow(label: String, value: String, valueBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (valueBold) FontWeight.SemiBold else FontWeight.Normal, color = Color.Black)
    }
}

fun formatCentsToUSD(cents: Int): String {
    val dollars = cents / 100.0
    return String.format("$%.2f", dollars)
}

/**
 * Default cancel button properties for the TransactionProcessingSheet.
 * Uses fallback coordinates that match the sheet's layout.
 * After the first transaction, actual coordinates from onGloballyPositioned should be used.
 */
fun defaultCancelButtonProperties(): List<com.visa.kic.sdk.common.ipc.ButtonProperties> {
    return listOf(com.visa.kic.sdk.common.ipc.ButtonProperties("Cancel", 24, 48, 48, 48))
}
