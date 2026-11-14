package com.phoneguard.app

import android.app.Activity
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Local wallet-safety review.
 * It never reads wallet secrets, clipboard contents, screens, or private app data.
 * Findings are indicators for user review, not malware verdicts.
 */
object CryptoWalletGuard {
    data class Finding(val title: String, val detail: String, val risk: Int)

    private fun label(context: Context, packageName: String): String {
        return runCatching {
            val app = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(app).toString()
        }.getOrDefault(packageName)
    }

    private fun enabledPackages(context: Context, setting: String): List<String> {
        val raw = Settings.Secure.getString(context.contentResolver, setting).orEmpty()
        return raw.split(':').mapNotNull { component ->
            component.trim().takeIf { it.isNotEmpty() }?.substringBefore('/')
        }.distinct()
    }

    @Suppress("DEPRECATION")
    private fun opAllowed(appOps: AppOpsManager, op: String, app: ApplicationInfo): Boolean {
        return runCatching {
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(op, app.uid, app.packageName)
            } else {
                appOps.checkOpNoThrow(op, app.uid, app.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun inspect(context: Context): List<Finding> {
        val pm = context.packageManager
        val findings = mutableListOf<Finding>()
        val apps = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val accessibility = enabledPackages(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .filterNot { it == context.packageName }
        findings += if (accessibility.isEmpty()) {
            Finding("دسترس‌پذیری", "سرویس شخص ثالث فعالی در فهرست تنظیمات دیده نشد.", 0)
        } else {
            Finding(
                "سرویس‌های دسترس‌پذیری فعال",
                accessibility.joinToString("\n") { "${label(context, it)} ($it)" } +
                    "\nاین دسترسی می‌تواند محتوای صفحه را بخواند یا با آن تعامل کند. اگر سرویس را خودت فعال کرده‌ای و به آن اعتماد داری، لزوماً خطر نیست.",
                1
            )
        }

        val listeners = enabledPackages(context, "enabled_notification_listeners")
            .filterNot { it == context.packageName }
        findings += if (listeners.isEmpty()) {
            Finding("دسترسی خواندن اعلان‌ها", "شنونده اعلان شخص ثالثی در تنظیمات فعال دیده نشد.", 0)
        } else {
            Finding(
                "برنامه‌های دارای دسترسی اعلان",
                listeners.joinToString("\n") { "${label(context, it)} ($it)" } +
                    "\nاین برنامه‌ها ممکن است محتوای اعلان‌ها، از جمله بعضی کدهای ورود، را ببینند. موارد ناشناس را بررسی کن.",
                1
            )
        }

        val overlayApps = apps.filter {
            it.packageName != context.packageName &&
                !it.flags.hasFlag(ApplicationInfo.FLAG_SYSTEM) &&
                opAllowed(appOps, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, it)
        }
        findings += if (overlayApps.isEmpty()) {
            Finding("نمایش روی برنامه‌های دیگر", "برنامه غیرسیستمی با این دسترسی در فهرست قابل مشاهده پیدا نشد.", 0)
        } else {
            Finding(
                "برنامه‌های دارای اجازه نمایش روی برنامه‌ها",
                overlayApps.take(25).joinToString("\n") { "${pm.getApplicationLabel(it)} (${it.packageName})" } +
                    (if (overlayApps.size > 25) "\nو ${overlayApps.size - 25} برنامه دیگر" else "") +
                    "\nاین اجازه می‌تواند برای پوشاندن صفحه یا فریب کاربر سوءاستفاده شود؛ وجود آن به‌تنهایی نشانه بدافزار نیست.",
                1
            )
        }

        val installSources = apps.filter {
            it.packageName != context.packageName &&
                !it.flags.hasFlag(ApplicationInfo.FLAG_SYSTEM) &&
                opAllowed(appOps, "android:request_install_packages", it)
        }
        findings += if (installSources.isEmpty()) {
            Finding("اجازه نصب برنامه از منابع دیگر", "برنامه غیرسیستمی با اجازه نصب بسته در بررسی قابل مشاهده پیدا نشد.", 0)
        } else {
            Finding(
                "برنامه‌های مجاز به نصب APK",
                installSources.take(25).joinToString("\n") { "${pm.getApplicationLabel(it)} (${it.packageName})" } +
                    (if (installSources.size > 25) "\nو ${installSources.size - 25} برنامه دیگر" else "") +
                    "\nاگر برنامه‌ای را نمی‌شناسی، اجازه نصب آن را از تنظیماتش لغو کن. داشتن این اجازه به‌تنهایی اثبات آلودگی نیست.",
                1
            )
        }

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secureLock = runCatching { keyguard.isDeviceSecure }.getOrDefault(false)
        findings += Finding(
            if (secureLock) "قفل امن صفحه" else "قفل امن صفحه فعال نیست",
            if (secureLock) "برای دستگاه PIN، گذرواژه یا الگوی امن تنظیم شده است."
            else "برای محافظت از برنامه‌ها و اطلاعات، یک PIN یا گذرواژه قوی برای صفحه تنظیم کن.",
            if (secureLock) 0 else 1
        )

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val vpnActive = runCatching {
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true
        }.getOrDefault(false)
        findings += Finding(
            if (vpnActive) "VPN فعال است" else "VPN فعال تشخیص داده نشد",
            if (vpnActive) "اتصال VPN فعال است. نام برنامه VPN از این بررسی قابل تعیین قطعی نیست؛ VPN به‌تنهایی نشانه سرقت نیست."
            else "در لحظه بررسی اتصال VPN شناسایی نشد.",
            if (vpnActive) 1 else 0
        )

        val walletTerms = listOf("trust wallet", "metamask", "coinbase wallet", "rabby", "safepal", "tokenpocket", "exodus", "okx wallet")
        val wallets = apps.filter { app ->
            val appLabel = runCatching { pm.getApplicationLabel(app).toString().lowercase() }.getOrDefault("")
            walletTerms.any { appLabel.contains(it) || app.packageName.lowercase().contains(it.replace(" ", "")) }
        }
        findings += Finding(
            "کیف پول‌های شناسایی‌شده",
            if (wallets.isEmpty()) "کیف پول شناخته‌شده‌ای از نام برنامه‌ها شناسایی نشد."
            else wallets.distinctBy { it.packageName }.joinToString("\n") { "${pm.getApplicationLabel(it)} (${it.packageName})" },
            0
        )

        findings += Finding(
            "عبارت بازیابی و کلید خصوصی",
            "PhoneGuard هرگز عبارت بازیابی یا کلید خصوصی را درخواست نمی‌کند. آن را در سایت، پیام‌رسان، فرم پشتیبانی یا برنامه اسکنر وارد نکن. این برنامه نمی‌تواند سرقت را متوقف یا تراکنش را مسدود کند.",
            0
        )
        findings += Finding(
            "محدودیت بررسی",
            "اندروید بدون روت اجازه بازرسی فایل‌های خصوصی Trust Wallet، خواندن عبارت بازیابی، دیدن محتوای صفحه برنامه‌های دیگر یا تشخیص قطعی همه بدافزارها را نمی‌دهد. نبود هشدار به معنی امن بودن قطعی گوشی نیست.",
            1
        )
        return findings
    }

    fun show(activity: Activity, root: LinearLayout, home: () -> Unit) {
        root.removeAllViews()
        val blue = Color.rgb(35, 105, 220)
        val ink = Color.rgb(29, 39, 56)
        val muted = Color.rgb(105, 116, 132)
        root.addView(Button(activity).apply {
            text = "⌂  بازگشت به صفحه اصلی"
            isAllCaps = false
            setTextColor(blue)
            setOnClickListener { home() }
        }, LinearLayout.LayoutParams(-1, dp(activity, 46)))
        root.addView(TextView(activity).apply {
            text = "محافظ کیف پول رمزارز"
            textSize = 24f
            setTextColor(ink)
            setPadding(0, dp(activity, 18), 0, dp(activity, 8))
        })
        root.addView(TextView(activity).apply {
            text = "بررسی محلی دسترسی‌های حساس و تنظیمات امنیتی؛ بدون دسترسی به اطلاعات محرمانه کیف پول."
            textSize = 14f
            setTextColor(muted)
        })
        val findings = inspect(activity)
        findings.forEach { finding ->
            val color = when (finding.risk) {
                2 -> Color.rgb(190, 45, 45)
                1 -> Color.rgb(175, 112, 20)
                else -> Color.rgb(30, 125, 80)
            }
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(activity, 14).toFloat()
                }
            }
            card.addView(TextView(activity).apply { text = finding.title; textSize = 16f; setTextColor(color) })
            card.addView(TextView(activity).apply { text = finding.detail; textSize = 13f; setTextColor(ink); setPadding(0, dp(activity, 6), 0, 0) })
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 10) })
        }
        root.addView(TextView(activity).apply {
            text = "دسترسی‌های مهم را از تنظیمات اندروید بررسی کن:"
            textSize = 14f
            setTextColor(ink)
            setPadding(0, dp(activity, 16), 0, dp(activity, 4))
        })
        val actions = listOf(
            "بررسی دسترس‌پذیری" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "بررسی دسترسی نمایش روی برنامه‌ها" to Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "بررسی برنامه‌های مدیر دستگاه" to Settings.ACTION_SECURITY_SETTINGS
        )
        actions.forEach { (title, action) ->
            root.addView(Button(activity).apply {
                text = title
                isAllCaps = false
                setOnClickListener {
                    runCatching { activity.startActivity(Intent(action)) }
                }
            }, LinearLayout.LayoutParams(-1, dp(activity, 46)).apply { topMargin = dp(activity, 6) })
        }
        root.addView(TextView(activity).apply {
            text = "اقدام ضروری: عبارت بازیابی را آفلاین و روی کاغذ نگه‌دار. اگر احتمال افشای آن وجود دارد، از یک دستگاه پاک کیف پول تازه‌ای با عبارت جدید بساز و دارایی را منتقل کن."
            textSize = 14f
            setTextColor(Color.rgb(150, 45, 35))
            setPadding(0, dp(activity, 16), 0, dp(activity, 18))
        })
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun Int.hasFlag(flag: Int): Boolean = (this and flag) != 0
}
