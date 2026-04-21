package com.koard.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koard.android.R
import com.koard.android.ui.theme.KoardAndroidSDKTheme
import com.koard.android.ui.theme.KoardGreen800
import com.koard.android.ui.theme.KoardRed500
import com.koard.android.utils.PhoneNumberVisualTransformation
import com.koardlabs.merchant.sdk.domain.KoardTransactionStatus

private const val PARTIAL_APPROVAL_STATUS = "partial_approval"

@Composable
fun TransactionDetailsLayout(
    transactionDetailsUI: TransactionDetailsUI,
    receiptInputType: ReceiptInputType = ReceiptInputType.NONE,
    isSendEnabled: Boolean = false,
    isSmsSendEnabled: Boolean = false,
    isSendingReceipt: Boolean = false,
    onDispatch: (TransactionDetailsIntent) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp, horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.transaction_id_label, transactionDetailsUI.id),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            LineItem(
                label = stringResource(R.string.transaction_type),
                itemValue = transactionDetailsUI.getTransactionTypeDisplayName(context)
            )
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.status), transactionDetailsUI.status.displayName)
            if (transactionDetailsUI.statusReason.isNotBlank()) {
                HorizontalDivider(thickness = .4.dp)
                LineItem("Status Reason", transactionDetailsUI.statusReason)
            }
            if (transactionDetailsUI.approvalCode.isNotBlank()) {
                HorizontalDivider(thickness = .4.dp)
                LineItem("Approval Code", transactionDetailsUI.approvalCode)
            }
            if (transactionDetailsUI.processorResponseCode.isNotBlank()) {
                HorizontalDivider(thickness = .4.dp)
                LineItem("Response Code", transactionDetailsUI.processorResponseCode)
            }
            if (transactionDetailsUI.processorResponseMessage.isNotBlank()) {
                HorizontalDivider(thickness = .4.dp)
                LineItem("Response", transactionDetailsUI.processorResponseMessage)
            }
            if (transactionDetailsUI.gatewayResponseMessage.isNotBlank()) {
                HorizontalDivider(thickness = .4.dp)
                LineItem("Gateway Response", transactionDetailsUI.gatewayResponseMessage)
            }
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.total_amount), transactionDetailsUI.totalFormatted)
            if (transactionDetailsUI.refunded > 0) {
                HorizontalDivider(thickness = .4.dp)
                LineItem(
                    label = stringResource(R.string.refunded_label),
                    itemValue = transactionDetailsUI.refundedFormatted,
                    valueColor = Color(0xFFF6693E)
                )
            }
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.subtotal), transactionDetailsUI.subtotalFormatted)
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.tax_amount), transactionDetailsUI.taxFormatted)
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.tip_amount), transactionDetailsUI.tipFormatted)
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.surcharge_amount), transactionDetailsUI.surchargeFormatted)
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.date), transactionDetailsUI.dateFormatted)
            HorizontalDivider(thickness = .4.dp)
            LineItem(stringResource(R.string.card), transactionDetailsUI.card)

            if (transactionDetailsUI.reversed > 0) {
                HorizontalDivider(thickness = .4.dp)
                LineItem(
                    label = stringResource(R.string.reversed_label),
                    itemValue = transactionDetailsUI.reversedFormatted,
                    valueColor = Color(0xFFF6693E)
                )
            }

            // Show authorized & remaining amounts for partial approvals
            if (transactionDetailsUI.isPartialApproval) {
                HorizontalDivider(thickness = .4.dp)
                LineItem(stringResource(R.string.authorized_amount), transactionDetailsUI.authorizedFormatted)
                HorizontalDivider(thickness = .4.dp)
                LineItem(stringResource(R.string.remaining_amount), transactionDetailsUI.remainingFormatted)
            }
        }

        // Payment operation buttons for AUTHORIZED and CAPTURED transactions
        if (transactionDetailsUI.status == KoardTransactionStatus.AUTHORIZED ||
            transactionDetailsUI.status == KoardTransactionStatus.CAPTURED) {
            Text(
                text = "Payment Operations",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KoardOutlinedButton(
                        text = "Incremental Auth",
                        onClick = { onDispatch(TransactionDetailsIntent.OnIncrementalAuthClick) },
                        modifier = Modifier.weight(1f)
                    )
                    KoardOutlinedButton(
                        text = "Capture",
                        onClick = { onDispatch(TransactionDetailsIntent.OnCaptureClick) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KoardOutlinedButton(
                        text = "Reverse",
                        onClick = { onDispatch(TransactionDetailsIntent.OnReverseClick) },
                        modifier = Modifier.weight(1f)
                    )
                    KoardOutlinedButton(
                        text = "Adjust Tip",
                        onClick = { onDispatch(TransactionDetailsIntent.OnAdjustTipClick) },
                        modifier = Modifier.weight(1f)
                    )
                }
                KoardOutlinedButton(
                    text = "Refund",
                    onClick = { onDispatch(TransactionDetailsIntent.OnRefundClick) }
                )
                KoardOutlinedButton(
                    text = "Refund (EMV)",
                    onClick = { onDispatch(TransactionDetailsIntent.OnEmvRefundClick) }
                )
                if (transactionDetailsUI.statusReason == PARTIAL_APPROVAL_STATUS) {
                    KoardOutlinedButton(
                        text = stringResource(R.string.complete_partial_auth),
                        onClick = { onDispatch(TransactionDetailsIntent.OnCompletePartialAuthClick) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KoardOutlinedButton(
    text: String,
    leftIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = KoardGreen800
        ),
        border = BorderStroke(1.dp, KoardGreen800),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftIcon?.invoke()
            Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun LineItem(label: String, itemValue: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

        Text(
            itemValue,
            fontSize = 15.sp,
            color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReceiptInputField(
    inputType: ReceiptInputType,
    isSendEnabled: Boolean = true,
    isSendingReceipt: Boolean = false,
    onDispatch: (TransactionDetailsIntent) -> Unit
) {
    var inputValue by remember { mutableStateOf("") }
    val context = LocalContext.current

    val leadingIcon: @Composable (() -> Unit)? = if (inputType.iconVector != null) {
        {
            Icon(
                imageVector = inputType.iconVector,
                contentDescription = null,
                tint = KoardGreen800
            )
        }
    } else if (inputType.iconResId != null) {
        {
            Icon(
                painter = painterResource(inputType.iconResId),
                contentDescription = null,
                tint = KoardGreen800
            )
        }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = inputValue,
            onValueChange = {
                inputValue = it
                when (inputType) {
                    ReceiptInputType.EMAIL -> onDispatch(
                        TransactionDetailsIntent.OnEmailInputChanged(
                            it
                        )
                    )

                    ReceiptInputType.SMS -> onDispatch(TransactionDetailsIntent.OnSmsInputChanged(it))
                    ReceiptInputType.NONE -> {
                        /* No action */
                    }
                }
            },
            label = { Text(inputType.getLabel(context)) },
            placeholder = { Text(inputType.getPlaceholder(context)) },
            leadingIcon = leadingIcon,
            trailingIcon = {
                if (isSendingReceipt) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp),
                        color = KoardGreen800,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = { onDispatch(TransactionDetailsIntent.OnSendReceipt) },
                        enabled = isSendEnabled
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = if (isSendEnabled) KoardGreen800 else KoardGreen800.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = inputType.keyboardType),
            visualTransformation = if (inputType == ReceiptInputType.SMS) PhoneNumberVisualTransformation else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        TextButton(
            onClick = { onDispatch(TransactionDetailsIntent.OnCancelReceiptInput) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.cancel),
                color = KoardGreen800,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransactionDetailsLayoutPreview() {
    val sampleTransaction = TransactionDetailsUI(
        id = "TXN-001-SAMPLE",
        amount = 2750,
        status = KoardTransactionStatus.CAPTURED,
        merchant = "Coffee Shop Downtown",
        card = "**** **** **** 1234",
        cardBrand = "Visa",
        cardType = "Credit",
        date = System.currentTimeMillis(),
        currency = "USD",
        subtotal = 2500,
        taxAmount = 200,
        taxRate = 8.0,
        tipAmount = 50,
        surchargeAmount = 0.0,
        gateway = "Square",
        processor = "Chase Paymentech",
        processorResponseCode = "00",
        processorResponseMessage = "Approved",
        statusReason = "Transaction completed successfully",
        paymentMethod = "Apple Pay",
        deviceId = "DEVICE-123",
        locationId = "LOC-456",
        refunded = 0,
        reversed = 0,
        approvalCode = "123456",
        gatewayResponseMessage = "Transaction approved",
        transactionType = "sale",
        simplifiedStatus = "Approved"
    )

    KoardAndroidSDKTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            TransactionDetailsLayout(
                transactionDetailsUI = sampleTransaction,
                receiptInputType = ReceiptInputType.NONE,
                isSendEnabled = false,
                isSmsSendEnabled = false,
                isSendingReceipt = false,
                onDispatch = {}
            )
        }
    }
}
