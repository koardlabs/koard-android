package com.payroc.terminal.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.payroc.terminal.R

import com.koardlabs.merchant.sdk.KoardMerchantSdk
import com.koardlabs.merchant.sdk.domain.AmountType
import com.koardlabs.merchant.sdk.domain.KoardLocation
import com.koardlabs.merchant.sdk.domain.KoardTransactionActionStatus
import com.koardlabs.merchant.sdk.domain.KoardTransactionFinalStatus
import com.koardlabs.merchant.sdk.domain.KoardTransactionResponse
import com.koardlabs.merchant.sdk.domain.PaymentBreakdown
import com.koardlabs.merchant.sdk.domain.Surcharge
import com.visa.kic.sdk.common.ipc.ButtonProperties
import com.koardlabs.merchant.sdk.domain.exception.KoardException
import com.koardlabs.merchant.sdk.domain.KoardErrorType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val koardSdk = KoardMerchantSdk.getInstance()

    private val _uiState = MutableStateFlow(
        MainScreenUiState(
            isDeviceEnrolled = koardSdk.isDeviceEnrolled
        )
    )
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val intents = MutableSharedFlow<MainScreenIntent>()
    private val _effects = Channel<MainScreenEffect>()
    val effects = _effects.receiveAsFlow()

    // Expose SDK readiness state
    val sdkReadiness = koardSdk.readinessState

    init {
        viewModelScope.launch(
            context = CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "Uncaught exception in intent handler")
                // Clear loading states on any uncaught exception
                _uiState.update {
                    it.copy(
                        isLoadingLocations = false,
                        isEnrollingDevice = false,
                        isProcessing = false
                    )
                }
            }
        ) {
            intents.collect { intent ->
                try {
                    when (intent) {
                        MainScreenIntent.OnSelectLocation -> onSelectLocation()
                        is MainScreenIntent.OnLocationSelected -> onLocationSelected(intent.location)
                        MainScreenIntent.EnrollDevice -> enrollDevice()
                        is MainScreenIntent.OnAmountChanged -> onAmountChanged(intent.amount)
                        is MainScreenIntent.OnTaxAmountChanged -> onTaxAmountChanged(intent.amount)
                        MainScreenIntent.OnTaxTypeToggled -> onTaxTypeToggled()
                        is MainScreenIntent.OnTipAmountChanged -> onTipAmountChanged(intent.amount)
                        MainScreenIntent.OnTipTypeToggled -> onTipTypeToggled()
                        is MainScreenIntent.OnSurchargeStateChanged -> onSurchargeStateChanged(intent.state)
                        is MainScreenIntent.OnSurchargeAmountChanged -> onSurchargeAmountChanged(intent.amount)
                        MainScreenIntent.OnSurchargeTypeToggled -> onSurchargeTypeToggled()
                        is MainScreenIntent.StartPreauth -> startPreauth(intent.activity)
                        is MainScreenIntent.StartTransaction -> startTransaction(intent.activity)
                        MainScreenIntent.DismissTransactionResult -> dismissTransactionResult()
                        is MainScreenIntent.OnSurchargeOverrideAmountChanged -> onSurchargeOverrideAmountChanged(intent.amount)
                        MainScreenIntent.OnSurchargeOverrideTypeToggled -> onSurchargeOverrideTypeToggled()
                        is MainScreenIntent.ConfirmSurcharge -> onConfirmSurcharge(intent.confirm)
                        is MainScreenIntent.UpdateCancelButtonMetrics -> updateCancelButtonMetrics(
                            intent.xDp,
                            intent.yDp,
                            intent.widthDp,
                            intent.heightDp
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error handling intent: $intent")
                    // Update UI with error if it's a location-related intent
                    if (intent is MainScreenIntent.OnLocationSelected) {
                        _uiState.update {
                            it.copy(
                                isLoadingLocations = false,
                                locationError = e.message ?: "Failed to handle location selection"
                            )
                        }
                    }
                }
            }
        }

        // Observe SDK readiness state to refresh location when it changes
        viewModelScope.launch(Dispatchers.IO) {
            koardSdk.readinessState.collect { readiness ->
                if (readiness.hasActiveLocation) {
                    // Location is active - load it
                    koardSdk.activeLocationId?.let { locationId ->
                        try {
                            val result = koardSdk.getLocation(locationId)
                            result.onSuccess { location ->
                                _uiState.update { it.copy(selectedLocation = location) }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load active location")
                        }
                    }
                } else {
                    // No active location - clear it
                    _uiState.update { it.copy(selectedLocation = null) }
                }
            }
        }

        // Auto-fetch locations after init
        viewModelScope.launch(Dispatchers.IO) {
            fetchLocations()
        }
    }

    private suspend fun fetchLocations() = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isLoadingLocations = true, locationError = null) }
        try {
            val result = koardSdk.getLocations()
            if (result.isSuccess) {
                val locations = result.getOrNull().orEmpty()
                _uiState.update { it.copy(locations = locations, isLoadingLocations = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingLocations = false,
                        locationError = "Failed to load locations"
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching locations")
            _uiState.update {
                it.copy(
                    isLoadingLocations = false,
                    locationError = e.message ?: "Failed to load locations"
                )
            }
        }
    }

    private suspend fun onSelectLocation() = withContext(Dispatchers.IO) {
        try {
            val result = koardSdk.getLocations()
            if (result.isSuccess) {
                val locations = result.getOrNull().orEmpty()
                if (locations.isNotEmpty()) {
                    _effects.send(MainScreenEffect.ShowLocationSheet(locations))
                } else {
                    Timber.w("No locations available")
                }
            } else {
                Timber.e("Failed to fetch locations: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching locations")
        }
    }

    private suspend fun onLocationSelected(location: KoardLocation) = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isLoadingLocations = true, locationError = null) }
        try {
            koardSdk.setActiveLocation(location.id)
            _uiState.update { it.copy(selectedLocation = location, isLoadingLocations = false, locationError = null) }
        } catch (e: KoardException) {
            Timber.e(e, "Failed to set active location")
            val errorMessage = e.error.shortMessage
            _uiState.update {
                it.copy(
                    isLoadingLocations = false,
                    locationError = errorMessage
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set active location")
            _uiState.update {
                it.copy(
                    isLoadingLocations = false,
                    locationError = e.message ?: "Failed to set location"
                )
            }
        }
    }

    private suspend fun enrollDevice() = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isEnrollingDevice = true, enrollmentError = null) }

        // Check if kernel app is installed (dynamically - not cached)
        val isKernelInstalled = try {
            koardSdk.isKernelAppInstalled()
        } catch (e: Exception) {
            Timber.w(e, "Failed to check kernel app status")
            false
        }

        if (!isKernelInstalled) {
            _uiState.update {
                it.copy(
                    isEnrollingDevice = false,
                    enrollmentError = "Visa Kernel app is not installed."
                )
            }
            return@withContext
        }

        try {
            Timber.d("Enrolling device...")
            val result = koardSdk.enrollDevice()
            Timber.d("Enrollment result: $result")

            // SDK returns error messages as strings for some cases (including mapped KIC SDK exceptions)
            // Check if the result indicates a failure
            if (result.contains("failed", ignoreCase = true) || result.contains("error", ignoreCase = true)) {
                Timber.w("Enrollment returned error message: $result")
                _uiState.update { it.copy(enrollmentError = result) }
            } else {
                // Success - error would be null, enrolled status updated in finally block
                Timber.d("Enrollment succeeded")
            }
        } catch (e: KoardException) {
            Timber.e(e, "Failed to enroll device - KoardException: ${e.error.errorType}")
            Timber.e("Cause: ${e.cause}")

            // Build comprehensive error message
            val errorMessage = buildString {
                append(e.error.shortMessage)

                // Add error type information if it's not generic
                when (val errorType = e.error.errorType) {
                    is KoardErrorType.VACEnrollmentError -> append("\nEnrollment error")
                    is KoardErrorType.NfcTransactionError -> append("\nNFC error")
                    is KoardErrorType.CertificateError -> append("\nCertificate error")
                    is KoardErrorType.BLEError -> append("\nBluetooth error")
                    is KoardErrorType.DeviceIntegrityError -> append("\nDevice integrity issue")
                    is KoardErrorType.KoardServiceErrorType -> {
                        when (errorType) {
                            is KoardErrorType.KoardServiceErrorType.HttpError ->
                                append("\nHTTP error: ${errorType.errorCode}")
                            else -> append("\nService error")
                        }
                    }
                    else -> {} // Don't add for GeneralError or other types
                }

                // Add cause message if available and different from main message
                e.cause?.message?.let { causeMsg ->
                    if (causeMsg.isNotBlank() && causeMsg != e.error.shortMessage) {
                        append("\nDetails: $causeMsg")
                    }
                }
            }

            _uiState.update { it.copy(enrollmentError = errorMessage) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to enroll device - Unexpected exception")
            _uiState.update { it.copy(enrollmentError = e.message ?: "Failed to enroll device") }
        } finally {
            _uiState.update {
                it.copy(
                    isEnrollingDevice = false,
                    isDeviceEnrolled = koardSdk.isDeviceEnrolled
                )
            }
        }
    }

    // Transaction form handlers
    private fun onAmountChanged(newAmount: String) {
        updateTransactionFormState { it.copy(amount = newAmount, errorMessage = null) }
    }

    private fun onTaxAmountChanged(newAmount: String) {
        updateTransactionFormState {
            if (it.taxType == AmountType.PERCENTAGE) {
                it.copy(taxAmountPercentage = newAmount, errorMessage = null)
            } else {
                it.copy(taxAmountFixed = newAmount, errorMessage = null)
            }
        }
    }

    private fun onTaxTypeToggled() {
        updateTransactionFormState {
            it.copy(
                taxType = if (it.taxType == AmountType.PERCENTAGE) AmountType.FIXED else AmountType.PERCENTAGE,
                errorMessage = null
            )
        }
    }

    private fun onTipAmountChanged(newAmount: String) {
        updateTransactionFormState {
            if (it.tipType == AmountType.PERCENTAGE) {
                it.copy(tipAmountPercentage = newAmount, errorMessage = null)
            } else {
                it.copy(tipAmountFixed = newAmount, errorMessage = null)
            }
        }
    }

    private fun onTipTypeToggled() {
        updateTransactionFormState {
            it.copy(
                tipType = if (it.tipType == AmountType.PERCENTAGE) AmountType.FIXED else AmountType.PERCENTAGE,
                errorMessage = null
            )
        }
    }

    private fun onSurchargeStateChanged(newState: SurchargeState) {
        updateTransactionFormState { it.copy(surchargeState = newState, errorMessage = null) }
    }

    private fun onSurchargeAmountChanged(newAmount: String) {
        updateTransactionFormState {
            if (it.surchargeType == AmountType.PERCENTAGE) {
                it.copy(surchargeAmountPercentage = newAmount, errorMessage = null)
            } else {
                it.copy(surchargeAmountFixed = newAmount, errorMessage = null)
            }
        }
    }

    private fun onSurchargeTypeToggled() {
        updateTransactionFormState {
            it.copy(
                surchargeType = if (it.surchargeType == AmountType.PERCENTAGE) AmountType.FIXED else AmountType.PERCENTAGE,
                errorMessage = null
            )
        }
    }

    private suspend fun startPreauth(activity: Activity) = withContext(Dispatchers.IO) {
        // Check SDK readiness
        val readiness = koardSdk.readinessState.value
        if (!readiness.isReadyForTransactions) {
            _uiState.update {
                it.copy(errorMessage = "Cannot start transaction: ${readiness.getStatusMessage()}")
            }
            return@withContext
        }

        val amount = uiState.value.amount.trim()
        if (amount.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.transaction_error_missing_amount))
            }
            return@withContext
        }

        if (uiState.value.isProcessing) return@withContext

        _uiState.update {
            it.copy(
                isProcessing = true,
                errorMessage = null,
                statusMessages = emptyList(),
                finalStatus = null,
                transactionId = null,
                transaction = null,
                visaStatusCode = null,
                visaDisplayMessage = null,
                visaReaderStatus = null,
                visaActionStatus = null,
                visaFinalStatus = null
            )
        }

        try {
            val breakdown = buildPaymentBreakdown(uiState.value, amount)
            val totalAmountCents = computeTotalAmountCents(breakdown)
            val cancelButton = buildCancelButtonProperties(uiState.value)

            val eventId = UUID.randomUUID().toString()
            Timber.d("Starting preauth with eventId: $eventId, amount: $totalAmountCents")

            koardSdk.preauth(
                activity = activity,
                amount = totalAmountCents,
                breakdown = breakdown,
                buttonProperties = cancelButton,
                currency = "USD",
                eventId = eventId
            ).onEach { response ->
                handleTransactionResponse(response)
            }.launchIn(viewModelScope)
        } catch (t: Throwable) {
            Timber.e(t, "Preauth transaction failed")
            _uiState.update {
                it.copy(isProcessing = false, errorMessage = formatError(t))
            }
        }
    }

    private suspend fun startTransaction(activity: Activity) = withContext(Dispatchers.IO) {
        // Check SDK readiness
        val readiness = koardSdk.readinessState.value
        if (!readiness.isReadyForTransactions) {
            _uiState.update {
                it.copy(errorMessage = "Cannot start transaction: ${readiness.getStatusMessage()}")
            }
            return@withContext
        }

        val amount = uiState.value.amount.trim()
        if (amount.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.transaction_error_missing_amount))
            }
            return@withContext
        }

        if (uiState.value.isProcessing) return@withContext

        _uiState.update {
            it.copy(
                isProcessing = true,
                errorMessage = null,
                statusMessages = emptyList(),
                finalStatus = null,
                transactionId = null,
                transaction = null,
                visaStatusCode = null,
                visaDisplayMessage = null,
                visaReaderStatus = null,
                visaActionStatus = null,
                visaFinalStatus = null
            )
        }

        try {
            val breakdown = buildPaymentBreakdown(uiState.value, amount)
            val totalAmountCents = computeTotalAmountCents(breakdown)
            val cancelButton = buildCancelButtonProperties(uiState.value)

            val eventId = UUID.randomUUID().toString()
            Timber.d("Starting sale with eventId: $eventId, amount: $totalAmountCents")

            koardSdk.sale(
                activity = activity,
                amount = totalAmountCents,
                breakdown = breakdown,
                buttonProperties = cancelButton,
                currency = "USD",
                eventId = eventId
            ).onEach { response ->
                handleTransactionResponse(response)
            }.launchIn(viewModelScope)
        } catch (t: Throwable) {
            Timber.e(t, "Transaction failed")
            _uiState.update {
                it.copy(isProcessing = false, errorMessage = formatError(t))
            }
        }
    }

    private fun buildPaymentBreakdown(state: MainScreenUiState, amountStr: String): PaymentBreakdown {
        val subtotalDollars = amountStr.toDoubleOrNull() ?: 0.0
        val subtotalCents = (subtotalDollars * 100).toInt()

        // Calculate tip
        val tipValue = when (state.tipType) {
            AmountType.PERCENTAGE -> {
                val rate = state.tipAmount.toDoubleOrNull() ?: 0.0
                subtotalDollars * (rate / 100.0)
            }
            AmountType.FIXED -> state.tipAmount.toDoubleOrNull() ?: 0.0
        }
        val tipCents = if (state.tipType == AmountType.PERCENTAGE) {
            kotlin.math.round(tipValue * 100).toInt()
        } else {
            (tipValue * 100).toInt()
        }

        // Calculate tax
        val taxValue = when (state.taxType) {
            AmountType.PERCENTAGE -> {
                val rate = state.taxAmount.toDoubleOrNull() ?: 0.0
                (subtotalDollars + tipValue) * (rate / 100.0)
            }
            AmountType.FIXED -> state.taxAmount.toDoubleOrNull() ?: 0.0
        }
        val taxCents = if (state.taxType == AmountType.PERCENTAGE) {
            kotlin.math.round(taxValue * 100).toInt()
        } else {
            (taxValue * 100).toInt()
        }

        // Calculate surcharge
        val surchargeValue = if (state.surchargeState == SurchargeState.ENABLE ||
                                state.surchargeState == SurchargeState.BYPASS) {
            val baseAmount = subtotalDollars + tipValue + taxValue
            when (state.surchargeType) {
                AmountType.PERCENTAGE -> {
                    val rate = state.surchargeAmount.toDoubleOrNull() ?: 0.0
                    baseAmount * (rate / 100.0)
                }
                AmountType.FIXED -> state.surchargeAmount.toDoubleOrNull() ?: 0.0
            }
        } else {
            0.0
        }
        val surchargeCents = if (state.surchargeType == AmountType.PERCENTAGE) {
            kotlin.math.round(surchargeValue * 100).toInt()
        } else {
            (surchargeValue * 100).toInt()
        }

        val isBypass = state.surchargeState == SurchargeState.BYPASS
        return PaymentBreakdown(
            subtotal = subtotalCents,
            taxRate = if (state.taxType == AmountType.PERCENTAGE)
                state.taxAmount.toDoubleOrNull() else null,
            taxAmount = taxCents,
            tipAmount = tipCents,
            tipRate = if (state.tipType == AmountType.PERCENTAGE)
                state.tipAmount.toDoubleOrNull() else null,
            tipType = if (state.tipType == AmountType.PERCENTAGE) "percentage" else "fixed",
            surcharge = Surcharge(
                amount = if (!isBypass && state.surchargeType == AmountType.FIXED &&
                    (state.surchargeState == SurchargeState.ENABLE || state.surchargeState == SurchargeState.BYPASS))
                    surchargeCents else null,
                percentage = if (!isBypass && state.surchargeType == AmountType.PERCENTAGE &&
                    (state.surchargeState == SurchargeState.ENABLE || state.surchargeState == SurchargeState.BYPASS))
                    state.surchargeAmount.toDoubleOrNull() else null,
                bypass = isBypass
            )
        )
    }

    private fun computeTotalAmountCents(breakdown: PaymentBreakdown): Int {
        val subtotal = breakdown.subtotal
        val tip = breakdown.tipAmount ?: 0
        val tax = breakdown.taxAmount ?: 0
        val baseCents = subtotal + tip + tax

        val surcharge = breakdown.surcharge
        val surchargeCents = when {
            surcharge == null -> 0
            surcharge.bypass == true -> 0
            surcharge.amount != null -> surcharge.amount ?: 0
            surcharge.percentage != null -> {
                val percent = surcharge.percentage ?: 0.0
                kotlin.math.round(baseCents * (percent / 100.0)).toInt()
            }
            else -> 0
        }

        return baseCents + surchargeCents
    }

    private fun buildCancelButtonProperties(state: MainScreenUiState): List<ButtonProperties>? {
        if (state.cancelButtonWidthDp <= 0 || state.cancelButtonHeightDp <= 0) {
            return null
        }
        return listOf(
            ButtonProperties(
                "Cancel",
                state.cancelButtonXCoordinateDp,
                state.cancelButtonYCoordinateDp,
                state.cancelButtonWidthDp,
                state.cancelButtonHeightDp
            )
        )
    }

    private fun updateCancelButtonMetrics(xDp: Int, yDp: Int, widthDp: Int, heightDp: Int) {
        _uiState.update {
            it.copy(
                cancelButtonXCoordinateDp = xDp,
                cancelButtonYCoordinateDp = yDp,
                cancelButtonWidthDp = widthDp,
                cancelButtonHeightDp = heightDp
            )
        }
    }

    private fun updateTransactionFormState(update: (MainScreenUiState) -> MainScreenUiState) {
        _uiState.update { current ->
            val updated = update(current)
            val amountStr = updated.amount.trim()
            val totalCents = if (amountStr.isBlank()) {
                null
            } else {
                val breakdown = buildPaymentBreakdown(updated, amountStr)
                computeTotalAmountCents(breakdown)
            }
            updated.copy(computedTotalCents = totalCents)
        }
    }


    private fun handleTransactionResponse(response: KoardTransactionResponse) {
        val statusMessage = buildString {
            append(formatReaderStatus(response.readerStatus))
            response.statusCodeDescription?.let { append("\n$it") }
        }

        Timber.d("Transaction response: readerStatus=${response.readerStatus}, action=${response.actionStatus}")

        // Update reader status
        _uiState.update { it.copy(currentReaderStatus = response.readerStatus) }

        when (response.actionStatus) {
            KoardTransactionActionStatus.OnProgress -> {
                _uiState.update {
                    it.copy(
                        statusMessages = listOf(statusMessage),
                        errorMessage = null
                    )
                }
            }

            KoardTransactionActionStatus.OnFailure -> {
                if (response.statusCode == 12) {
                    _uiState.update { it.copy(statusMessages = listOf(statusMessage)) }
                    return
                }

                val failureMessage = buildString {
                    append("Transaction Failed")
                    response.displayMessage?.let { append("\n\n$it") }
                    response.statusCodeDescription?.let { append("\n\n$it") }
                    response.statusCode?.let { append("\n\nStatus Code: $it") }
                    response.finalStatus?.let { append("\nFinal Status: ${resolveFinalStatusLabel(it)}") }
                }

                _uiState.update {
                    it.copy(
                        statusMessages = listOf(failureMessage),
                        errorMessage = failureMessage,
                        isProcessing = false,
                        visaStatusCode = response.statusCode,
                        visaStatusDescription = response.statusCodeDescription,
                        visaDisplayMessage = response.displayMessage,
                        visaReaderStatus = response.readerStatus.toString(),
                        visaActionStatus = response.actionStatus.toString(),
                        visaFinalStatus = response.finalStatus?.toString()
                    )
                }
            }

            KoardTransactionActionStatus.OnComplete -> {
                val txn = response.transaction
                val resolvedFinalStatus = resolveFinalStatusLabel(response.finalStatus)
                val isFailureOutcome = response.finalStatus is KoardTransactionFinalStatus.Abort ||
                    response.finalStatus is KoardTransactionFinalStatus.Decline ||
                    response.finalStatus is KoardTransactionFinalStatus.Failure
                if (txn?.status == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.SURCHARGE_PENDING) {
                    _uiState.update {
                        it.copy(
                            statusMessages = listOf(statusMessage),
                            transactionId = txn.transactionId,
                            transaction = txn,
                            showSurchargeConfirmation = true,
                            isProcessing = false,
                            visaStatusCode = response.statusCode,
                            visaStatusDescription = response.statusCodeDescription,
                            visaDisplayMessage = response.displayMessage,
                            visaReaderStatus = response.readerStatus.toString(),
                            visaActionStatus = response.actionStatus.toString(),
                            visaFinalStatus = response.finalStatus?.toString()
                        )
                    }
                } else {
                    val failureMessage = if (isFailureOutcome) {
                        buildString {
                            append(resolvedFinalStatus ?: "Transaction Failed")
                            response.displayMessage?.let { append("\n\n$it") }
                            response.statusCodeDescription?.let { append("\n\n$it") }
                            response.statusCode?.let { append("\n\nStatus Code: $it") }
                        }
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            statusMessages = listOf(statusMessage),
                            finalStatus = resolvedFinalStatus,
                            transactionId = txn?.transactionId ?: response.transactionId,
                            transaction = txn,
                            isProcessing = false,
                            errorMessage = failureMessage,
                            visaStatusCode = response.statusCode,
                            visaStatusDescription = response.statusCodeDescription,
                            visaDisplayMessage = response.displayMessage,
                            visaReaderStatus = response.readerStatus.toString(),
                            visaActionStatus = response.actionStatus.toString(),
                            visaFinalStatus = response.finalStatus?.toString()
                        )
                    }
                }
            }

            else -> {
                _uiState.update { it.copy(statusMessages = listOf(statusMessage)) }
            }
        }
    }

    private fun formatReaderStatus(readerStatus: com.koardlabs.merchant.sdk.domain.KoardReaderStatus): String {
        return readerStatus.toString()
    }

    private fun formatError(throwable: Throwable): String {
        return buildString {
            append("Error")
            throwable.message?.let { append("\n\n$it") }
            if (throwable is KoardException) {
                append("\n${throwable.error.shortMessage}")
            }
        }
    }

    private fun resolveFinalStatusLabel(finalStatus: KoardTransactionFinalStatus?): String? {
        finalStatus ?: return null
        val app = getApplication<Application>()
        return when (finalStatus) {
            KoardTransactionFinalStatus.Approve -> app.getString(R.string.transaction_final_status_approve)
            KoardTransactionFinalStatus.Abort -> app.getString(R.string.transaction_final_status_abort)
            KoardTransactionFinalStatus.Decline -> app.getString(R.string.transaction_final_status_decline)
            KoardTransactionFinalStatus.Failure -> app.getString(R.string.transaction_final_status_failure)
            KoardTransactionFinalStatus.AltService -> app.getString(R.string.transaction_final_status_alt_service)
            is KoardTransactionFinalStatus.Unknown -> finalStatus.rawStatus
        }
    }

    private fun dismissTransactionResult() {
        _uiState.update {
            it.copy(
                isProcessing = false,
                finalStatus = null,
                statusMessages = emptyList(),
                transactionId = null,
                transaction = null,
                errorMessage = null,
                showSurchargeConfirmation = false,
                visaStatusCode = null,
                visaStatusDescription = null,
                visaDisplayMessage = null,
                visaReaderStatus = null,
                visaActionStatus = null,
                visaFinalStatus = null,
                currentReaderStatus = null
            )
        }
    }

    private fun onSurchargeOverrideAmountChanged(newAmount: String) {
        _uiState.update { it.copy(surchargeOverrideAmount = newAmount) }
    }

    private fun onSurchargeOverrideTypeToggled() {
        _uiState.update {
            it.copy(
                surchargeOverrideType = if (it.surchargeOverrideType == AmountType.PERCENTAGE) {
                    AmountType.FIXED
                } else {
                    AmountType.PERCENTAGE
                }
            )
        }
    }

    private fun onConfirmSurcharge(confirm: Boolean) {
        val transactionId = _uiState.value.transactionId ?: return
        val transaction = _uiState.value.transaction ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isConfirmingSurcharge = true, errorMessage = null) }

            try {
                val overrideAmount = _uiState.value.surchargeOverrideAmount.trim()
                val breakdown: PaymentBreakdown?
                val totalAmount: Int?

                if (overrideAmount.isNotBlank()) {
                    val overrideValue = overrideAmount.toDoubleOrNull()
                    if (overrideValue == null) {
                        _uiState.update {
                            it.copy(
                                isConfirmingSurcharge = false,
                                errorMessage = "Invalid surcharge amount"
                            )
                        }
                        return@launch
                    }

                    val subtotal = transaction.subtotal
                    val tip = transaction.tipAmount
                    val tax = transaction.taxAmount

                    val newSurcharge = if (_uiState.value.surchargeOverrideType == AmountType.PERCENTAGE) {
                        ((subtotal.toDouble() * overrideValue) / 100.0).toInt()
                    } else {
                        (overrideValue * 100).toInt()
                    }

                    totalAmount = subtotal + tip + tax + newSurcharge

                    breakdown = PaymentBreakdown(
                        subtotal = subtotal,
                        tipAmount = tip,
                        tipType = if (_uiState.value.surchargeOverrideType == AmountType.PERCENTAGE) "percentage" else "fixed",
                        taxAmount = tax,
                        surcharge = Surcharge(
                            amount = if (_uiState.value.surchargeOverrideType == AmountType.FIXED) newSurcharge else null,
                            percentage = if (_uiState.value.surchargeOverrideType == AmountType.PERCENTAGE) overrideValue else null
                        )
                    )
                } else {
                    breakdown = null
                    totalAmount = null
                }

                val result = koardSdk.confirm(
                    transactionId = transactionId,
                    confirm = confirm,
                    breakdown = breakdown,
                    amount = totalAmount
                )

                if (result.isSuccess) {
                    val updatedTransaction = result.getOrNull()
                    _uiState.update {
                        it.copy(
                            transaction = updatedTransaction,
                            showSurchargeConfirmation = false,
                            isConfirmingSurcharge = false,
                            finalStatus = if (confirm) "Surcharge Accepted" else "Surcharge Declined",
                            surchargeOverrideAmount = ""
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to confirm surcharge"
                    _uiState.update {
                        it.copy(
                            isConfirmingSurcharge = false,
                            errorMessage = error
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error confirming surcharge")
                _uiState.update {
                    it.copy(
                        isConfirmingSurcharge = false,
                        errorMessage = e.message ?: "Error confirming surcharge"
                    )
                }
            }
        }
    }

    fun onDispatch(intent: MainScreenIntent) {
        viewModelScope.launch { intents.emit(intent) }
    }
}

data class MainScreenUiState(
    // Location state
    val selectedLocation: KoardLocation? = null,
    val locations: List<KoardLocation> = emptyList(),
    val isLoadingLocations: Boolean = false,
    val locationError: String? = null,

    // Enrollment state
    val isEnrollingDevice: Boolean = false,
    val isDeviceEnrolled: Boolean = false,
    val enrollmentError: String? = null,

    // Transaction form state
    val amount: String = "",
    val taxAmountPercentage: String = "",
    val taxAmountFixed: String = "",
    val taxType: AmountType = AmountType.PERCENTAGE,
    val tipAmountPercentage: String = "",
    val tipAmountFixed: String = "",
    val tipType: AmountType = AmountType.PERCENTAGE,
    val surchargeState: SurchargeState = SurchargeState.OFF,
    val surchargeAmountPercentage: String = "",
    val surchargeAmountFixed: String = "",
    val surchargeType: AmountType = AmountType.PERCENTAGE,

    // Transaction processing state
    val isProcessing: Boolean = false,
    val statusMessages: List<String> = emptyList(),
    val finalStatus: String? = null,
    val transactionId: String? = null,
    val transaction: com.koardlabs.merchant.sdk.domain.KoardTransaction? = null,
    val errorMessage: String? = null,

    // Surcharge confirmation
    val showSurchargeConfirmation: Boolean = false,
    val isConfirmingSurcharge: Boolean = false,
    val surchargeOverrideAmount: String = "",
    val surchargeOverrideType: AmountType = AmountType.PERCENTAGE,

    // Visa response fields
    val visaStatusCode: Int? = null,
    val visaDisplayMessage: String? = null,
    val visaStatusDescription: String? = null,
    val visaReaderStatus: String? = null,
    val visaActionStatus: String? = null,
    val visaFinalStatus: String? = null,

    // Reader status
    val currentReaderStatus: com.koardlabs.merchant.sdk.domain.KoardReaderStatus? = null,
    val computedTotalCents: Int? = null,
    val cancelButtonXCoordinateDp: Int = 0,
    val cancelButtonYCoordinateDp: Int = 0,
    val cancelButtonWidthDp: Int = 0,
    val cancelButtonHeightDp: Int = 0
) {
    val taxAmount: String
        get() = if (taxType == AmountType.PERCENTAGE) taxAmountPercentage else taxAmountFixed

    val tipAmount: String
        get() = if (tipType == AmountType.PERCENTAGE) tipAmountPercentage else tipAmountFixed

    val surchargeAmount: String
        get() = if (surchargeType == AmountType.PERCENTAGE) surchargeAmountPercentage else surchargeAmountFixed
}

sealed class MainScreenIntent {
    data object OnSelectLocation : MainScreenIntent()
    data class OnLocationSelected(val location: KoardLocation) : MainScreenIntent()
    data object EnrollDevice : MainScreenIntent()

    // Transaction form intents
    data class OnAmountChanged(val amount: String) : MainScreenIntent()
    data class OnTaxAmountChanged(val amount: String) : MainScreenIntent()
    data object OnTaxTypeToggled : MainScreenIntent()
    data class OnTipAmountChanged(val amount: String) : MainScreenIntent()
    data object OnTipTypeToggled : MainScreenIntent()
    data class OnSurchargeStateChanged(val state: SurchargeState) : MainScreenIntent()
    data class OnSurchargeAmountChanged(val amount: String) : MainScreenIntent()
    data object OnSurchargeTypeToggled : MainScreenIntent()
    data class StartPreauth(val activity: Activity) : MainScreenIntent()
    data class StartTransaction(val activity: Activity) : MainScreenIntent()
    data object DismissTransactionResult : MainScreenIntent()
    data class OnSurchargeOverrideAmountChanged(val amount: String) : MainScreenIntent()
    data object OnSurchargeOverrideTypeToggled : MainScreenIntent()
    data class ConfirmSurcharge(val confirm: Boolean) : MainScreenIntent()
    data class UpdateCancelButtonMetrics(
        val xDp: Int,
        val yDp: Int,
        val widthDp: Int,
        val heightDp: Int
    ) : MainScreenIntent()
}

sealed interface MainScreenEffect {
    data class ShowLocationSheet(val locations: List<KoardLocation>) : MainScreenEffect
}

enum class SurchargeState {
    OFF,
    BYPASS,
    ENABLE
}
