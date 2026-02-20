package com.payroc.terminal.ui

import android.app.Application
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.payroc.terminal.ui.components.PayrocLogo
import com.payroc.terminal.ui.components.PayrocLogoVariant
import com.payroc.terminal.ui.theme.PayrocBlue
import com.payroc.terminal.ui.theme.PayrocDarkText
import com.payroc.terminal.ui.theme.PayrocLightGray
import com.payroc.terminal.ui.theme.PayrocLightestGray
import com.payroc.terminal.ui.theme.PayrocMediumGray
import com.payroc.terminal.ui.theme.PayrocNavy
import com.payroc.terminal.ui.theme.PayrocRed
import com.payroc.terminal.ui.theme.PayrocWhite
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshSettings() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PayrocLightestGray)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PayrocNavy)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            PayrocLogo(
                variant = PayrocLogoVariant.OnDark,
                iconSize = 44.dp,
                fontSize = 26.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Device Status Section ──────────────────────────────────────────
        SettingsSection(title = "Device Status") {
            // Kernel App warning (only if not installed)
            if (!uiState.isKernelInstalled) {
                KernelWarningRow()
                HorizontalDivider(color = PayrocLightGray, thickness = 0.5.dp)
            }

            // Developer mode warning (only if enabled)
            if (uiState.isDeveloperModeEnabled) {
                SettingsStatusRow(
                    label = "Developer Mode",
                    value = "Enabled",
                    valueColor = Color(0xFFF59E0B),
                    icon = {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                HorizontalDivider(color = PayrocLightGray, thickness = 0.5.dp)
            }

            SettingsStatusRow(
                label = "SDK Status",
                value = uiState.sdkStatusMessage
            )

            HorizontalDivider(color = PayrocLightGray, thickness = 0.5.dp)

            SettingsStatusRow(
                label = "Device Enrolled",
                value = if (uiState.isDeviceEnrolled) "Enrolled" else "Not Enrolled",
                valueColor = if (uiState.isDeviceEnrolled) Color(0xFF22A550) else PayrocMediumGray,
                icon = if (uiState.isDeviceEnrolled) {
                    {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF22A550),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Device Info Section ────────────────────────────────────────────
        if (!uiState.deviceId.isNullOrBlank() || !uiState.vacDeviceId.isNullOrBlank()) {
            SettingsSection(title = "Device Info") {
                uiState.deviceId?.takeIf { it.isNotBlank() }?.let { id ->
                    SettingsInfoRow("Device ID", id)
                    HorizontalDivider(color = PayrocLightGray, thickness = 0.5.dp)
                }
                uiState.vacDeviceId?.takeIf { it.isNotBlank() }?.let { id ->
                    SettingsInfoRow("VAC Device ID", id)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Logout ─────────────────────────────────────────────────────────
        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, PayrocBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PayrocBlue)
            ) {
                Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PayrocMediumGray,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PayrocWhite)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsStatusRow(
    label: String,
    value: String,
    valueColor: Color = PayrocDarkText,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = PayrocDarkText)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.invoke()
            Text(value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, fontSize = 13.sp, color = PayrocMediumGray)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            fontSize = 14.sp,
            color = PayrocDarkText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun KernelWarningRow() {
    val uriHandler = LocalUriHandler.current
    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.visa.kic.app.kernel"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = PayrocRed, modifier = Modifier.size(18.dp))
            Text("Kernel App", fontSize = 15.sp, color = PayrocDarkText)
        }

        val annotatedString = buildAnnotatedString {
            pushStringAnnotation(tag = "URL", annotation = playStoreUrl)
            withStyle(style = SpanStyle(color = PayrocBlue, fontWeight = FontWeight.SemiBold)) {
                append("Install")
            }
            pop()
        }

        ClickableText(
            text = annotatedString,
            onClick = { offset ->
                annotatedString.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()?.let { uriHandler.openUri(it.item) }
            }
        )
    }
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sdk = KoardMerchantSdk.getInstance()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            sdk.readinessState.collect { readiness ->
                _uiState.update { state ->
                    state.copy(
                        isKernelInstalled = readiness.kernelAppInstalled,
                        isDeveloperModeEnabled = readiness.isDeveloperModeEnabled,
                        sdkStatusMessage = readiness.getStatusMessage(),
                        isDeviceEnrolled = readiness.enrollmentState == com.koardlabs.merchant.sdk.domain.EnrollmentState.Enrolled
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                isKernelInstalled = sdk.isKernelAppInstalled,
                isDeveloperModeEnabled = sdk.isDeveloperModeEnabled(),
                sdkStatusMessage = sdk.readinessState.value.getStatusMessage(),
                isDeviceEnrolled = sdk.isDeviceEnrolled,
                deviceId = sdk.getStoredDeviceId(),
                vacDeviceId = sdk.getVacDeviceId()
            )
        }
    }

    fun refreshSettings() { loadSettings() }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try { sdk.logout() } catch (e: Exception) { Timber.w(e, "Failed to logout") }
            loadSettings()
        }
    }
}

data class SettingsUiState(
    val isKernelInstalled: Boolean = false,
    val isDeveloperModeEnabled: Boolean = false,
    val sdkStatusMessage: String = "Unknown",
    val isDeviceEnrolled: Boolean = false,
    val deviceId: String? = null,
    val vacDeviceId: String? = null
)
