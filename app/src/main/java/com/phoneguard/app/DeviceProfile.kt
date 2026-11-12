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
        val buildText = listOf(Build.DISPLAY, Build.VERSION.INCREMENTAL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val isXiaomiFamily = listOf(manufacturer, brand).any {
            it.contains("xiaomi", ignoreCase = true) ||
                it.contains("poco", ignoreCase = true) ||
                it.contains("redmi", ignoreCase = true)
        }
        val hyperOsVersion = Regex("OS\\d+(?:\\.\\d+)+", RegexOption.IGNORE_CASE)
            .find(buildText)?.value
        val interfaceName = when {
            !isXiaomiFamily -> "رابط کاربری: غیرشیائومی / نامشخص"
            hyperOsVersion != null -> "رابط کاربری: HyperOS ($hyperOsVersion؛ تشخیص از اطلاعات ساخت)"
            buildText.contains("hyperos", ignoreCase = true) ->
                "رابط کاربری: HyperOS (نسخه از اطلاعات ساخت قابل تشخیص نبود)"
            buildText.contains("miui", ignoreCase = true) ->
                "رابط کاربری: MIUI (نسخه از اطلاعات ساخت قابل تشخیص نبود)"
            else -> "رابط کاربری: شیائومی؛ نسخه MIUI / HyperOS به‌صورت خودکار قابل تأیید نبود"
        }
        val family = if (isXiaomiFamily) {
            "پروفایل بررسی سازگاری شیائومی / پوکو"
        } else {
            "پروفایل عمومی اندروید"
        }
        return "سازنده: $manufacturer\\nبرند: $brand\\nمدل: $model\\nشناسه دستگاه: $device\\nاندروید: $android (API $sdk)\\n$interfaceName\\n$family\\nبررسی‌ها فقط از قابلیت‌های مجاز و قابل دسترس اندروید استفاده می‌کنند."
    }
}
