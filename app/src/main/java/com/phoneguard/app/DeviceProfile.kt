package com.phoneguard.app

import android.content.Context
import android.os.Build

/**
 * Read-only device profile used to select safe, device-aware diagnostics.
 * This does not change system settings or request elevated privileges.
 */
object DeviceProfile {
    fun summary(context: Context): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "نامشخص" }
        val brand = Build.BRAND.orEmpty().ifBlank { "نامشخص" }
        val model = Build.MODEL.orEmpty().ifBlank { "نامشخص" }
        val device = Build.DEVICE.orEmpty().ifBlank { "نامشخص" }
        val android = Build.VERSION.RELEASE.orEmpty().ifBlank { "نامشخص" }
        val sdk = Build.VERSION.SDK_INT
        val isXiaomiFamily = listOf(manufacturer, brand).any {
            it.contains("xiaomi", ignoreCase = true) ||
                it.contains("poco", ignoreCase = true) ||
                it.contains("redmi", ignoreCase = true)
        }
        val family = if (isXiaomiFamily) {
            "پروفایل سازگاری شیائومی / پوکو"
        } else {
            "پروفایل عمومی اندروید"
        }
        return "سازنده: $manufacturer\nبرند: $brand\nمدل: $model\nشناسه دستگاه: $device\nاندروید: $android (API $sdk)\n$family\nبررسی‌ها بر اساس قابلیت‌های قابل دسترس همین دستگاه انجام می‌شوند."
    }
}
