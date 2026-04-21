package com.koard.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koard.android.R
import com.koard.android.ui.theme.KoardGreen800
import com.koard.android.ui.theme.KoardRed500
import com.koardlabs.merchant.sdk.domain.AmountType
import com.koardlabs.merchant.sdk.domain.KoardLocation
import com.koardlabs.merchant.sdk.domain.KoardReaderStatus

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

    // If user navigates away (tabs) while transaction sheet is showing, cancel the SDK
    // flow so the thin client doesn't keep running, then dismiss the UI sheet.
    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.uiState.value.showTransactionSheet) {
                viewModel.cancelTransaction()
                viewModel.onDispatch(MainScreenIntent.DismissTransactionResult)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with location selector and reader status
            TopBar(
                selectedLocation = uiState.selectedLocation,
                isLoadingLocation = uiState.isLoadingLocations,
                readerStatus = uiState.currentReaderStatus ?: deriveReaderStatusFromSdk(sdkReadiness),
                onLocationClick = { viewModel.onDispatch(MainScreenIntent.OnSelectLocation) }
            )
            uiState.locationError?.let { error ->
                Text(
                    text = error,
                    color = KoardRed500,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Main content area
            if (!uiState.isDeviceEnrolled) {
                // Show centered enrollment section
                EnrollmentSection(
                    selectedLocation = uiState.selectedLocation,
                    isEnrolling = uiState.isEnrollingDevice,
                    enrollmentError = uiState.enrollmentError,
                    onEnroll = { viewModel.onDispatch(MainScreenIntent.EnrollDevice) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Show transaction form
                TransactionForm(
                    uiState = uiState,
                    isProcessingDisabled = uiState.isLoadingLocations || uiState.selectedLocation == null,
                    onAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnAmountChanged(it)) },
                    onTaxAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnTaxAmountChanged(it)) },
                    onTaxTypeToggled = { viewModel.onDispatch(MainScreenIntent.OnTaxTypeToggled) },
                    onTipAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnTipAmountChanged(it)) },
                    onTipTypeToggled = { viewModel.onDispatch(MainScreenIntent.OnTipTypeToggled) },
                    onSurchargeStateChanged = { viewModel.onDispatch(MainScreenIntent.OnSurchargeStateChanged(it)) },
                    onSurchargeAmountChanged = { viewModel.onDispatch(MainScreenIntent.OnSurchargeAmountChanged(it)) },
                    onSurchargeTypeToggled = { viewModel.onDispatch(MainScreenIntent.OnSurchargeTypeToggled) },
                    onStartPreauth = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            viewModel.onDispatch(MainScreenIntent.StartPreauth(activity))
                        }
                    },
                    onStartTransaction = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            viewModel.onDispatch(MainScreenIntent.StartTransaction(activity))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Transaction processing sheet — shown when showTransactionSheet is true
        // Flow may be null initially while the cancel button position is measured
        if (uiState.showTransactionSheet) {
            TransactionProcessingSheet(
                title = uiState.activeTransactionTitle,
                amountLabel = uiState.activeTransactionAmountLabel,
                operationType = when (uiState.activeTransactionTitle) {
                    "Preauthorization" -> TransactionSheetOperationType.PREAUTH
                    "Sale" -> TransactionSheetOperationType.SALE
                    "Complete Authorization" -> TransactionSheetOperationType.PARTIAL_AUTH_COMPLETION
                    else -> TransactionSheetOperationType.UNKNOWN
                },
                transactionFlow = uiState.activeTransactionFlow,
                onClose = { viewModel.onDispatch(MainScreenIntent.DismissTransactionResult) },
                onCancel = { viewModel.cancelTransaction() },
                onTapAnotherCard = { txnId, remaining ->
                    val activity = context.findActivity()
                    if (activity != null) {
                        viewModel.onDispatch(MainScreenIntent.TapAnotherCard(activity, txnId, remaining))
                    }
                },
                onDismissPartialApproval = {
                    viewModel.onDispatch(MainScreenIntent.DismissPartialApproval)
                    viewModel.onDispatch(MainScreenIntent.DismissTransactionResult)
                },
                onCancelButtonMetrics = { x, y, width, height ->
                    viewModel.onDispatch(
                        MainScreenIntent.UpdateCancelButtonMetrics(
                            xDp = x,
                            yDp = y,
                            widthDp = width,
                            heightDp = height
                        )
                    )
                }
            )
        }

        // Surcharge confirmation modal
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

@Composable
private fun TopBar(
    selectedLocation: KoardLocation?,
    isLoadingLocation: Boolean,
    readerStatus: KoardReaderStatus,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Location selector button (left)
        val locationLabel = selectedLocation?.let {
            if (it.name.isNotBlank()) it.name else it.id
        } ?: "Select Location"
        OutlinedButton(
            onClick = onLocationClick,
            enabled = !isLoadingLocation,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (isLoadingLocation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.padding(4.dp))
            }
            Text(
                text = if (selectedLocation == null) locationLabel else "Active: $locationLabel",
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.padding(8.dp))

        // Reader status chip (right)
        ReaderStatusChip(readerStatus = readerStatus)
    }
}

@Composable
private fun ReaderStatusChip(readerStatus: KoardReaderStatus) {
    val (statusText, chipColor) = when (readerStatus) {
        KoardReaderStatus.readyForTap -> "Ready for Tap" to Color(0xFF4CAF50) // Green
        KoardReaderStatus.complete -> "Complete" to Color(0xFF4CAF50) // Green
        KoardReaderStatus.preparing, KoardReaderStatus.processing,
        KoardReaderStatus.cardDetected -> "Processing..." to Color(0xFFFFA726) // Amber
        KoardReaderStatus.readNotCompleted, KoardReaderStatus.readCancelled -> "Error" to Color(0xFFE53935) // Red
        else -> "Not Ready" to Color(0xFF9E9E9E) // Gray
    }

    AssistChip(
        onClick = { },
        label = { Text(statusText, fontWeight = FontWeight.Medium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = chipColor.copy(alpha = 0.2f),
            labelColor = chipColor
        )
    )
}

@Composable
private fun EnrollmentSection(
    selectedLocation: KoardLocation?,
    isEnrolling: Boolean,
    enrollmentError: String?,
    onEnroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.visa.kic.app.kernel"

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (selectedLocation == null) {
            Text(
                text = "Select a location above to enroll device",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        } else {
            Button(
                onClick = onEnroll,
                enabled = !isEnrolling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isEnrolling) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("Enrolling...")
                    }
                } else {
                    Text("Enroll Device", fontSize = 18.sp)
                }
            }

            if (enrollmentError != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Check if error mentions kernel app and show link
                if (enrollmentError.contains("Kernel", ignoreCase = true) ||
                    enrollmentError.contains("kernel app", ignoreCase = true)) {
                    val annotatedString = buildAnnotatedString {
                        append(enrollmentError)
                        append(" ")
                        pushStringAnnotation(tag = "URL", annotation = playStoreUrl)
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append("Install from Play Store")
                        }
                        pop()
                    }

                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = KoardRed500,
                            textAlign = TextAlign.Center
                        ),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    uriHandler.openUri(annotation.item)
                                }
                        }
                    )
                } else {
                    Text(
                        text = enrollmentError,
                        color = KoardRed500,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionForm(
    uiState: MainScreenUiState,
    isProcessingDisabled: Boolean,
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
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.amount,
            onValueChange = { onAmountChanged(filterDecimalInput(it)) },
            label = { Text("Subtotal") },
            placeholder = { Text("0.00") },
            leadingIcon = { Text("$") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Tip input with toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.tipAmount,
                onValueChange = { onTipAmountChanged(filterDecimalInput(it)) },
                label = { Text("Tip") },
                placeholder = { Text("0.00") },
                leadingIcon = { Text(if (uiState.tipType == AmountType.PERCENTAGE) "%" else "$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onTipTypeToggled) {
                Text(if (uiState.tipType == AmountType.PERCENTAGE) "%" else "$")
            }
        }

        // Tax input with toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.taxAmount,
                onValueChange = { onTaxAmountChanged(filterDecimalInput(it)) },
                label = { Text("Tax") },
                placeholder = { Text("0.00") },
                leadingIcon = { Text(if (uiState.taxType == AmountType.PERCENTAGE) "%" else "$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onTaxTypeToggled) {
                Text(if (uiState.taxType == AmountType.PERCENTAGE) "%" else "$")
            }
        }

        // Surcharge state selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Surcharge:", style = MaterialTheme.typography.bodyLarge)
            TextButton(
                onClick = { onSurchargeStateChanged(SurchargeState.OFF) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (uiState.surchargeState == SurchargeState.OFF)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Default")
            }
            TextButton(
                onClick = { onSurchargeStateChanged(SurchargeState.BYPASS) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (uiState.surchargeState == SurchargeState.BYPASS)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Bypass")
            }
            TextButton(
                onClick = { onSurchargeStateChanged(SurchargeState.ENABLE) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (uiState.surchargeState == SurchargeState.ENABLE)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Override")
            }
        }

        // Surcharge amount input (visible when enabled)
        if (uiState.surchargeState == SurchargeState.ENABLE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.surchargeAmount,
                    onValueChange = { onSurchargeAmountChanged(filterDecimalInput(it)) },
                    label = { Text("Surcharge") },
                    placeholder = { Text("0.00") },
                    leadingIcon = { Text(if (uiState.surchargeType == AmountType.PERCENTAGE) "%" else "$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSurchargeTypeToggled) {
                    Text(if (uiState.surchargeType == AmountType.PERCENTAGE) "%" else "$")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = uiState.computedTotalCents?.let { formatCentsToUSD(it) } ?: "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartPreauth,
                enabled = uiState.amount.isNotBlank() && !uiState.isProcessing && !isProcessingDisabled,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Preauth")
            }
            Button(
                onClick = onStartTransaction,
                enabled = uiState.amount.isNotBlank() && !uiState.isProcessing && !isProcessingDisabled,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Sale")
            }
        }

        // Show error messages
        if (!uiState.isProcessing && uiState.finalStatus == null) {
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = KoardRed500,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}


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

    Surface(modifier = modifier, color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = "Surcharge Confirmation Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transaction details
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Original Total:", fontWeight = FontWeight.Medium)
                    Text("${"$%.2f".format(totalMinusSurcharge / 100.0)}", fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Surcharge:", fontWeight = FontWeight.Medium)
                    Text("${"$%.2f".format(surchargeAmountInCents / 100.0)}", fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("New Total:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${"$%.2f".format(newTotal / 100.0)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Override Surcharge (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.surchargeOverrideAmount,
                    onValueChange = onOverrideAmountChanged,
                    label = { Text("Override Amount") },
                    placeholder = { Text("Leave blank to keep original") },
                    leadingIcon = { Text(if (uiState.surchargeOverrideType == AmountType.PERCENTAGE) "%" else "$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOverrideTypeToggled) {
                    Text(if (uiState.surchargeOverrideType == AmountType.PERCENTAGE) "%" else "$")
                }
            }

            uiState.errorMessage?.let {
                Text(text = it, color = KoardRed500, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isConfirmingSurcharge) {
                CircularProgressIndicator(color = KoardGreen800)
            } else {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KoardGreen800)
                    ) {
                        Text("Accept Surcharge", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KoardRed500),
                        border = BorderStroke(1.dp, KoardRed500)
                    ) {
                        Text("Decline Surcharge", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSelectionSheet(
    locations: List<KoardLocation>,
    selectedLocation: KoardLocation?,
    onLocationSelected: (KoardLocation) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            LazyColumn {
                items(locations) { location ->
                    LocationItem(
                        location = location,
                        isSelected = location.id == selectedLocation?.id,
                        onLocationClick = { onLocationSelected(location) },
                        isLast = location == locations.last()
                    )
                }
            }
            Spacer(modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun LocationItem(
    location: KoardLocation,
    isSelected: Boolean,
    onLocationClick: () -> Unit,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLocationClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name.ifBlank { "Location ${location.id}" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = KoardGreen800,
                maxLines = 1
            )
            if (location.name.isNotBlank()) {
                Text(
                    text = location.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = KoardGreen800
            )
        }
    }
    if (!isLast) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
    }
}





private fun deriveReaderStatusFromSdk(sdkReadiness: com.koardlabs.merchant.sdk.domain.KoardSdkReadiness): KoardReaderStatus {
    return if (sdkReadiness.isReadyForTransactions) {
        KoardReaderStatus.readyForTap
    } else {
        KoardReaderStatus.unknown
    }
}

/**
 * Filters input to valid decimal numbers: digits, at most one decimal point, max 2 decimal places.
 */
private fun filterDecimalInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val parts = filtered.split(".")
    return when {
        parts.size <= 1 -> filtered
        else -> parts[0] + "." + parts[1].take(2)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
