package com.payroc.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payroc.terminal.ui.theme.PayrocBlue
import com.payroc.terminal.ui.theme.PayrocNavy
import com.payroc.terminal.ui.theme.PayrocWhite

enum class PayrocLogoVariant {
    OnLight,   // Blue icon + navy text — for white/light backgrounds
    OnDark,    // White icon + white text — for dark/navy backgrounds
}

/**
 * Payroc brand logo composed of:
 *  - A rounded blue square with a stylized "roc" letterform
 *  - The wordmark "payroc" in bold next to it
 */
@Composable
fun PayrocLogo(
    modifier: Modifier = Modifier,
    variant: PayrocLogoVariant = PayrocLogoVariant.OnLight,
    iconSize: Dp = 40.dp,
    fontSize: TextUnit = 26.sp
) {
    val iconBg = when (variant) {
        PayrocLogoVariant.OnLight -> PayrocBlue
        PayrocLogoVariant.OnDark -> PayrocWhite
    }
    val iconText = when (variant) {
        PayrocLogoVariant.OnLight -> PayrocWhite
        PayrocLogoVariant.OnDark -> PayrocBlue
    }
    val wordmarkColor = when (variant) {
        PayrocLogoVariant.OnLight -> PayrocNavy
        PayrocLogoVariant.OnDark -> PayrocWhite
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "roc",
                color = iconText,
                fontSize = (iconSize.value * 0.38f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Wordmark
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = "payroc",
                color = wordmarkColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

/** Compact icon-only version */
@Composable
fun PayrocIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    variant: PayrocLogoVariant = PayrocLogoVariant.OnLight
) {
    val bg = if (variant == PayrocLogoVariant.OnLight) PayrocBlue else PayrocWhite
    val fg = if (variant == PayrocLogoVariant.OnLight) PayrocWhite else PayrocBlue

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.25f).dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "roc",
            color = fg,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic
        )
    }
}
