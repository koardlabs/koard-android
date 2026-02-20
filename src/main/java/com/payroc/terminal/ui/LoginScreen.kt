package com.payroc.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.payroc.terminal.ui.components.PayrocLogo
import com.payroc.terminal.ui.components.PayrocLogoVariant
import com.payroc.terminal.ui.theme.PayrocBlue
import com.payroc.terminal.ui.theme.PayrocDarkText
import com.payroc.terminal.ui.theme.PayrocLightGray
import com.payroc.terminal.ui.theme.PayrocMediumGray
import com.payroc.terminal.ui.theme.PayrocRed
import com.payroc.terminal.ui.theme.PayrocWhite

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PayrocWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Payroc Logo
            PayrocLogo(
                variant = PayrocLogoVariant.OnLight,
                iconSize = 56.dp,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Payment Terminal",
                fontSize = 14.sp,
                color = PayrocMediumGray,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(56.dp))

            // Merchant Code field
            OutlinedTextField(
                value = uiState.merchantCode,
                onValueChange = viewModel::onMerchantCodeChange,
                label = { Text("Merchant Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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

            Spacer(modifier = Modifier.height(16.dp))

            // PIN field
            OutlinedTextField(
                value = uiState.merchantPin,
                onValueChange = viewModel::onMerchantPinChange,
                label = { Text("PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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

            // Error message
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.errorMessage!!,
                    color = PayrocRed,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Login button
            Button(
                onClick = { viewModel.login(onLoginSuccess) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !uiState.isLoading && uiState.merchantCode.isNotBlank() && uiState.merchantPin.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PayrocBlue,
                    contentColor = PayrocWhite,
                    disabledContainerColor = PayrocLightGray,
                    disabledContentColor = PayrocMediumGray
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(20.dp)
                            .padding(end = 8.dp),
                        color = PayrocWhite,
                        strokeWidth = 2.dp
                    )
                    Text(
                        "Logging in...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        "Log In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Need help? Contact support",
                fontSize = 13.sp,
                color = PayrocBlue,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
