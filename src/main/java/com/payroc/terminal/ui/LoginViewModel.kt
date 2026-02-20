package com.payroc.terminal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val sdk = KoardMerchantSdk.getInstance()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onMerchantCodeChange(code: String) {
        _uiState.update { it.copy(merchantCode = code, errorMessage = null) }
    }

    fun onMerchantPinChange(pin: String) {
        _uiState.update { it.copy(merchantPin = pin, errorMessage = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val merchantCode = _uiState.value.merchantCode.trim()
        val merchantPin = _uiState.value.merchantPin.trim()

        if (merchantCode.isEmpty() || merchantPin.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Please enter both merchant code and PIN")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val success = withContext(Dispatchers.IO) {
                    sdk.login(merchantCode, merchantPin)
                }

                if (success) {
                    Timber.d("Login successful")
                    _uiState.update { it.copy(isLoading = false) }
                    // Call onSuccess on Main thread for navigation
                    onSuccess()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Login failed. Please check your credentials."
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Login error")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Login error: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }
}

data class LoginUiState(
    val merchantCode: String = "",
    val merchantPin: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
