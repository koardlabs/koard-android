package com.koard.android.utils

internal fun String.isValidEmail(): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    return this.isNotBlank() && emailRegex.matches(this)
}

internal fun String.isValidUSPhoneNumber(): Boolean {
    // Remove all non-digit characters
    val digitsOnly = this.replace(Regex("[^\\d]"), "")
    // Check for 10 digits (US number) or 11 digits starting with 1 (US with country code)
    return digitsOnly.length == 10 || (digitsOnly.length == 11 && digitsOnly.startsWith("1"))
}
