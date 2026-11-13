package com.phoneguard.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ThreatFeedInfo(val version: Int, val entries: Int, val updatedAt: Long)
data class ThreatIndicator(val sha256: String, val category: String, val family: String, val source: String, val sourceUrl: String, val confidence: String)

class ThreatFeedManager(private val context: Context) {
    companion object {
        private const val FEED_URL = "https://raw.githubusercontent.com/pooldar999-coder/android-app/main/threat-feed.json"
        private const val MAX_BYTES = 2 * 1024 * 1024
    }

    @Synchronized
    fun loadIndicators(): Map<String, ThreatIndicator> {
        // فایل محلی هم دوباره اعتبارسنجی می‌شود؛ خرابی یا دستکاری آن نباید باعث توقف اسکن شود.
        return runCatching {
            val file = File(context.filesDir, "threat-feed.json")
            if (!file.exists() || file.length() > MAX_BYTES) return@runCatching emptyMap()
            val json = JSONObject(file.readText(Charsets.UTF_8))
            require(json.optInt("schema_version", -1) == 1)
            require(json.getInt("version") > 0)
            val updatedAt = json.getLong("updated_at")
            require(updatedAt > 0 && updatedAt <= System.currentTimeMillis() / 1000L + 86400L)
            val prefs = context.getSharedPreferences("phoneguard", Context.MODE_PRIVATE)
            require(json.getInt("version") >= prefs.getInt("threat_version", 0))
            val entries = json.getJSONArray("entries")
            require(entries.length() <= 50000)
            val result = mutableMapOf<String, ThreatIndicator>()
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val hash = item.getString("sha256").lowercase()
                require(hash.matches(Regex("^[a-f0-9]{64}$")))
                require(!result.containsKey(hash))
                val category = item.getString("category")
                require(category in setOf("trojan", "spyware", "ransomware", "other"))
                val family = item.getString("family")
                val source = item.getString("source")
                val sourceUrl = item.getString("source_url")
                val parsedUrl = URL(sourceUrl)
                require(family.isNotBlank() && source.isNotBlank())
                require(parsedUrl.protocol == "https" && parsedUrl.host.isNotBlank())
                require(item.optString("sample_type").equals("APK", ignoreCase = true))
                result[hash] = ThreatIndicator(
                    hash, category, family, source, sourceUrl,
                    item.optString("confidence", "نامشخص")
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun update(): ThreatFeedInfo {
        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            // Redirects are disabled so the trusted feed origin cannot silently change.
            instanceFollowRedirects = false
        }
        try {
            check(connection.url.protocol == "https" && connection.url.host == "raw.githubusercontent.com") {
                "نشانی سرور بانک تهدیدها مورد اعتماد نیست"
            }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "سرور پاسخ موفق نداد: HTTP " + connection.responseCode
            }
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES) { "حجم بانک بیش از حد مجاز است" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.optInt("schema_version", -1) == 1) { "نسخه قالب بانک پشتیبانی نمی‌شود" }
            val version = json.getInt("version")
            val updatedAt = json.getLong("updated_at")
            require(version > 0 && updatedAt > 0 && updatedAt <= System.currentTimeMillis() / 1000L + 86400L) {
                "اطلاعات نسخه یا زمان بانک نامعتبر است"
            }
            val entries = json.getJSONArray("entries")
            require(entries.length() <= 50000) { "تعداد رکوردها بیش از حد مجاز است" }
            val seen = HashSet<String>()
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val hash = item.getString("sha256").lowercase()
                require(hash.matches(Regex("^[a-f0-9]{64}$"))) { "هش نامعتبر در رکورد $i" }
                require(seen.add(hash)) { "هش تکراری در رکورد $i" }
                require(item.getString("category") in setOf("trojan", "spyware", "ransomware", "other")) {
                    "دسته‌بندی نامعتبر در رکورد $i"
                }
                require(item.getString("family").isNotBlank()) { "نام خانواده در رکورد $i خالی است" }
                require(item.getString("source").isNotBlank()) { "منبع رکورد $i خالی است" }
                val sourceUrl = URL(item.getString("source_url"))
                require(sourceUrl.protocol == "https" && sourceUrl.host.isNotBlank()) { "نشانی منبع رکورد $i نامعتبر است" }
                require(item.optString("sample_type").equals("APK", ignoreCase = true)) {
                    "نوع نمونه رکورد $i باید APK باشد"
                }
            }

            val prefs = context.getSharedPreferences("phoneguard", Context.MODE_PRIVATE)
            val oldVersion = prefs.getInt("threat_version", 0)
            require(version >= oldVersion) { "نسخه دریافتی از نسخه نصب‌شده قدیمی‌تر است" }

            val target = File(context.filesDir, "threat-feed.json")
            if (version == oldVersion && oldVersion > 0 && target.exists()) {
                val currentDigest = MessageDigest.getInstance("SHA-256").digest(target.readBytes())
                val incomingDigest = MessageDigest.getInstance("SHA-256").digest(bytes)
                require(currentDigest.contentEquals(incomingDigest)) {
                    "محتوای بانک تغییر کرده اما شماره نسخه افزایش نیافته است"
                }
            }
            val temp = File(context.filesDir, "threat-feed.tmp")
            FileOutputStream(temp).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val now = System.currentTimeMillis()
            prefs.edit().putLong("threat_updated", now)
                .putInt("threat_version", version)
                .putInt("threat_entries", entries.length())
                .apply()
            return ThreatFeedInfo(version, entries.length(), now)
        } finally {
            connection.disconnect()
        }
    }
}
