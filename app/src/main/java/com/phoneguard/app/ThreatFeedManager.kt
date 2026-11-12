package com.phoneguard.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ThreatFeedInfo(val version: Int, val entries: Int, val updatedAt: Long)
data class ThreatIndicator(val sha256: String, val category: String, val family: String, val source: String, val sourceUrl: String, val confidence: String)

class ThreatFeedManager(private val context: Context) {
    companion object {
        private const val FEED_URL = "https://raw.githubusercontent.com/pooldar999-coder/android-app/main/threat-feed.json"
        private const val MAX_BYTES = 2 * 1024 * 1024
    }

    fun loadIndicators(): Map<String, ThreatIndicator> {\n        val file = File(context.filesDir, "threat-feed.json")\n        if (!file.exists()) return emptyMap()\n        val entries = JSONObject(file.readText(Charsets.UTF_8)).getJSONArray("entries")\n        val result = mutableMapOf<String, ThreatIndicator>()\n        for (i in 0 until entries.length()) {\n            val item = entries.getJSONObject(i)\n            val hash = item.getString("sha256").lowercase()\n            result[hash] = ThreatIndicator(hash, item.getString("category"), item.getString("family"), item.optString("source", "نامشخص"), item.getString("source_url"), item.optString("confidence", "نامشخص"))\n        }\n        return result\n    }\n\n    fun update(): ThreatFeedInfo {
        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "سرور پاسخ موفق نداد: HTTP " + connection.responseCode }
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
            val raw = bytes.toString(Charsets.UTF_8)
            val json = JSONObject(raw)
            require(json.optInt("schema_version", -1) == 1) { "نسخه قالب بانک پشتیبانی نمی‌شود" }
            val version = json.getInt("version")
            val updatedAt = json.getLong("updated_at")
            require(version > 0 && updatedAt > 0) { "اطلاعات نسخه بانک نامعتبر است" }
            val entries = json.getJSONArray("entries")
            require(entries.length() <= 50000) { "تعداد رکوردها بیش از حد مجاز است" }
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val hash = item.getString("sha256")
                require(hash.matches(Regex("(?i)^[a-f0-9]{64}$"))) { "هش نامعتبر در رکورد " + i }
                require(item.getString("category") in setOf("trojan", "spyware", "ransomware", "other")) { "دسته‌بندی نامعتبر در رکورد " + i }
                require(item.getString("family").isNotBlank() && item.getString("source_url").startsWith("https://")) { "منبع یا خانواده رکورد ناقص است" }
            }
            val target = File(context.filesDir, "threat-feed.json")
            val temp = File(context.filesDir, "threat-feed.tmp")
            temp.writeBytes(bytes)
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "ذخیره بانک انجام نشد" }
            context.getSharedPreferences("phoneguard", Context.MODE_PRIVATE).edit()
                .putLong("threat_updated", System.currentTimeMillis())
                .putInt("threat_version", version)
                .putInt("threat_entries", entries.length())
                .apply()
            return ThreatFeedInfo(version, entries.length(), System.currentTimeMillis())
        } finally {
            connection.disconnect()
        }
    }
}
