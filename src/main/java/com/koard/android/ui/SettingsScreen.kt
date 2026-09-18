package com.koard.android.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.koard.android.R
import com.koard.android.DemoApplication
import com.koard.android.MainActivity

import com.koardlabs.merchant.sdk.KoardMerchantSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val activity = LocalContext.current as? MainActivity

    // Keep navigation and transaction controls inaccessible during teardown.
    if (uiState.isResetting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Resetting SDK") },
            text = { CircularProgressIndicator() }
        )
    }

    // Refresh settings every time the screen is displayed
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Merchant Info
        uiState.merchantName?.let { name ->
            StatusRow("Merchant", name)
        }
        uiState.merchantId?.let { id ->
            StatusRow("Merchant ID", id)
        }
        if (uiState.merchantName != null || uiState.merchantId != null) {
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Kernel App Status - only show if NOT installed
        if (!uiState.isKernelInstalled) {
            KernelStatusRow(uiState.isKernelInstalled)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Developer Mode - only show if ENABLED
        if (uiState.isDeveloperModeEnabled) {
            StatusRow("Developer Mode", "Enabled ⚠️")
            Spacer(modifier = Modifier.height(24.dp))
        }

        // SDK Status
        StatusRow("SDK Status", uiState.sdkStatusMessage)
        StatusRow("SDK Initialized", uiState.isInitialized.toString())
        StatusRow("Device Enrolled", uiState.isDeviceEnrolled.toString())
        Spacer(modifier = Modifier.height(24.dp))

        // Timeout Configuration
        Text(
            text = "Timeouts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.tapTimeoutSeconds,
            onValueChange = { viewModel.onTapTimeoutChanged(it) },
            label = { Text("Tap Timeout (seconds)") },
            placeholder = { Text("No auto-cancel") },
            supportingText = { Text("Auto-cancels tap after this duration. Leave empty to disable.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Unenroll Device Button
        if (uiState.canUnenroll) {
            Button(
                onClick = { viewModel.unenrollDevice() },
                enabled = !uiState.isUnenrolling && !uiState.isSessionBusy && uiState.isInitialized,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (uiState.isUnenrolling) {
                    Text("Unenrolling...")
                } else {
                    Text("Unenroll Device")
                }
            }
            if (uiState.unenrollError != null) {
                Text(
                    text = uiState.unenrollError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Logout Button
        Button(
            onClick = { viewModel.logout(onLogout) },
            enabled = !uiState.isSessionBusy && !uiState.isUnenrolling && uiState.isInitialized,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Reset clears the merchant session and reader enrollment, then initializes a fresh SDK instance. Log in and enroll again afterward.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = {
                viewModel.resetSdk {
                    activity?.refreshNfcRegistration()
                    onLogout()
                }
            },
            enabled = !uiState.isSessionBusy && !uiState.isUnenrolling,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset SDK and sign out")
        }
        uiState.sessionError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun KernelStatusRow(isInstalled: Boolean) {
    val uriHandler = LocalUriHandler.current
    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.visa.kic.app.kernel"

    if (isInstalled) {
        Text(
            text = "Kernel App is installed ✓",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    } else {
        val annotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color.Gray)) {
                append("Kernel app needs to be installed: ")
            }
            pushStringAnnotation(tag = "URL", annotation = playStoreUrl)
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append("Install")
            }
            pop()
        }

        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        uriHandler.openUri(annotation.item)
                    }
            }
        )
    }
}



class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sdk get() = KoardMerchantSdk.getInstance()
    private val readinessState = sdk.readinessState
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadAccountInfo()

        // Observe readiness state changes to update UI dynamically
        viewModelScope.launch {
            readinessState.collect { readiness ->
                val canUnenroll = readiness.enrollmentState == com.koardlabs.merchant.sdk.domain.EnrollmentState.Enrolled ||
                    readiness.enrollmentState is com.koardlabs.merchant.sdk.domain.EnrollmentState.Failed ||
                    readiness.certificateState == com.koardlabs.merchant.sdk.domain.CertificateState.Generated
                _uiState.update { currentState ->
                    currentState.copy(
                        isKernelInstalled = readiness.kernelAppInstalled,
                        isDeveloperModeEnabled = readiness.isDeveloperModeEnabled,
                        sdkStatusMessage = readiness.getStatusMessage(),
                        isDeviceEnrolled = readiness.enrollmentState == com.koardlabs.merchant.sdk.domain.EnrollmentState.Enrolled,
                        canUnenroll = canUnenroll,
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        if (!KoardMerchantSdk.isInitialized()) {
            _uiState.update { it.copy(isInitialized = false, sdkStatusMessage = "Not initialized") }
            return
        }
        val isDeveloperMode = sdk.isDeveloperModeEnabled()
        val readiness = readinessState.value
        val readinessMessage = readiness.getStatusMessage()
        val isEnrolled = sdk.isDeviceEnrolled
        val deviceId = sdk.getStoredDeviceId()
        val vacDeviceId = sdk.getVacDeviceId()
        val canUnenroll = readiness.enrollmentState == com.koardlabs.merchant.sdk.domain.EnrollmentState.Enrolled ||
            readiness.enrollmentState is com.koardlabs.merchant.sdk.domain.EnrollmentState.Failed ||
            readiness.certificateState == com.koardlabs.merchant.sdk.domain.CertificateState.Generated

        _uiState.update {
            it.copy(
                isInitialized = true,
                isKernelInstalled = sdk.isKernelAppInstalled,
                isDeveloperModeEnabled = isDeveloperMode,
                sdkStatusMessage = readinessMessage,
                isDeviceEnrolled = isEnrolled,
                deviceId = deviceId,
                vacDeviceId = vacDeviceId,
                canUnenroll = canUnenroll,
                tapTimeoutSeconds = TimeoutSettings.tapTimeoutSeconds,
            )
        }
    }



    private fun loadAccountInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = sdk.getMerchantAccount()
                val account = result.getOrNull()
                if (account != null) {
                    _uiState.update {
                        it.copy(
                            merchantName = account.name,
                            merchantId = account.merchantId ?: account.id
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load account info")
            }
        }
    }

    fun refreshSettings() {
        loadSettings()
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sdk.refreshDeviceCertificates()
            } catch (e: Exception) {
                // Ignore errors
            }
            // Reload settings after refresh
            loadSettings()
        }
    }

    fun unenrollDevice() {
        if (_uiState.value.isSessionBusy || _uiState.value.isUnenrolling) return
        _uiState.update { it.copy(isUnenrolling = true, unenrollError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isUnenrolling = true, unenrollError = null) }
                val result = sdk.unenrollDevice()
                Timber.d("Unenroll result: $result")
                if (result.contains("failed", ignoreCase = true) || result.contains("Unable", ignoreCase = true)) {
                    _uiState.update { it.copy(isUnenrolling = false, unenrollError = result) }
                } else {
                    _uiState.update { it.copy(isUnenrolling = false, unenrollError = null) }
                }
                loadSettings()
            } catch (e: Exception) {
                Timber.w(e, "Failed to unenroll device")
                _uiState.update { it.copy(isUnenrolling = false, unenrollError = e.message) }
            }
        }
    }

    fun onTapTimeoutChanged(value: String) {
        TimeoutSettings.tapTimeoutSeconds = value
        _uiState.update { it.copy(tapTimeoutSeconds = value) }
    }

    fun logout(onSuccess: () -> Unit) {
        if (_uiState.value.isSessionBusy || _uiState.value.isUnenrolling) return
        _uiState.update { it.copy(isSessionBusy = true, sessionError = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { sdk.logout() }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to logout")
                _uiState.update { it.copy(sessionError = e.message ?: "Logout failed") }
            } finally {
                _uiState.update { it.copy(isSessionBusy = false) }
            }
        }
    }

    fun resetSdk(onSuccess: () -> Unit) {
        if (_uiState.value.isSessionBusy || _uiState.value.isUnenrolling) return
        _uiState.update { it.copy(isSessionBusy = true, isResetting = true, sessionError = null) }
        viewModelScope.launch {
            try {
                getApplication<DemoApplication>().resetSdk()
                // Navigation pops the authenticated graph and its ViewModels;
                // the next session collects readiness from the new SDK instance.
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SDK reset failed")
                _uiState.update { it.copy(sessionError = e.message ?: "SDK reset failed") }
            } finally {
                _uiState.update {
                    it.copy(
                        isSessionBusy = false,
                        isResetting = false,
                        isInitialized = KoardMerchantSdk.isInitialized()
                    )
                }
            }
        }
    }
}

data class SettingsUiState(
    val isInitialized: Boolean = KoardMerchantSdk.isInitialized(),
    val isSessionBusy: Boolean = false,
    val isResetting: Boolean = false,
    val sessionError: String? = null,
    val isKernelInstalled: Boolean = false,
    val isDeveloperModeEnabled: Boolean = false,
    val sdkStatusMessage: String = "Unknown",
    val isDeviceEnrolled: Boolean = false,
    val deviceId: String? = null,
    val vacDeviceId: String? = null,
    val canUnenroll: Boolean = false,
    val isUnenrolling: Boolean = false,
    val unenrollError: String? = null,
    val tapTimeoutSeconds: String = "",
    val merchantName: String? = null,
    val merchantId: String? = null,
)

/**
 * Shared timeout settings accessible across ViewModels.
 * Tap timeout is in-memory only (resets each app session).
 */
object TimeoutSettings {
    var tapTimeoutSeconds: String = ""

    val tapTimeoutMs: Long?
        get() = tapTimeoutSeconds.toLongOrNull()?.let { it * 1000L }
}
