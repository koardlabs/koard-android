package com.payroc.terminal.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.koardlabs.merchant.sdk.domain.AmountType
import com.koardlabs.merchant.sdk.domain.KoardLocation
import com.koardlabs.merchant.sdk.domain.KoardReaderStatus
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sdkReadiness by viewModel.sdkReadiness.collectAsState()
    val context = LocalContext.current
    var showLocationSheet by remember { mutableStateOf(false) }
    var availableLocations by remember { mutableStateOf<List<KoardLocation>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainScreenEffect.ShowLocationSheet -> {
                    availableLocations = effect.locations
                    showLocationSheet = true
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!uiState.isDeviceEnrolled) {
            // Enrollment screen
            EnrollmentScreen(
                selectedLocation = uiState.selectedLocation,
                isLoadingLocation = uiState.isLoadingLocations,
                isEnrolling = uiState.isEnrollingDevice,
                enrollmentError = uiState.enrollmentError,
                readerStatus = uiState.currentReaderStatus ?: deriveReaderStatusFromSdk(sdkReadiness),
                onLocationClick = { viewModel.onDispatch(MainScreenIntent.OnSelectLocation) },
                onEnroll = { viewModel.onDispatch(MainScreenIntent.EnrollDevice) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Checkout PIN pad screen
            CheckoutScreen(
                uiState = uiState,
                sdkReadiness = sdkReadiness,
                onLocationClick = { viewModel.onDispatch(MainScreenIntent.OnSelectLocation) },
                onAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnAmountChanged(it)) },
                onTaxAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnTaxAmountChanged(it)) },
                onTaxTypeToggled = { viewModel.onDispatch(MainScreenIntent.OnTaxTypeToggled) },
                onTipAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnTipAmountChanged(it)) },
                onTipTypeToggled = { viewModel.onDispatch(MainScreenIntent.OnTipTypeToggled) },
                onSurchargeStateChanged = { viewModel.onDispatch(MainScreenIntent.OnSurchargeStateChanged(it)) },
                onSurchargeAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnSurchargeAmountChanged(it)) },
                onSurchargeTypeToggled = { viewModel.onDispatch(MainScreenIntent.OnSurchargeTypeToggled) },
                onStartPreauth = {
                    context.findActivity()?.let { viewModel.onDispatch(MainScreenIntent.StartPreauth(it)) }
                },
                onStartTransaction = {
                    context.findActivity()?.let { viewModel.onDispatch(MainScreenIntent.StartTransaction(it)) }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Transaction processing overlay
        if (uiState.isProcessing || uiState.finalStatus != null) {
            TransactionOverlay(
                uiState = uiState,
                onClose = { viewModel.onDispatch(MainScreenIntent.DismissTransactionResult) },
                onCancelButtonMetrics = { x, y, w, h ->
                    viewModel.onDispatch(MainScreenIntent.UpdateCancelButtonMetrics(x, y, w, h))
                }
            )
        }

        // Surcharge confirmation overlay
        if (uiState.showSurchargeConfirmation) {
            SurchargeConfirmationModal(
                uiState = uiState,
                onOverrideAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnSurchargeOverrideAmountChanged(it)) },
                onOverrideTypeToggled = { viewModel.onDispatch(MainScreenIntent.OnSurchargeOverrideTypeToggled) },
                onConfirm = { viewModel.onDispatch(MainScreenIntent.ConfirmSurcharge(true)) },
                onDecline = { viewModel.onDispatch(MainScreenIntent.ConfirmSurcharge(false)) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Location selection bottom sheet
    if (showLocationSheet) {
        LocationSelectionSheet(
            locations = availableLocations,
            selectedLocation = uiState.selectedLocation,
            onLocationSelected = { location ->
                showLocationSheet = false
                viewModel.onDispatch(MainScreenIntent.OnLocationSelected(location))
            },
            onDismiss = { showLocationSheet = false }
        )
    }
}

// ─── Enrollment Screen ────────────────────────────────────────────────────────

@Composable
private fun EnrollmentScreen(
    selectedLocation: KoardLocation?,
    isLoadingLocation: Boolean,
    isEnrolling: Boolean,
    enrollmentError: String?,
    readerStatus: KoardReaderStatus,
    onLocationClick: () -> Unit,
    onEnroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(PayrocWhite)) {
        // Navy top header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PayrocNavy)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PayrocLogo(variant = PayrocLogoVariant.OnDark, iconSize = 32.dp, fontSize = 20.sp)
                    ReaderStatusPill(readerStatus)
                }
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Location selector
            OutlinedButton(
                onClick = onLocationClick,
                enabled = !isLoadingLocation,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, PayrocBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PayrocBlue)
            ) {
                if (isLoadingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PayrocBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedLocation?.let { if (it.name.isNotBlank()) it.name else it.id } ?: "Select Location",
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (selectedLocation != null) {
                Text(
                    text = "Device Setup Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = PayrocDarkText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enroll this device to start accepting tap payments.",
                    fontSize = 15.sp,
                    color = PayrocMediumGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onEnroll,
                    enabled = !isEnrolling,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PayrocBlue,
                        contentColor = PayrocWhite,
                        disabledContainerColor = PayrocLightGray
                    )
                ) {
                    if (isEnrolling) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PayrocWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enrolling...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Enroll Device", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (enrollmentError != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = enrollmentError, color = PayrocRed, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    text = "Select a location above to get started",
                    fontSize = 16.sp,
                    color = PayrocMediumGray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Checkout Screen (iOS PIN pad style) ─────────────────────────────────────

@Composable
private fun CheckoutScreen(
    uiState: MainScreenUiState,
    sdkReadiness: com.koardlabs.merchant.sdk.domain.KoardSdkReadiness,
    onLocationClick: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onTaxAmountChanged: (String) -> Unit,
    onTaxTypeToggled: () -> Unit,
    onTipAmountChanged: (String) -> Unit,
    onTipTypeToggled: () -> Unit,
    onSurchargeStateChanged: (SurchargeState) -> Unit,
    onSurchargeAmountChanged: (String) -> Unit,
    onSurchargeTypeToggled: () -> Unit,
    onStartPreauth: () -> Unit,
    onStartTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val readerStatus = uiState.currentReaderStatus ?: deriveReaderStatusFromSdk(sdkReadiness)
    val isDisabled = uiState.isLoadingLocations || uiState.selectedLocation == null

    Column(modifier = modifier.background(PayrocWhite)) {
        // ── Navy top area with amount display ──────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PayrocNavy)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top row: location + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location selector
                TextButton(
                    onClick = onLocationClick,
                    enabled = !uiState.isLoadingLocations,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    if (uiState.isLoadingLocations) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = PayrocWhite)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = uiState.selectedLocation?.let {
                            if (it.name.isNotBlank()) it.name else it.id
                        } ?: "Select Location",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }

                ReaderStatusPill(readerStatus)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Currency label
            Text(
                text = "USD",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Large amount display
            val displayAmount = if (uiState.amount.isBlank()) "0.00" else uiState.amount
            Text(
                text = "$$displayAmount",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = PayrocWhite,
                letterSpacing = (-1).sp
            )

            // Total breakdown if there's tip/tax
            val total = uiState.computedTotalCents
            if (total != null && uiState.amount.isNotBlank()) {
                val subtotalCents = (uiState.amount.toDoubleOrNull() ?: 0.0) * 100
                if (total.toLong() != subtotalCents.toLong()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total: ${formatCentsToUSD(total)}",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            uiState.locationError?.let {
                Text(text = it, color = Color(0xFFFF7B7B), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }

        // ── White bottom card ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(PayrocWhite)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Amount input
                CheckoutTextField(
                    value = uiState.amount,
                    onValueChange = onAmountChanged,
                    label = "Subtotal",
                    prefix = "$",
                    keyboardType = KeyboardType.Decimal
                )

                // Tip row
                AmountInputRow(
                    value = uiState.tipAmount,
                    onValueChange = onTipAmountChanged,
                    label = "Tip",
                    prefix = if (uiState.tipType == AmountType.PERCENTAGE) "%" else "$",
                    typeLabel = if (uiState.tipType == AmountType.PERCENTAGE) "%" else "$",
                    onTypeToggle = onTipTypeToggled
                )

                // Tax row
                AmountInputRow(
                    value = uiState.taxAmount,
                    onValueChange = onTaxAmountChanged,
                    label = "Tax",
                    prefix = if (uiState.taxType == AmountType.PERCENTAGE) "%" else "$",
                    typeLabel = if (uiState.taxType == AmountType.PERCENTAGE) "%" else "$",
                    onTypeToggle = onTaxTypeToggled
                )

                // Surcharge selector
                SurchargeRow(
                    surchargeState = uiState.surchargeState,
                    surchargeAmount = uiState.surchargeAmount,
                    surchargeType = uiState.surchargeType,
                    onStateChanged = onSurchargeStateChanged,
                    onAmountChanged = onSurchargeAmountChanged,
                    onTypeToggled = onSurchargeTypeToggled
                )

                HorizontalDivider(color = PayrocLightGray, thickness = 0.5.dp)

                // Total row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, PayrocBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PayrocDarkText)
                    Text(
                        text = uiState.computedTotalCents?.let { formatCentsToUSD(it) } ?: "--",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayrocBlue
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onStartPreauth,
                        enabled = uiState.amount.isNotBlank() && !uiState.isProcessing && !isDisabled,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, PayrocBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PayrocBlue)
                    ) {
                        Text("Preauth", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onStartTransaction,
                        enabled = uiState.amount.isNotBlank() && !uiState.isProcessing && !isDisabled,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PayrocBlue,
                            contentColor = PayrocWhite,
                            disabledContainerColor = PayrocLightGray
                        )
                    ) {
                        Text("Charge Now", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                uiState.errorMessage?.let {
                    Text(text = it, color = PayrocRed, fontSize = 13.sp, textAlign = TextAlign.Center)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ─── Helper Composables ───────────────────────────────────────────────────────

@Composable
private fun CheckoutTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: String,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("0.00", color = PayrocLightGray) },
        leadingIcon = { Text(prefix, color = PayrocMediumGray, fontWeight = FontWeight.Medium) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
private fun AmountInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: String,
    typeLabel: String,
    onTypeToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text("0.00", color = PayrocLightGray) },
            leadingIcon = { Text(prefix, color = PayrocMediumGray, fontWeight = FontWeight.Medium) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
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
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PayrocBlue.copy(alpha = 0.08f))
                .clickable { onTypeToggle() },
            contentAlignment = Alignment.Center
        ) {
            Text(typeLabel, color = PayrocBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SurchargeRow(
    surchargeState: SurchargeState,
    surchargeAmount: String,
    surchargeType: AmountType,
    onStateChanged: (SurchargeState) -> Unit,
    onAmountChanged: (String) -> Unit,
    onTypeToggled: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Surcharge", fontSize = 13.sp, color = PayrocMediumGray, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SurchargeChip("Default", surchargeState == SurchargeState.OFF) { onStateChanged(SurchargeState.OFF) }
            SurchargeChip("Bypass", surchargeState == SurchargeState.BYPASS) { onStateChanged(SurchargeState.BYPASS) }
            SurchargeChip("Override", surchargeState == SurchargeState.ENABLE) { onStateChanged(SurchargeState.ENABLE) }
        }
        if (surchargeState == SurchargeState.ENABLE) {
            AmountInputRow(
                value = surchargeAmount,
                onValueChange = onAmountChanged,
                label = "Surcharge Amount",
                prefix = if (surchargeType == AmountType.PERCENTAGE) "%" else "$",
                typeLabel = if (surchargeType == AmountType.PERCENTAGE) "%" else "$",
                onTypeToggle = onTypeToggled
            )
        }
    }
}

@Composable
private fun SurchargeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PayrocBlue else PayrocLightestGray)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PayrocWhite else PayrocMediumGray
        )
    }
}

@Composable
private fun ReaderStatusPill(readerStatus: KoardReaderStatus) {
    val (label, bg, fg) = when (readerStatus) {
        KoardReaderStatus.readyForTap -> Triple("Ready", Color(0xFF22A550).copy(alpha = 0.15f), Color(0xFF22A550))
        KoardReaderStatus.complete -> Triple("Complete", Color(0xFF22A550).copy(alpha = 0.15f), Color(0xFF22A550))
        KoardReaderStatus.preparing, KoardReaderStatus.processing,
        KoardReaderStatus.cardDetected -> Triple("Processing", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B))
        KoardReaderStatus.readNotCompleted, KoardReaderStatus.readCancelled ->
            Triple("Error", Color(0xFFD32F2F).copy(alpha = 0.15f), Color(0xFFFF7B7B))
        else -> Triple("Not Ready", Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.5f))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

// ─── Transaction Overlay ─────────────────────────────────────────────────────

@Composable
private fun TransactionOverlay(
    uiState: MainScreenUiState,
    onClose: () -> Unit,
    onCancelButtonMetrics: (Int, Int, Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val (statusTitle, statusSubtitle) = resolveTransactionStatus(uiState)
    val isSuccess = uiState.finalStatus?.let {
        it.contains("Approve", ignoreCase = true) || it.contains("Captured", ignoreCase = true)
    } ?: false
    val isError = uiState.errorMessage != null
    val isProcessing = uiState.isProcessing && uiState.finalStatus == null

    Surface(modifier = Modifier.fillMaxSize(), color = PayrocNavy) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProcessing) {
                    IconButton(
                        onClick = { /* cancel tracked via metrics */ },
                        modifier = Modifier
                            .size(40.dp)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInWindow()
                                val size: IntSize = coordinates.size
                                val xDp = with(density) { pos.x.toDp().value.roundToInt() }
                                val yDp = with(density) { pos.y.toDp().value.roundToInt() }
                                val widthDp = with(density) { size.width.toDp().value.roundToInt() }
                                val heightDp = with(density) { size.height.toDp().value.roundToInt() }
                                onCancelButtonMetrics(xDp, yDp, widthDp, heightDp)
                            }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = PayrocWhite)
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                PayrocLogo(variant = PayrocLogoVariant.OnDark, iconSize = 28.dp, fontSize = 18.sp)
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Status icon / indicator
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        color = PayrocBlue,
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 4.dp
                    )
                }
                isSuccess -> {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22A550)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = PayrocWhite,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                isError || uiState.finalStatus != null -> {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(PayrocRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = PayrocWhite,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = statusTitle,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = PayrocWhite,
                textAlign = TextAlign.Center
            )

            statusSubtitle?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    fontSize = 15.sp,
                    color = PayrocWhite.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // Transaction details card
            if (!isProcessing && uiState.transaction != null) {
                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val txn = uiState.transaction!!

                        OverlayDetailRow("Subtotal", formatCentsToUSD(txn.subtotal))
                        if (txn.tipAmount > 0) OverlayDetailRow("Tip", formatCentsToUSD(txn.tipAmount))
                        if (txn.taxAmount > 0) OverlayDetailRow("Tax", formatCentsToUSD(txn.taxAmount))
                        if (txn.surchargeAmount > 0) OverlayDetailRow("Surcharge", formatCentsToUSD(txn.surchargeAmount.toInt()))

                        HorizontalDivider(color = PayrocWhite.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", fontWeight = FontWeight.Bold, color = PayrocWhite, fontSize = 16.sp)
                            Text(
                                formatCentsToUSD(txn.totalAmount),
                                fontWeight = FontWeight.Bold,
                                color = PayrocWhite,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Close / Done button
            if (!isProcessing && (uiState.errorMessage != null || uiState.finalStatus != null)) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PayrocBlue,
                        contentColor = PayrocWhite
                    )
                ) {
                    Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun OverlayDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = PayrocWhite.copy(alpha = 0.6f), fontSize = 14.sp)
        Text(value, color = PayrocWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Surcharge Confirmation ───────────────────────────────────────────────────

@Composable
private fun SurchargeConfirmationModal(
    uiState: MainScreenUiState,
    onOverrideAmountChanged: (String) -> Unit,
    onOverrideTypeToggled: () -> Unit,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transaction = uiState.transaction ?: return
    val originalTotal = transaction.totalAmount
    val surchargeAmountInCents = (transaction.surchargeAmount / 100 * 100).toInt()
    val totalMinusSurcharge = originalTotal - surchargeAmountInCents

    val overrideAmount = uiState.surchargeOverrideAmount.trim()
    val newTotal = if (overrideAmount.isNotBlank()) {
        val overrideValue = overrideAmount.toDoubleOrNull() ?: 0.0
        val newSurcharge = if (uiState.surchargeOverrideType == AmountType.PERCENTAGE) {
            ((totalMinusSurcharge.toDouble() * overrideValue) / 100.0).toInt()
        } else {
            (overrideValue * 100).toInt()
        }
        totalMinusSurcharge + newSurcharge
    } else {
        originalTotal
    }

    Surface(modifier = modifier, color = PayrocWhite) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(PayrocBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PayrocBlue)
            }

            Text(
                text = "Surcharge Confirmation",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PayrocDarkText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Details card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PayrocLightestGray)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SurchargeDetailRow("Original Total", "${"$%.2f".format(totalMinusSurcharge / 100.0)}")
                    SurchargeDetailRow("Surcharge", "${"$%.2f".format(surchargeAmountInCents / 100.0)}")
                    HorizontalDivider(color = PayrocLightGray)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("New Total", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PayrocDarkText)
                        Text(
                            "${"$%.2f".format(newTotal / 100.0)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = PayrocBlue
                        )
                    }
                }
            }

            // Override input
            Text(
                "Override Surcharge (Optional)",
                fontSize = 14.sp,
                color = PayrocMediumGray,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )

            AmountInputRow(
                value = uiState.surchargeOverrideAmount,
                onValueChange = onOverrideAmountChanged,
                label = "Override Amount",
                prefix = if (uiState.surchargeOverrideType == AmountType.PERCENTAGE) "%" else "$",
                typeLabel = if (uiState.surchargeOverrideType == AmountType.PERCENTAGE) "%" else "$",
                onTypeToggle = onOverrideTypeToggled
            )

            uiState.errorMessage?.let {
                Text(text = it, color = PayrocRed, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isConfirmingSurcharge) {
                CircularProgressIndicator(color = PayrocBlue)
            } else {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PayrocBlue, contentColor = PayrocWhite)
                    ) {
                        Text("Accept Surcharge", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, PayrocRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PayrocRed)
                    ) {
                        Text("Decline Surcharge", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurchargeDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = PayrocMediumGray, fontSize = 14.sp)
        Text(value, color = PayrocDarkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Location Sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSelectionSheet(
    locations: List<KoardLocation>,
    selectedLocation: KoardLocation?,
    onLocationSelected: (KoardLocation) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PayrocWhite
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Select Location",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PayrocDarkText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            HorizontalDivider(color = PayrocLightGray)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(locations) { location ->
                    val isSelected = location.id == selectedLocation?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLocationSelected(location) }
                            .background(if (isSelected) PayrocBlue.copy(alpha = 0.05f) else PayrocWhite)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = location.name.ifBlank { "Location ${location.id}" },
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = if (isSelected) PayrocBlue else PayrocDarkText
                            )
                            if (location.name.isNotBlank()) {
                                Text(
                                    text = location.id,
                                    fontSize = 13.sp,
                                    color = PayrocMediumGray
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = PayrocBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = PayrocLightGray, thickness = 0.5.dp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun formatCentsToUSD(cents: Int): String = "${"$%.2f".format(cents / 100.0)}"

private fun resolveTransactionStatus(uiState: MainScreenUiState): Pair<String, String?> {
    if (uiState.errorMessage != null) return "Payment Failed" to uiState.errorMessage
    if (uiState.finalStatus != null) return uiState.finalStatus to uiState.visaStatusDescription
    if (uiState.isProcessing) {
        val label = when (uiState.currentReaderStatus) {
            KoardReaderStatus.preparing -> "Preparing..."
            KoardReaderStatus.readyForTap -> "Tap Your Card"
            KoardReaderStatus.cardDetected -> "Card Detected"
            KoardReaderStatus.pinEntryRequested -> "Enter PIN"
            KoardReaderStatus.pinEntryCompleted -> "PIN Captured"
            KoardReaderStatus.readCompleted -> "Read Complete"
            KoardReaderStatus.readNotCompleted -> "Read Failed"
            KoardReaderStatus.readCancelled -> "Cancelled"
            KoardReaderStatus.readRetry -> "Please Retry"
            KoardReaderStatus.processing -> "Processing"
            KoardReaderStatus.complete -> "Completing..."
            KoardReaderStatus.developerModeEnabled -> "Blocked"
            else -> "Processing..."
        }
        return label to uiState.statusMessages.lastOrNull()
    }
    return "Transaction" to null
}

private fun deriveReaderStatusFromSdk(sdkReadiness: com.koardlabs.merchant.sdk.domain.KoardSdkReadiness): KoardReaderStatus {
    return if (sdkReadiness.isReadyForTransactions) KoardReaderStatus.readyForTap else KoardReaderStatus.unknown
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
