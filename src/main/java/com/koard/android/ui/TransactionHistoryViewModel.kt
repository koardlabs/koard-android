package com.koard.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.koard.android.R
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

class TransactionHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TransactionHistoryUiState())
    val uiState: StateFlow<TransactionHistoryUiState> = _uiState.asStateFlow()

    private val intents = MutableSharedFlow<TransactionHistoryIntent>()

    private val _effects = Channel<TransactionHistoryEffect>()
    val effects = _effects.receiveAsFlow()

    private val koardSdk = KoardMerchantSdk.getInstance()

    init {
        viewModelScope.launch {
            intents.collect { intent ->
                when (intent) {
                    is TransactionHistoryIntent.LoadTransactions -> loadTransactions()
                }
            }
        }
    }

    private suspend fun loadTransactions() = withContext(Dispatchers.IO) {
        try {
            _uiState.update { it.copy(isLoading = true, error = null) }

            Timber.d("Loading transactions...")

            if (!koardSdk.isAuthenticated) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(R.string.transaction_history_requires_login),
                        transactions = emptyList()
                    )
                }
                return@withContext
            }

            if (koardSdk.activeLocationId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(R.string.transaction_history_requires_location),
                        transactions = emptyList()
                    )
                }
                return@withContext
            }

            // Call SDK to get transactions
            val result = koardSdk.getTransactions()

            if (result.isSuccess) {
                val transactionResponse = result.getOrNull()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        transactions = transactionResponse?.transactions?.map {
                            it.toTransactionUi()
                        }.orEmpty(),
                        error = null
                    )
                }
                Timber.d("Transactions loaded successfully: ${transactionResponse?.transactions?.size ?: 0} transactions")
            } else {
                val exception = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(
                            R.string.failed_to_load_transactions,
                            exception?.message ?: "Unknown error"
                        )
                    )
                }
                Timber.e(exception, "Failed to load transactions")
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = getApplication<Application>().getString(
                        R.string.failed_to_load_transactions,
                        e.message ?: "Unknown error"
                    )
                )
            }
            Timber.e(e, "Failed to load transactions")
        }
    }

    private fun KoardTransaction.toTransactionUi(): TransactionUI = TransactionUI(
        id = transactionId,
        amount = totalAmount,
        status = status,
        merchant = merchantName,
        card = card,
        date = createdAt
    )

    fun onDispatch(intent: TransactionHistoryIntent) {
        viewModelScope.launch { intents.emit(intent) }
    }
}

data class TransactionHistoryUiState(
    val isLoading: Boolean = false,
    val transactions: List<TransactionUI> = emptyList(),
    val error: String? = null
)

sealed class TransactionHistoryIntent {
    data object LoadTransactions : TransactionHistoryIntent()
}

data class TransactionUI(
    val id: String,
    val amount: Int,
    val status: KoardTransactionStatus,
    val merchant: String,
    val card: String,
    val date: Long
)

sealed interface TransactionHistoryEffect {
    data class ShowError(val message: String) : TransactionHistoryEffect
}
