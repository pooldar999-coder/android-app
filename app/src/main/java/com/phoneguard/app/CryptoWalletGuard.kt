package com.phoneguard.app

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
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
 * Local wallet-safety review. This does not inspect wallet secrets, transactions,
 * clipboard contents, or private app data, and it cannot block a malicious transfer.
 */
object CryptoWalletGuard {
    data class Finding(val title: String, val detail: String, val risk: Int)

    fun inspect(context: Context): List<Finding> {
        val pm = context.packageManager
        val findings = mutableListOf<Finding>()

        val enabledA11y = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        if (enabledA11y.isNotBlank()) {
            findings += Finding(
                "سرویس دسترس‌پذیری فعال است",
                "سرویس‌های فعال: ${enabledA11y.replace(':', '\n')}\nاگر هرکدام را نمی‌شناسی، از تنظیمات اندروید غیرفعالش کن. دسترس‌پذیری می‌تواند محتوای صفحه را مشاهده یا با آن تعامل کند.",
                2
            )
        } else {
            findings += Finding("دسترس‌پذیری ویژه", "سرویس دسترس‌پذیری شخص ثالثی در فهرست فعال دیده نشد.", 0)
        }

        val overlayApps = mutableListOf<String>()
        val apps = runCatching { pm.getInstalledApplications(PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        for (app in apps) {
            if (app.packageName == context.packageName) continue
            val mode = runCatching {
                if (Build.VERSION.SDK_INT >= 29) appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.uid, app.packageName
                ) else @Suppress("DEPRECATION") appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.uid, app.packageName
                )
            }.getOrDefault(AppOpsManager.MODE_IGNORED)
            if (mode == AppOpsManager.MODE_ALLOWED) {
                val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
                overlayApps += "$label (${app.packageName})"
            }
        }
        findings += if (overlayApps.isEmpty()) {
            Finding("اجازه نمایش روی برنامه‌ها", "برنامه‌ای با این اجازه در بررسی قابل مشاهده پیدا نشد.", 0)
        } else {
            Finding("برنامه‌های دارای اجازه نمایش روی برنامه‌ها", overlayApps.take(30).joinToString("\n") +
                if (overlayApps.size > 30) "\nو ${overlayApps.size - 30} مورد دیگر" else "" +
                "\nاین اجازه می‌تواند برای فریب صفحه‌ای استفاده شود؛ وجود آن به‌تنهایی نشانه بدافزار نیست.", 1)
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val vpnActive = runCatching {
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true
        }.getOrDefault(false)
        findings += Finding(
            if (vpnActive) "VPN فعال است" else "VPN فعال تشخیص داده نشد",
            if (vpnActive) "اگر VPN را خودت روشن نکرده‌ای، تنظیمات VPN را بررسی کن. VPN به‌تنهایی به معنی سرقت کیف پول نیست."
            else "وضعیت اتصال فعلی VPN را نشان نمی‌دهد؛ VPN ممکن است در شرایط دیگر فعال شود.", if (vpnActive) 1 else 0
        )

        val walletTerms = listOf("trust wallet", "metamask", "coinbase wallet", "rabby", "safePal", "tokenpocket", "exodus", "okx wallet")
        val wallets = apps.filter { app ->
            val label = runCatching { pm.getApplicationLabel(app).toString().lowercase() }.getOrDefault("")
            walletTerms.any { label.contains(it) || app.packageName.lowercase().contains(it.replace(" ", "")) }
        }.map { app -> runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName) }
        findings += Finding(
            "برنامه‌های کیف پول شناسایی‌شده",
            if (wallets.isEmpty()) "کیف پول شناخته‌شده‌ای از نام برنامه‌ها شناسایی نشد؛ ممکن است کیف پول نصب نباشد یا نام آن متفاوت باشد."
            else wallets.distinct().joinToString("، "),
            0
        )

        findings += Finding(
            "عبارت بازیابی و کلید خصوصی",
            "PhoneGuard هرگز عبارت بازیابی یا کلید خصوصی را درخواست نمی‌کند. آن را در هیچ سایت، پیام‌رسان، فرم پشتیبانی یا برنامه اسکنر وارد نکن. فقط از داخل خود کیف پول و برای بازیابی واقعی استفاده کن.",
            0
        )
        findings += Finding(
            "محدودیت مهم",
            "این بررسی محلی است و نمی‌تواند تراکنش را متوقف کند، مجوز قرارداد هوشمند را لغو کند، محتوای خصوصی کیف پول را بخواند یا سرقت را با قطعیت تشخیص دهد. برای لغو دسترسی‌های توکن باید از ابزار رسمی همان شبکه و کیف پول استفاده شود.",
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
            text = "بررسی محلی چند نشانه خطر؛ بدون دسترسی به عبارت بازیابی یا کلید خصوصی."
            textSize = 14f
            setTextColor(muted)
        })
        val findings = inspect(activity)
        findings.forEach { finding ->
            val color = when (finding.risk) { 2 -> Color.rgb(190, 45, 45); 1 -> Color.rgb(175, 112, 20); else -> Color.rgb(30, 125, 80) }
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.WHITE); cornerRadius = dp(activity, 14).toFloat()
                }
            }
            card.addView(TextView(activity).apply { text = finding.title; textSize = 16f; setTextColor(color) })
            card.addView(TextView(activity).apply { text = finding.detail; textSize = 13f; setTextColor(ink); setPadding(0, dp(activity, 6), 0, 0) })
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 10) })
        }
        root.addView(TextView(activity).apply {
            text = "اقدام ضروری: عبارت بازیابی را آفلاین و روی کاغذ نگه‌دار؛ هرگز عکس یا نسخه ابری از آن نساز. اگر احتمال افشای آن وجود دارد، از یک دستگاه پاک، کیف پول جدید با عبارت جدید بساز و دارایی را منتقل کن."
            textSize = 14f
            setTextColor(Color.rgb(150, 45, 35))
            setPadding(0, dp(activity, 16), 0, dp(activity, 18))
        })
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
