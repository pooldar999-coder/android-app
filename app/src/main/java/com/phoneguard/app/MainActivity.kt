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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
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
        root.addView(Button(this).apply {
            text = "☰  منوی برنامه"
            isAllCaps = false
            textSize = 14f
            setTextColor(blue)
            background = round(Color.rgb(232, 240, 253), 14)
            setOnClickListener { showAppMenu() }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })
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
        root.addView(Button(this).apply {
            text = "خروج کامل از برنامه"
            isAllCaps = false
            textSize = 15f
            setTextColor(red)
            background = round(Color.rgb(255, 238, 236), 14)
            setOnClickListener { confirmExit() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
    }


    private fun showAppMenu() {
        val items = arrayOf("صفحه اصلی", "راهنمای استفاده", "درباره PhoneGuard", "تنظیمات برنامه", "خروج کامل")
        android.app.AlertDialog.Builder(this)
            .setTitle("منوی PhoneGuard")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showHome()
                    1 -> android.app.AlertDialog.Builder(this)
                        .setTitle("راهنمای استفاده")
                        .setMessage("برای بررسی گوشی، «شروع اسکن امنیتی» را بزنید. نتیجه‌ها فقط شاخص‌های قابل مشاهده در اندروید هستند و هشدارها به‌تنهایی اثبات بدافزار نیستند. برای خروج کامل، از منو گزینه «خروج کامل» را انتخاب کنید.")
                        .setPositiveButton("متوجه شدم", null)
                        .show()
                    2 -> android.app.AlertDialog.Builder(this)
                        .setTitle("درباره PhoneGuard")
                        .setMessage("PhoneGuard\nابزار بررسی امنیت و حریم خصوصی اندروید\n\nسازنده: mehdiroomiany@gmail.com\nنسخه: 1.0")
                        .setPositiveButton("بستن", null)
                        .setNeutralButton("تماس با سازنده") { _, _ ->
                            runCatching { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:mehdiroomiany@gmail.com"))) }
                        }
                        .show()
                    3 -> runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                    4 -> confirmExit()
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun confirmExit() {
        android.app.AlertDialog.Builder(this)
            .setTitle("خروج از PhoneGuard")
            .setMessage("برنامه بسته می‌شود و سرویس پس‌زمینه‌ای برای ادامه فعالیت ندارد.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("خروج") { _, _ ->
                finishAffinity()
                if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask()
            }
            .show()
    }

    private fun runScan() {
        root.removeAllViews()
        addText("اسکن امنیتی", 25f, ink, true)
        addText("در حال بررسی بخش‌های امنیتی گوشی…", 14f, muted)
        val stage = text("آماده‌سازی اسکن", 15f, blue, true).apply { gravity = Gravity.CENTER }
        root.addView(stage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val meter = ScanRadarView(this)
        root.addView(meter, LinearLayout.LayoutParams(-1, dp(230)).apply { topMargin = dp(10) })
        val percent = text("۰٪", 20f, ink, true).apply { gravity = Gravity.CENTER }
        root.addView(percent, LinearLayout.LayoutParams(-1, -2))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(225, 233, 245))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(14); bottomMargin = dp(12) })
        val notes = listOf("بررسی اتصال‌ها و VPN…", "بررسی دسترسی‌های ویژه…", "بررسی مجوزهای حساس…", "بررسی برنامه‌های نصب‌شده…")
        val animator = ValueAnimator.ofInt(0, 100).apply {
            duration = 4200
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val value = a.animatedValue as Int
                meter.progress = value
                bar.progress = value
                percent.text = "$value٪"
                stage.text = notes[(value / 26).coerceIn(0, notes.lastIndex)]
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { showResults() }
            })
        }
        animator.start()
    }

    private inner class ScanRadarView(context: Context) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = Color.rgb(205, 218, 239) }
        private val sweep = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(65, 35, 105, 220) }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND; color = blue }
        private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = blue }
        var progress: Int = 0
            set(value) { field = value; invalidate() }
        private var angle = 0f
        private val rotation = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { angle = it.animatedValue as Float; invalidate() }
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); rotation.start() }
        override fun onDetachedFromWindow() { rotation.cancel(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.34f
            for (scale in listOf(1f, 0.68f, 0.36f)) canvas.drawCircle(cx, cy, radius * scale, ring)
            canvas.save()
            canvas.rotate(angle, cx, cy)
            canvas.drawArc(RectF(cx-radius, cy-radius, cx+radius, cy+radius), -38f, 38f, true, sweep)
            canvas.drawLine(cx, cy, cx, cy-radius, line)
            canvas.restore()
            canvas.drawCircle(cx, cy, dp(6).toFloat(), center)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = dp(13).toFloat(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            canvas.drawText("PHONEGUARD", cx, cy + radius + dp(28), label)
        }
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
        val privacyApps = privacyPermissionApps()
        val reviewApps = auditInstalledApps()
        // Count all findings that require user review in the summary, including app-audit flags.
        // These are review indicators, not proof that an app is malicious.
        val warnings = highRisk.size + (if (adb) 1 else 0) + reviewApps.size

        addText("گزارش اسکن", 13f, blue, true)
        addText("نتیجه بررسی امنیتی", 26f, ink, true)
        val summary = card()
        summary.setBackground(round(if (warnings > 0) Color.rgb(255, 241, 238) else Color.rgb(232, 247, 239), 18))
        summary.addView(text(if (warnings > 0) "نیاز به بررسی" else "مورد پرخطر مشخصی پیدا نشد", 20f, if (warnings > 0) red else green, true))
        summary.addView(text(if (warnings > 0) "$warnings مورد نیازمند بازبینی پیدا شد؛ این موارد به‌تنهایی اثبات بدافزار نیستند." else "در بررسی‌های انجام‌شده هشدار مهمی دیده نشد.", 14f, ink))
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
        addResultCard(if (vpn) "i" else "✓", "VPN", if (vpn) "یک اتصال VPN فعال است. اندروید نام برنامهٔ سازندهٔ تونل را از این API در اختیار این برنامه نمی‌گذارد؛ فعال بودن VPN به‌تنهایی خطر نیست." else "اتصال VPN فعالی شناسایی نشد.", if (vpn) amber else green)
        if (vpn) {
            root.addView(Button(this).apply {
                text = "باز کردن تنظیمات VPN"
                isAllCaps = false
                setTextColor(blue)
                background = round(Color.rgb(232, 240, 253), 13)
                setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(8) })
        }
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
        val groups = listOf(
            "نسخه‌های قابل اشکال‌زدایی" to apps.filter { app -> app.flags.any { it.contains("اشکال‌زدایی") } },
            "برنامه‌های با منبع نصب نامشخص" to apps.filter { app -> app.flags.any { it.contains("منبع نصب") } },
            "برنامه‌های دارای چند مجوز حساس" to apps.filter { app -> app.flags.any { it.contains("مجوز حساس") } }
        )
        groups.forEach { (heading, entries) ->
            if (entries.isNotEmpty()) {
                addText("$heading (${entries.size})", 16f, ink, true, 14)
                entries.forEach { app ->
                    val c = card()
                    c.addView(text(app.name, 16f, ink, true))
                    c.addView(text(app.pkg, 11f, muted))
                    c.addView(text("نشانه‌ها: ${app.flags.joinToString("، ")}", 13f, amber))
                    c.addView(text("منبع نصب: ${app.installer}\\nاثر انگشت گواهی SHA-256:\\n${app.hash}", 11f, muted))
                    c.addView(Button(this).apply {
                        text = "مشاهده اطلاعات برنامه / حذف"
                        isAllCaps = false
                        setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))) } }
                    })
                    root.addView(c)
                }
            }
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
