package com.phoneguard.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import java.security.MessageDigest
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var scanButton: Button? = null
    private val blue = Color.rgb(35, 105, 220)
    private val ink = Color.rgb(29, 39, 56)
    private val muted = Color.rgb(105, 116, 132)
    private val red = Color.rgb(194, 50, 50)
    private val amber = Color.rgb(190, 119, 20)
    private val green = Color.rgb(27, 133, 83)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 248, 253)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(245, 248, 253)) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)
        showHome()
    }

    private fun showHome() {
        root.removeAllViews()
        addText("PHONEGUARD", 13f, blue, true)
        addText("مرکز امنیت گوشی", 27f, ink, true)
        addText("بررسی دسترسی‌های مهم و تنظیمات امنیتی دستگاه", 14f, muted)
        val hero = card()
        hero.setBackground(round(Color.rgb(24, 65, 133), 22))
        hero.addView(text("بررسی امنیتی گوشی شما", 19f, Color.WHITE, true))
        hero.addView(text("اسکن محلی انجام می‌شود؛ هیچ اطلاعاتی از گوشی ارسال نمی‌شود.", 13f, Color.rgb(225, 235, 250)))
        scanButton = Button(this).apply {
            text = "شروع اسکن امنیتی"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = round(blue, 14)
            setPadding(dp(18), dp(7), dp(18), dp(7))
            setOnClickListener { runScan() }
        }
        hero.addView(scanButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(18) })
        root.addView(hero)
        addText("چه چیزهایی بررسی می‌شوند؟", 18f, ink, true, 18)
        addInfoCard("دسترسی‌های ویژه", "دسترس‌پذیری، خواندن اعلان‌ها و مدیر دستگاه")
        addInfoCard("اتصالات و تنظیمات", "VPN و اشکال‌زدایی USB")
        addInfoCard("حریم خصوصی", "برنامه‌های دارای مجوز دوربین، میکروفون و موقعیت مکانی")
        addText("توجه: این ابزار آنتی‌ویروس حرفه‌ای نیست و نتیجه آن تشخیص قطعی بدافزار نیست.", 12f, muted, false, 16)
    }

    private fun runScan() {
        root.removeAllViews()
        addText("در حال اسکن...", 25f, ink, true)
        addText("در حال بررسی تنظیمات و دسترسی‌های قابل مشاهده در اندروید", 14f, muted)
        val bar = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(24); bottomMargin = dp(24) })
        root.postDelayed({ showResults() }, 850)
    }

    private data class Finding(val title: String, val detail: String)
    private fun showResults() {
        root.removeAllViews()
        val adb = safe { Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } ?: false
        val vpn = safe {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: false
        val accessibility = settingPackages(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val listeners = settingPackages("enabled_notification_listeners")
        val admins = safe {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.map { it.packageName } ?: emptyList()
        } ?: emptyList()
        val highRisk = (accessibility + listeners + admins).distinct().filterNot { isSystemPackage(it) }
        val warnings = highRisk.size + reviewApps.size + if (adb) 1 else 0
        val privacyApps = privacyPermissionApps()
        val reviewApps = auditInstalledApps()

        addText("گزارش اسکن", 13f, blue, true)
        addText("نتیجه بررسی امنیتی", 26f, ink, true)
        val summary = card()
        summary.setBackground(round(if (warnings > 0) Color.rgb(255, 241, 238) else Color.rgb(232, 247, 239), 18))
        summary.addView(text(if (warnings > 0) "نیاز به بررسی" else "مورد پرخطر مشخصی پیدا نشد", 20f, if (warnings > 0) red else green, true))
        summary.addView(text(if (warnings > 0) "$warnings دسترسی یا تنظیم نیازمند توجه پیدا شد." else "در بررسی‌های انجام‌شده هشدار مهمی دیده نشد.", 14f, ink))
        root.addView(summary)

        addText("موارد مهم", 19f, ink, true, 18)
        val findings = mutableListOf<Finding>()
        if (adb) findings.add(Finding("اشکال‌زدایی USB روشن است", "اگر خودتان فعالش نکرده‌اید، از تنظیمات توسعه‌دهنده خاموش کنید."))
        highRisk.forEach { pkg ->
            val kind = when {
                pkg in accessibility -> "دسترسی دسترس‌پذیری"
                pkg in listeners -> "دسترسی خواندن اعلان‌ها"
                else -> "مدیر دستگاه"
            }
            findings.add(Finding("${appLabel(pkg)} — $kind", "این دسترسی حساس فعال است. اگر برنامه را نمی‌شناسید، بررسی‌اش کنید."))
        }
        if (findings.isEmpty()) addResultCard("✓", "دسترسی ویژه مشکوکی پیدا نشد", "دسترس‌پذیری، اعلان‌ها و مدیر دستگاه بررسی شدند.", green)
        else findings.forEach { addResultCard("!", it.title, it.detail, red) }

        addText("وضعیت اتصال", 19f, ink, true, 18)
        addResultCard(if (vpn) "i" else "✓", "VPN", if (vpn) "یک اتصال VPN فعال است؛ اگر خودتان روشن کرده‌اید طبیعی است." else "اتصال VPN فعالی شناسایی نشد.", if (vpn) amber else green)
        addResultCard(if (adb) "!" else "✓", "اشکال‌زدایی USB", if (adb) "روشن است؛ در صورت عدم نیاز خاموشش کنید." else "خاموش است.", if (adb) red else green)

        addText("بررسی برنامه‌های نصب‌شده", 19f, ink, true, 18)
        addResultCard(if (reviewApps.isEmpty()) "✓" else "!", "برنامه‌های نیازمند بازبینی", "${reviewApps.size} برنامه نشانه‌هایی مانند منبع نصب نامشخص یا نسخه قابل اشکال‌زدایی دارند. این موارد به‌تنهایی اثبات بدافزار نیستند.", if (reviewApps.isEmpty()) green else amber)
        root.addView(Button(this).apply { text = "بررسی و مدیریت برنامه‌ها"; isAllCaps = false; setTextColor(blue); background = round(Color.rgb(232, 240, 253), 13); setOnClickListener { showAppAudits(reviewApps) } }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        addText("حریم خصوصی", 19f, ink, true, 18)
        addResultCard("i", "مجوزهای حساس برنامه‌ها", "${privacyApps.size} برنامه مجوز دوربین، میکروفون یا موقعیت مکانی دارند. این موضوع به‌تنهایی نشانه بدافزار نیست.", blue)
        root.addView(Button(this).apply {
            text = "نمایش فهرست برنامه‌ها"
            isAllCaps = false
            setTextColor(blue)
            background = round(Color.rgb(232, 240, 253), 13)
            setOnClickListener { showPrivacyApps(privacyApps) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        addText("یادآوری", 12f, muted, false, 18)
        addText("PhoneGuard فقط موارد قابل مشاهده در اندروید را گزارش می‌کند؛ نبود جاسوس‌افزار را تضمین نمی‌کند.", 12f, muted)
        root.addView(Button(this).apply {
            text = "اسکن دوباره"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = round(blue, 14)
            setOnClickListener { runScan() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
    }

    private data class AppAudit(val name: String, val pkg: String, val installer: String, val hash: String, val flags: List<String>)

    @Suppress("DEPRECATION")
    private fun auditInstalledApps(): List<AppAudit> {
        return safe {
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { info ->
                val app = info.applicationInfo ?: return@mapNotNull null
                if (app.packageName == packageName) return@mapNotNull null

                val flags = mutableListOf<String>()
                if ((app.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    flags.add("نسخه قابل اشکال‌زدایی")
                }

                val installer = if (Build.VERSION.SDK_INT >= 30) {
                    safe { packageManager.getInstallSourceInfo(info.packageName).installingPackageName }
                } else {
                    safe { packageManager.getInstallerPackageName(info.packageName) }
                } ?: "نامشخص"
                if (installer == "نامشخص" && (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    flags.add("منبع نصب ثبت نشده؛ به‌تنهایی نشانه بدافزار نیست")
                }

                // Count only permissions Android reports as granted; requested permissions alone
                // are not evidence that an app can use them.
                val requested = info.requestedPermissions ?: emptyArray()
                val permissionFlags = info.requestedPermissionsFlags ?: intArrayOf()
                val sensitiveGranted = requested.mapIndexedNotNull { index, permission ->
                    val isSensitive = permission in setOf(
                        "android.permission.READ_SMS",
                        "android.permission.RECEIVE_SMS",
                        "android.permission.SEND_SMS",
                        "android.permission.RECORD_AUDIO",
                        "android.permission.CAMERA",
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.READ_CONTACTS"
                    )
                    if (isSensitive && index < permissionFlags.size &&
                        (permissionFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    ) permission else null
                }.distinct()
                if (sensitiveGranted.size >= 3) {
                    flags.add("۳ مجوز حساس یا بیشتر واقعاً اعطا شده")
                }

                if (flags.isEmpty()) null else {
                    val hash = runCatching {
                        val bytes = if (Build.VERSION.SDK_INT >= 28) {
                            packageManager.getPackageInfo(info.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                                .signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                        } else {
                            packageManager.getPackageInfo(info.packageName, PackageManager.GET_SIGNATURES)
                                .signatures?.firstOrNull()?.toByteArray()
                        }
                        bytes?.let {
                            MessageDigest.getInstance("SHA-256").digest(it)
                                .joinToString("") { b -> "%02X".format(b) }
                        } ?: "در دسترس نیست"
                    }.getOrDefault("در دسترس نیست")
                    AppAudit(appLabel(info.packageName), info.packageName, installer, hash, flags)
                }
            }.sortedBy { it.name.lowercase() }
        } ?: emptyList()
    }

    private fun showAppAudits(apps: List<AppAudit>) {
        root.removeAllViews()
        addText("بازبینی برنامه‌ها", 23f, ink, true)
        addText("این فهرست نشانه‌های قابل بررسی را نشان می‌دهد؛ هیچ برنامه‌ای صرفاً بر اساس این نشانه‌ها ویروس اعلام نمی‌شود.", 13f, muted)
        if (apps.isEmpty()) addResultCard("✓", "موردی برای بازبینی پیدا نشد", "برنامه‌ای با شاخص‌های این بررسی شناسایی نشد.", green)
        apps.forEach { app ->
            val c = card()
            c.addView(text(app.name, 16f, ink, true))
            c.addView(text(app.pkg, 11f, muted))
            c.addView(text("نشانه‌ها: ${app.flags.joinToString("، ")}", 13f, amber))
            c.addView(text("منبع نصب: ${app.installer}\nاثر انگشت گواهی SHA-256:\n${app.hash}", 11f, muted))
            c.addView(Button(this).apply {
                text = "مشاهده اطلاعات برنامه / حذف"
                isAllCaps = false
                setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))) } }
            })
            root.addView(c)
        }
        root.addView(Button(this).apply { text = "بازگشت به گزارش"; isAllCaps = false; setOnClickListener { showResults() } })
    }
    private fun showPrivacyApps(apps: List<Triple<String, String, List<String>>>) {
        root.removeAllViews()
        addText("فهرست مجوزهای حریم خصوصی", 22f, ink, true)
        addText("این برنامه‌ها مجوزهایی مثل دوربین، میکروفون یا مکان دارند؛ صرف داشتن مجوز خطر یا آلودگی نیست.", 13f, muted)
        apps.forEach { (name, pkg, permissions) ->
            val c = card()
            c.addView(text(name, 16f, ink, true))
            c.addView(text(permissions.joinToString("، "), 13f, muted))
            root.addView(c)
        }
        root.addView(Button(this).apply { text = "بازگشت به گزارش"; isAllCaps = false; setOnClickListener { showResults() } })
    }

    private fun addInfoCard(title: String, detail: String) {
        val c = card()
        c.addView(text(title, 16f, ink, true))
        c.addView(text(detail, 13f, muted))
        root.addView(c)
    }

    private fun addResultCard(mark: String, title: String, detail: String, color: Int) {
        val c = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dot = TextView(this).apply {
            text = mark
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = round(color, 50)
        }
        row.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)))
        row.addView(text(title, 15f, ink, true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        c.addView(row)
        c.addView(text(detail, 13f, muted))
        root.addView(c)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = round(Color.WHITE, 18)
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun addText(value: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) {
        root.addView(text(value, size, color, bold), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(7) })
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
        }

    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun settingPackages(key: String): List<String> {
        val raw = safe { Settings.Secure.getString(contentResolver, key) } ?: return emptyList()
        return raw.split(':').mapNotNull { part -> part.substringBefore('/').takeIf { it.isNotBlank() } }
    }

    private fun privacyPermissionApps(): List<Triple<String, String, List<String>>> {
        val wanted = setOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
        return safe {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { info ->
                val req = info.requestedPermissions ?: return@mapNotNull null
                val flags = info.requestedPermissionsFlags ?: intArrayOf()
                val granted = req.mapIndexedNotNull { i, p ->
                    if (p in wanted && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) p.substringAfterLast('.') else null
                }.distinct()
                if (granted.isEmpty()) null else Triple(appLabel(info.packageName), info.packageName, granted)
            }.sortedBy { it.first.lowercase() }
        } ?: emptyList()
    }

    private fun appLabel(pkg: String): String = safe {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } ?: pkg

    private fun isSystemPackage(pkg: String): Boolean = safe {
        (packageManager.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    } ?: false

    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (_: Exception) { null }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
