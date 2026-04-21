package com.koard.android.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

val PhoneNumberVisualTransformation = VisualTransformation { text ->
    val digitsOnly = text.text.replace(Regex("\\D"), "")
    val formatted = buildString {
        for (i in digitsOnly.indices) {
            when (i) {
                0 -> append("(")
                3 -> append(") ")
                6 -> append("-")
            }
            append(digitsOnly[i])
        }
    }

    val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            if (offset <= 0) return 0
            if (offset <= 3) return offset + 1 // account for "("
            if (offset <= 6) return offset + 3 // account for "(" and ") "
            return (offset + 4).coerceAtMost(formatted.length) // account for all formatting
        }

        override fun transformedToOriginal(offset: Int): Int {
            // Calculate original position by counting digits up to the transformed offset
            var transformedPos = 0
            var originalPos = 0

            while (transformedPos < offset && originalPos < digitsOnly.length) {
                // Add formatting characters
                when (originalPos) {
                    0 -> transformedPos += 1 // "("
                    3 -> transformedPos += 2 // ") "
                    6 -> transformedPos += 1 // "-"
                }
                // Check if we've reached the target offset
                if (transformedPos >= offset) break
                // Add the digit
                transformedPos += 1
                originalPos += 1
            }

            return originalPos.coerceAtMost(digitsOnly.length)
        }
    }

    TransformedText(AnnotatedString(formatted), offsetMapping)
}
