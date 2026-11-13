package com.phoneguard.app

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class ThreatFeedInfo(val version: Int, val entries: Int, val updatedAt: Long)
data class ThreatIndicator(val sha256: String, val category: String, val family: String, val source: String, val sourceUrl: String, val confidence: String)

class ThreatFeedManager(private val context: Context) {
    companion object {
        private const val FEED_URL = "https://raw.githubusercontent.com/pooldar999-coder/android-app/main/threat-feed.json"
        private const val SIGNATURE_URL = "https://raw.githubusercontent.com/pooldar999-coder/android-app/main/threat-feed.sig"
        private const val MAX_BYTES = 2 * 1024 * 1024
        // Pinned P-256 public key. The corresponding private key is stored only as a GitHub Actions secret.
        private const val PUBLIC_KEY_DER_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEz07EkLG6jC+5CA/wkQXIS0ZbqnD6DxdLfTpgh1TH9PWjvoMgM7VcmNylvzZxoc3FnBrFOg1H5k9n"
    }

    private fun verifySignature(data: ByteArray, signatureBytes: ByteArray) {
        val keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_DER_B64)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(data)
        require(verifier.verify(signatureBytes)) { "امضای دیجیتال بانک معتبر نیست؛ بانک نصب یا به‌روزرسانی نشد" }
    }

    private fun fetch(urlText: String, maxBytes: Int): ByteArray {
        val url = URL(urlText)
        require(url.protocol == "https" && url.host == "raw.githubusercontent.com") {
            "نشانی سرور بانک تهدیدها مورد اعتماد نیست"
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/octet-stream")
            instanceFollowRedirects = false
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "سرور پاسخ موفق نداد: HTTP " + connection.responseCode
            }
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= maxBytes) { "حجم داده بیش از حد مجاز است" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateFeed(bytes: ByteArray): Pair<JSONObject, Int> {
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
            require(item.getString("family").isNotBlank() && item.getString("source").isNotBlank()) {
                "نام خانواده یا منبع رکورد $i خالی است"
            }
            val sourceUrl = URL(item.getString("source_url"))
            require(sourceUrl.protocol == "https" && sourceUrl.host.isNotBlank()) {
                "نشانی منبع رکورد $i نامعتبر است"
            }
            require(item.optString("sample_type").equals("APK", ignoreCase = true)) {
                "نوع نمونه رکورد $i باید APK باشد"
            }
        }
        return json to version
    }

    @Synchronized
    fun loadIndicators(): Map<String, ThreatIndicator> {
        return runCatching {
            val file = File(context.filesDir, "threat-feed.json")
            val signatureFile = File(context.filesDir, "threat-feed.sig")
            if (!file.exists() || !signatureFile.exists() || file.length() > MAX_BYTES) return@runCatching emptyMap()
            val bytes = file.readBytes()
            verifySignature(bytes, Base64.getDecoder().decode(signatureFile.readText(Charsets.UTF_8).trim()))
            val (json, _) = validateFeed(bytes)
            val entries = json.getJSONArray("entries")
            val result = mutableMapOf<String, ThreatIndicator>()
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val hash = item.getString("sha256").lowercase()
                result[hash] = ThreatIndicator(
                    hash, item.getString("category"), item.getString("family"),
                    item.getString("source"), item.getString("source_url"),
                    item.optString("confidence", "نامشخص")
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun update(): ThreatFeedInfo {
        val bytes = fetch(FEED_URL, MAX_BYTES)
        val signatureText = fetch(SIGNATURE_URL, 4096).toString(Charsets.UTF_8).trim()
        val signatureBytes = Base64.getDecoder().decode(signatureText)
        verifySignature(bytes, signatureBytes)
        val (json, version) = validateFeed(bytes)
        val entries = json.getJSONArray("entries")
        val prefs = context.getSharedPreferences("phoneguard", Context.MODE_PRIVATE)
        val oldVersion = prefs.getInt("threat_version", 0)
        require(version >= oldVersion) { "نسخه دریافتی از نسخه نصب‌شده قدیمی‌تر است" }

        val target = File(context.filesDir, "threat-feed.json")
        val signatureTarget = File(context.filesDir, "threat-feed.sig")
        if (version == oldVersion && oldVersion > 0 && target.exists()) {
            require(target.readBytes().contentEquals(bytes)) {
                "محتوای بانک تغییر کرده اما شماره نسخه افزایش نیافته است"
            }
        }

        fun atomicWrite(destination: File, content: ByteArray) {
            val temp = File(context.filesDir, destination.name + ".tmp")
            FileOutputStream(temp).use { stream ->
                stream.write(content)
                stream.fd.sync()
            }
            try {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        atomicWrite(target, bytes)
        atomicWrite(signatureTarget, (signatureText + "\n").toByteArray(Charsets.UTF_8))

        val now = System.currentTimeMillis()
        prefs.edit().putLong("threat_updated", now)
            .putInt("threat_version", version)
            .putInt("threat_entries", entries.length())
            .apply()
        return ThreatFeedInfo(version, entries.length(), now)
    }
}
