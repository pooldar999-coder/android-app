package com.phoneguard.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 40)
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
    }

    private fun render() {
        content.removeAllViews()
        addText("PhoneGuard", 28f, true)
        addText("بررسی امنیت گوشی", 18f, true)
        addText("این برنامه فقط وضعیت‌های قابل مشاهده در اندروید را بررسی می‌کند. نتیجه‌ها نشانه قطعی هک یا سالم بودن گوشی نیستند و هیچ اطلاعاتی از گوشی ارسال نمی‌شود.", 14f)
        content.addView(Button(this).apply {
            text = "شروع بررسی دوباره"
            setOnClickListener { render() }
        })
        addText("وضعیت‌های امنیتی", 20f, true)

        val adb = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)
        statusRow("اشکال‌زدایی USB (ADB)", if (adb) "روشن است؛ اگر خودتان فعال نکرده‌اید خاموشش کنید." else "خاموش است.")

        val vpn = runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
        statusRow("اتصال VPN", if (vpn) "در حال حاضر VPN فعال است؛ نام برنامه را در تنظیمات VPN بررسی کنید." else "VPN فعالی شناسایی نشد.")

        val accessibility = secureSettingList(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        statusRow("سرویس‌های دسترس‌پذیری فعال", if (accessibility.isEmpty()) "موردی ثبت نشده است." else accessibility.joinToString("\n"))

        val listeners = secureSettingList("enabled_notification_listeners")
        statusRow("دسترسی خواندن اعلان‌ها", if (listeners.isEmpty()) "موردی ثبت نشده است." else listeners.joinToString("\n"))

        val admins = runCatching {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.map { it.packageName } ?: emptyList()
        }.getOrDefault(emptyList())
        statusRow("برنامه‌های مدیر دستگاه", if (admins.isEmpty()) "موردی ثبت نشده است." else admins.joinToString("\n"))

        addText("برنامه‌های دارای مجوز حساس", 20f, true)
        addText("این فهرست فقط مجوزهای خطرناکِ اعطا‌شده را نشان می‌دهد؛ داشتن مجوز به‌تنهایی به معنی بدافزار بودن برنامه نیست.", 14f)
        val apps = findAppsWithGrantedDangerousPermissions()
        if (apps.isEmpty()) {
            addText("برنامه‌ای با مجوز حساسِ اعطا‌شده پیدا نشد یا امکان خواندن فهرست وجود نداشت.", 14f)
        } else {
            apps.forEach { (name, pkg, permissions) ->
                addText("${name}\nبسته: ${pkg}\nمجوزها: ${permissions.joinToString(", ")}", 14f)
                addDivider()
            }
        }
        addText("نکته مهم", 18f, true)
        addText("برای بررسی دقیق‌تر، دسترسی‌های ناشناس را از مسیر تنظیمات گوشی بررسی کنید. PhoneGuard نمی‌تواند بدافزارهای پنهان، سرقت حساب‌ها یا دسترسی‌های سوءاستفاده‌شده را با قطعیت تشخیص دهد.", 14f)
    }

    private fun secureSettingList(key: String): List<String> {
        val raw = runCatching { Settings.Secure.getString(contentResolver, key) }.getOrNull()
        return raw?.split(':')?.filter { it.isNotBlank() } ?: emptyList()
    }

    @Suppress("DEPRECATION")
    private fun findAppsWithGrantedDangerousPermissions(): List<Triple<String, String, List<String>>> {
        val dangerous = setOf(
            "android.permission.CAMERA", "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALL_LOG", "android.permission.READ_SMS",
            "android.permission.SEND_SMS", "android.permission.READ_PHONE_STATE",
            "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
        return runCatching {
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .mapNotNull { info ->
                    val requested = info.requestedPermissions ?: return@mapNotNull null
                    val flags = info.requestedPermissionsFlags ?: intArrayOf()
                    val granted = requested.mapIndexedNotNull { index, permission ->
                        if (permission in dangerous && index < flags.size &&
                            (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        ) permission.substringAfterLast('.') else null
                    }.distinct()
                    if (granted.isEmpty()) null else Triple(
                        info.applicationInfo?.loadLabel(packageManager)?.toString() ?: info.packageName,
                        info.packageName, granted
                    )
                }.sortedBy { it.first.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun addText(value: String, size: Float, bold: Boolean = false) {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xFF202124.toInt())
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
        content.addView(view)
    }

    private fun statusRow(title: String, detail: String) {
        addText(title, 16f, true)
        addText(detail, 14f)
        addDivider()
    }

    private fun addDivider() {
        val line = View(this).apply { setBackgroundColor(0xFFE0E0E0.toInt()) }
        content.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }
}
