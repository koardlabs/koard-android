package com.payroc.terminal.ui

import android.app.Application
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.payroc.terminal.R
import com.payroc.terminal.utils.isValidEmail
import com.payroc.terminal.utils.isValidUSPhoneNumber
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import com.koardlabs.merchant.sdk.domain.KoardTransaction
import com.koardlabs.merchant.sdk.domain.KoardTransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TransactionDetailsUiState())
    val uiState: StateFlow<TransactionDetailsUiState> = _uiState.asStateFlow()

    private val intents = MutableSharedFlow<TransactionDetailsIntent>()

    private val _effects = Channel<TransactionDetailsEffect>()
    val effects = _effects.receiveAsFlow()

    private val koardSdk = KoardMerchantSdk.getInstance()

    init {
        viewModelScope.launch {
            intents.collect { intent ->
                when (intent) {
                    is TransactionDetailsIntent.LoadTransaction -> loadTransaction(intent.transactionId)
                    TransactionDetailsIntent.OnRefundClick -> handleOperationClick(PaymentOperation.REFUND)
                    TransactionDetailsIntent.OnProcessRefund -> processRefund()
                    TransactionDetailsIntent.OnIncrementalAuthClick -> handleOperationClick(PaymentOperation.INCREMENTAL_AUTH)
                    TransactionDetailsIntent.OnCaptureClick -> handleOperationClick(PaymentOperation.CAPTURE)
                    TransactionDetailsIntent.OnReverseClick -> handleOperationClick(PaymentOperation.REVERSE)
                    TransactionDetailsIntent.OnAdjustTipClick -> handleOperationClick(PaymentOperation.ADJUST_TIP)
                    TransactionDetailsIntent.OnCloseOperationModal -> handleCloseOperationModal()
                    is TransactionDetailsIntent.OnProcessOperation -> processOperation(
                        intent.operation,
                        intent.amount,
                        intent.tipType,
                        intent.tipPercentage
                    )
                    TransactionDetailsIntent.OnSendEmailClick -> handleSendEmailClick()
                    TransactionDetailsIntent.OnSendSmsClick -> handleSendSmsClick()
                    TransactionDetailsIntent.OnCancelReceiptInput -> handleCancelReceiptInput()
                    TransactionDetailsIntent.OnSendReceipt -> handleSendReceipt()
                    is TransactionDetailsIntent.OnEmailInputChanged ->
                        handleEmailInputChanged(intent.email)

                    is TransactionDetailsIntent.OnSmsInputChanged -> handleSmsInputChanged(intent.phone)
                }
            }
        }
    }

    private suspend fun loadTransaction(transactionId: String) = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        Timber.d("Loading transaction details for ID: $transactionId")

        val result = koardSdk.getTransaction(transactionId)

        if (result.isSuccess) {
            val transaction = result.getOrNull()
            if (transaction != null) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        transaction = transaction.toTransactionDetailsUI(),
                        error = null
                    )
                }
                Timber.d("Transaction details loaded successfully for ID: $transactionId")
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(R.string.transaction_not_found)
                    )
                }
                Timber.e("Transaction not found for ID: $transactionId")
            }
        } else {
            val exception = result.exceptionOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = getApplication<Application>().getString(
                        R.string.failed_to_load_transaction,
                        exception?.message ?: "Unknown error"
                    )
                )
            }
            Timber.e(exception, "Failed to load transaction details")
        }
    }

    private suspend fun handleRefundClick() {
        _effects.send(TransactionDetailsEffect.ShowRefundDialog)
    }

    private suspend fun processRefund() = withContext(Dispatchers.IO) {
        try {
            val transaction = uiState.value.transaction ?: return@withContext
            val transactionId = transaction.id
            val amount = transaction.amount

            Timber.d("Processing refund for transaction ID: $transactionId")

            val result = koardSdk.refundTransaction(transactionId, amount)

            if (result.isSuccess) {
                val updateTransaction = result.getOrNull()
                if (updateTransaction != null) {
                    Timber.d("Refund processed successfully for transaction ID: $transactionId")
                    _effects.send(TransactionDetailsEffect.RefundSuccess)
                    _uiState.update {
                        it.copy(
                            transaction = updateTransaction.toTransactionDetailsUI(),
                            error = null
                        )
                    }
                } else {
                    Timber.e("Refund failed for transaction ID: $transactionId")
                    _effects.send(
                        TransactionDetailsEffect.ShowError(
                            getApplication<Application>().getString(
                                R.string.refund_failed
                            )
                        )
                    )
                }
            } else {
                val exception = result.exceptionOrNull()
                Timber.e(exception, "Failed to process refund for transaction ID: $transactionId")
                _effects.send(
                    TransactionDetailsEffect.ShowError(
                        getApplication<Application>().getString(
                            R.string.failed_to_process_refund,
                            exception?.message ?: "Unknown error"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during refund processing")
            _effects.send(
                TransactionDetailsEffect.ShowError(
                    getApplication<Application>().getString(
                        R.string.failed_to_process_refund,
                        e.message ?: "Unknown error"
                    )
                )
            )
        }
    }

    private fun handleSendEmailClick() {
        _uiState.update { it.copy(receiptInputType = ReceiptInputType.EMAIL) }
    }

    private fun handleSendSmsClick() {
        _uiState.update { it.copy(receiptInputType = ReceiptInputType.SMS) }
    }

    private fun handleCancelReceiptInput() {
        _uiState.update { it.copy(receiptInputType = ReceiptInputType.NONE) }
    }

    private fun handleEmailInputChanged(email: String) {
        _uiState.update { it.copy(email = email, isSendEnabled = email.isValidEmail()) }
    }

    private fun handleSmsInputChanged(phone: String) {
        _uiState.update { it.copy(phone = phone, isSmsSendEnabled = phone.isValidUSPhoneNumber()) }
    }

    private suspend fun handleSendReceipt() = withContext(Dispatchers.IO) {
        val transaction = uiState.value.transaction ?: return@withContext
        val transactionId = transaction.id

        val email = uiState.value.email.takeIf {
            uiState.value.receiptInputType == ReceiptInputType.EMAIL
        }
        val phoneNumber = uiState.value.phone.takeIf {
            uiState.value.receiptInputType == ReceiptInputType.SMS
        }

        Timber.d("Sending receipt for transaction ID: $transactionId, email: $email, phone: $phoneNumber")

        _uiState.update { it.copy(isSendingReceipt = true) }

        val result = koardSdk.sendReceipt(transactionId, email, phoneNumber)

        if (result.isSuccess) {
            Timber.d("Receipt sent successfully for transaction ID: $transactionId")
            _effects.send(TransactionDetailsEffect.ReceiptSentSuccess)
            _uiState.update {
                it.copy(
                    receiptInputType = ReceiptInputType.NONE,
                    email = "",
                    phone = "",
                    isSendEnabled = false,
                    isSmsSendEnabled = false,
                    isSendingReceipt = false
                )
            }
        } else {
            val exception = result.exceptionOrNull()
            Timber.e(exception, "Failed to send receipt for transaction ID: $transactionId")
            _effects.send(
                TransactionDetailsEffect.ShowError(
                    getApplication<Application>().getString(
                        R.string.failed_to_send_receipt,
                        exception?.message ?: "Unknown error"
                    )
                )
            )
            _uiState.update { it.copy(isSendingReceipt = false) }
        }
    }

    private fun handleOperationClick(operation: PaymentOperation) {
        _uiState.update {
            it.copy(operationModalState = OperationModalState(operation = operation))
        }
    }

    private fun handleCloseOperationModal() {
        _uiState.update { it.copy(operationModalState = null) }
    }

    private suspend fun processOperation(
        operation: PaymentOperation,
        amount: Int?,
        tipType: com.koardlabs.merchant.sdk.domain.AmountType?,
        tipPercentage: Double?
    ) = withContext(Dispatchers.IO) {
        try {
            val transaction = uiState.value.transaction ?: return@withContext
            val transactionId = transaction.id

            _uiState.update {
                it.copy(operationModalState = it.operationModalState?.copy(isProcessing = true))
            }

            Timber.d("Processing ${operation.displayName} for transaction ID: $transactionId")

            val result = when (operation) {
                PaymentOperation.INCREMENTAL_AUTH -> {
                    if (amount == null) {
                        Timber.e("Amount is required for incremental auth")
                        throw IllegalArgumentException("Amount is required")
                    }
                    koardSdk.incrementalAuth(transactionId, amount)
                }
                PaymentOperation.CAPTURE -> {
                    koardSdk.capture(transactionId, amount)
                }
                PaymentOperation.REVERSE -> {
                    koardSdk.reverse(transactionId, amount)
                }
                PaymentOperation.ADJUST_TIP -> {
                    if (tipType == null) {
                        throw IllegalArgumentException("Tip type is required")
                    }
                    koardSdk.adjust(transactionId, tipType, amount, tipPercentage)
                }
                PaymentOperation.REFUND -> {
                    koardSdk.refund(transactionId, amount)
                }
            }

            if (result.isSuccess) {
                val updatedTransaction = result.getOrNull()
                if (updatedTransaction != null) {
                    Timber.d("${operation.displayName} processed successfully for transaction ID: $transactionId")
                    _uiState.update {
                        it.copy(
                            transaction = updatedTransaction.toTransactionDetailsUI(),
                            operationModalState = it.operationModalState?.copy(
                                isProcessing = false,
                                result = OperationResult(
                                    success = true,
                                    message = "${operation.displayName} completed successfully",
                                    transaction = updatedTransaction.toTransactionDetailsUI()
                                )
                            )
                        )
                    }
                } else {
                    Timber.e("${operation.displayName} failed for transaction ID: $transactionId")
                    _uiState.update {
                        it.copy(
                            operationModalState = it.operationModalState?.copy(
                                isProcessing = false,
                                result = OperationResult(
                                    success = false,
                                    message = "${operation.displayName} failed",
                                    transaction = null
                                )
                            )
                        )
                    }
                }
            } else {
                val exception = result.exceptionOrNull()
                Timber.e(exception, "Failed to process ${operation.displayName} for transaction ID: $transactionId")
                _uiState.update {
                    it.copy(
                        operationModalState = it.operationModalState?.copy(
                            isProcessing = false,
                            result = OperationResult(
                                success = false,
                                message = "Failed: ${exception?.message ?: "Unknown error"}",
                                transaction = null
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during ${operation.displayName} processing")
            _uiState.update {
                it.copy(
                    operationModalState = it.operationModalState?.copy(
                        isProcessing = false,
                        result = OperationResult(
                            success = false,
                            message = "Error: ${e.message ?: "Unknown error"}",
                            transaction = null
                        )
                    )
                )
            }
        }
    }

    private fun KoardTransaction.toTransactionDetailsUI(): TransactionDetailsUI = TransactionDetailsUI(
        id = transactionId,
        amount = totalAmount,
        status = status,
        merchant = merchantName,
        card = card,
        cardBrand = cardBrand,
        cardType = cardType,
        date = createdAt,
        currency = currency,
        subtotal = subtotal,
        taxAmount = taxAmount,
        taxRate = taxRate,
        tipAmount = tipAmount,
        surchargeAmount = surchargeAmount,
        gateway = gateway,
        processor = processor,
        processorResponseCode = processorResponseCode,
        processorResponseMessage = processorResponseMessage,
        statusReason = statusReason,
        paymentMethod = paymentMethod,
        deviceId = deviceId,
        locationId = locationId,
        refunded = refunded,
        reversed = reversed,
        approvalCode = gatewayTransactionResponse.approvalCode,
        gatewayResponseMessage = gatewayTransactionResponse.responseMessage,
        transactionType = transactionType,
        simplifiedStatus = simplifiedStatus
    )

    fun onDispatch(intent: TransactionDetailsIntent) {
        viewModelScope.launch { intents.emit(intent) }
    }
}

data class TransactionDetailsUiState(
    val isLoading: Boolean = false,
    val transaction: TransactionDetailsUI? = null,
    val error: String? = null,
    val receiptInputType: ReceiptInputType = ReceiptInputType.NONE,
    val email: String = "",
    val isSendEnabled: Boolean = false,
    val phone: String = "",
    val isSmsSendEnabled: Boolean = false,
    val isSendingReceipt: Boolean = false,
    val operationModalState: OperationModalState? = null
)

data class OperationModalState(
    val operation: PaymentOperation,
    val isProcessing: Boolean = false,
    val result: OperationResult? = null
)

data class OperationResult(
    val success: Boolean,
    val message: String,
    val transaction: TransactionDetailsUI?
)

enum class PaymentOperation(val displayName: String) {
    INCREMENTAL_AUTH("Incremental Authorization"),
    CAPTURE("Capture"),
    REVERSE("Reverse"),
    ADJUST_TIP("Adjust Tip"),
    REFUND("Refund")
}

sealed class TransactionDetailsIntent {
    data class LoadTransaction(val transactionId: String) : TransactionDetailsIntent()
    data object OnRefundClick : TransactionDetailsIntent()
    data object OnProcessRefund : TransactionDetailsIntent()
    data object OnIncrementalAuthClick : TransactionDetailsIntent()
    data object OnCaptureClick : TransactionDetailsIntent()
    data object OnReverseClick : TransactionDetailsIntent()
    data object OnAdjustTipClick : TransactionDetailsIntent()
    data object OnCloseOperationModal : TransactionDetailsIntent()
    data class OnProcessOperation(
        val operation: PaymentOperation,
        val amount: Int?,
        val tipType: com.koardlabs.merchant.sdk.domain.AmountType?,
        val tipPercentage: Double?
    ) : TransactionDetailsIntent()
    data object OnSendEmailClick : TransactionDetailsIntent()
    data object OnSendSmsClick : TransactionDetailsIntent()
    data object OnCancelReceiptInput : TransactionDetailsIntent()
    data object OnSendReceipt : TransactionDetailsIntent()
    data class OnEmailInputChanged(val email: String) : TransactionDetailsIntent()
    data class OnSmsInputChanged(val phone: String) : TransactionDetailsIntent()
}

data class TransactionDetailsUI(
    val id: String,
    val amount: Int,
    val status: KoardTransactionStatus,
    val merchant: String,
    val card: String,
    val cardBrand: String,
    val cardType: String,
    val date: Long,
    val currency: String,
    val subtotal: Int,
    val taxAmount: Int,
    val taxRate: Int,
    val tipAmount: Int,
    val surchargeAmount: Double,
    val gateway: String,
    val processor: String,
    val processorResponseCode: String,
    val processorResponseMessage: String,
    val statusReason: String,
    val paymentMethod: String,
    val deviceId: String,
    val locationId: String,
    val refunded: Int,
    val reversed: Int,
    val approvalCode: String,
    val gatewayResponseMessage: String,
    val transactionType: String,
    val simplifiedStatus: String
) {
    fun getTransactionTypeDisplayName(context: Context): String = when (transactionType) {
        "sale" -> context.getString(R.string.transaction_type_sale)
        "manually_keyed_sale" -> context.getString(R.string.transaction_type_keyed_sale)
        "auth" -> context.getString(R.string.transaction_type_auth)
        "capture" -> context.getString(R.string.transaction_type_capture)
        "refund" -> context.getString(R.string.transaction_type_refund)
        "reverse" -> context.getString(R.string.transaction_type_reverse)
        "tip_adjust" -> context.getString(R.string.transaction_type_tip_adjust)
        "incremental_auth" -> context.getString(R.string.transaction_type_incremental_auth)
        "verification" -> context.getString(R.string.transaction_type_verification)
        else -> context.getString(R.string.transaction_type_unknown)
    }

    val totalFormatted: String get() = formatFromCents(amount)
    val subtotalFormatted: String get() = formatFromCents(subtotal)
    val taxFormatted: String get() = formatFromCents(taxAmount)
    val tipFormatted: String get() = formatFromCents(tipAmount)
    val surchargeFormatted: String get() = formatFromCents(surchargeAmount.toInt())

    val dateFormatted: String
        get() {
            val date = Date(date)
            val format = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            return format.format(date)
        }

    val isRefundable: Boolean
        get() = status.isRefundable &&
            status != KoardTransactionStatus.REFUNDED &&
            transactionType != "refund"
}

private fun formatFromCents(valueInCents: Int): String = "$${String.format("%.2f", valueInCents / 100.0)}"

enum class ReceiptInputType(
    val labelResId: Int,
    val placeholderResId: Int,
    val keyboardType: KeyboardType,
    val iconResId: Int?,
    val iconVector: ImageVector?
) {
    EMAIL(
        labelResId = R.string.email_address,
        placeholderResId = R.string.enter_email_address,
        keyboardType = KeyboardType.Email,
        iconResId = null,
        iconVector = Icons.Outlined.Email
    ),
    SMS(
        labelResId = R.string.phone_number,
        placeholderResId = R.string.enter_phone_number,
        keyboardType = KeyboardType.Phone,
        iconResId = R.drawable.outline_sms_24,
        iconVector = null
    ),
    NONE(0, 0, KeyboardType.Text, null, null);

    fun getLabel(context: Context): String = if (labelResId != 0) context.getString(labelResId) else ""

    fun getPlaceholder(context: Context): String = if (placeholderResId != 0) context.getString(placeholderResId) else ""
}

sealed interface TransactionDetailsEffect {
    data class ShowError(val message: String) : TransactionDetailsEffect
    data object ShowRefundDialog : TransactionDetailsEffect
    data object RefundSuccess : TransactionDetailsEffect
    data object ReceiptSentSuccess : TransactionDetailsEffect
}
