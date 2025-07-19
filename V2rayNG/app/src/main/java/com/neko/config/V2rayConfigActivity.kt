package com.neko.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.neko.v2ray.R
import com.neko.v2ray.ui.BaseActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class V2rayConfigActivity : BaseActivity() {

    // UI Components
    private lateinit var textConfig: TextView
    private lateinit var textLoading: TextView
    private lateinit var btnGenerate: Button
    private lateinit var btnCopy: ImageView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var textCountry: TextView
    private lateinit var BgtextConfig: LinearLayout
    private lateinit var BgtextCountry: LinearLayout

    companion object {
        private const val TAG = "V2rayConfigActivity"
        private const val IPINFO_BASE_URL = "https://ipinfo.io"
        private const val IPINFO_TOKEN = "your_ipinfo_token_here" // Register at ipinfo.io for a token
        private val PROTOCOL_PREFIXES = listOf(
            "vmess://", "vless://", "trojan://",
            "ss://", "http://", "socks://",
            "wireguard://", "hysteria2://"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_v2ray_config)

        initializeViews()
        setupButtonListeners()
    }

    private fun initializeViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        textConfig = findViewById(R.id.textConfig)
        textLoading = findViewById(R.id.textLoading)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnCopy = findViewById(R.id.btnCopy)
        progressIndicator = findViewById(R.id.progressIndicator)
        textCountry = findViewById(R.id.textCountry)
        BgtextConfig = findViewById(R.id.BgtextConfig)
        BgtextCountry = findViewById(R.id.BgtextCountry)

        BgtextConfig.visibility = View.GONE
        BgtextCountry.visibility = View.GONE
    }

    private fun setupButtonListeners() {
        btnGenerate.setOnClickListener {
            resetUI()
            progressIndicator.show()
            fetchV2rayConfig()
        }

        btnCopy.setOnClickListener {
            copyConfigToClipboard()
        }
    }

    private fun resetUI() {
        textLoading.text = "Loading..."
        textConfig.text = ""
        textCountry.text = ""
        BgtextCountry.visibility = View.GONE
        BgtextConfig.visibility = View.GONE
    }

    private fun copyConfigToClipboard() {
        val text = textConfig.text.toString()
        if (text.isNotBlank()) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).apply {
                setPrimaryClip(ClipData.newPlainText("V2Ray Config", text))
                Toast.makeText(this@V2rayConfigActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No config to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun extractIpFromConfig(config: String): String? {
        extractDomainFromConfig(config)?.let { domain ->
            resolveDomainToIp(domain)?.let { ip ->
                return ip
            }
        }

        return when {
            isEncodedConfig(config) -> extractFromEncodedConfig(config)
            else -> extractFromPlainConfig(config)
        } ?: extractIpFromText(config)
    }

    private fun extractDomainFromConfig(config: String): String? {
        val domainPattern = """(?<=@|host=|hostname=)([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""".toRegex()
        return domainPattern.find(config)?.value
    }

    private suspend fun resolveDomainToIp(domain: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(domain).hostAddress.also {
                    Log.d(TAG, "Resolved domain $domain to IP: $it")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Domain resolution failed", e)
                null
            }
        }
    }

    private fun isEncodedConfig(config: String): Boolean {
        return when {
            config.startsWith("vmess://") -> true
            config.startsWith("vless://") -> true
            config.startsWith("ss://") -> true
            config.startsWith("trojan://") && config.substringAfter("trojan://").contains("@") -> true
            config.startsWith("hysteria2://") -> true
            else -> false
        }
    }

    private fun extractFromEncodedConfig(config: String): String? {
        return try {
            when {
                config.startsWith("vmess://") -> handleVmessConfig(config)
                config.startsWith("vless://") -> handleVlessConfig(config)
                config.startsWith("ss://") -> handleShadowsocksConfig(config)
                config.startsWith("trojan://") -> handleTrojanConfig(config)
                config.startsWith("hysteria2://") -> handleHysteriaConfig(config)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Config decoding failed", e)
            null
        }
    }

    private fun handleVmessConfig(config: String): String? {
        val decoded = decodeBase64Safe(config.removePrefix("vmess://"))
        return try {
            JSONObject(decoded).let { json ->
                listOf("add", "server", "address").firstNotNullOfOrNull { key ->
                    json.optString(key).takeIf { isValidIp(it) }
                }
            }
        } catch (e: Exception) {
            extractIpFromText(decoded)
        }
    }

    private fun handleVlessConfig(config: String): String? {
        val decoded = decodeBase64Safe(config.removePrefix("vless://"))
        return extractIpFromText(decoded)
    }

    private fun handleShadowsocksConfig(config: String): String? {
        val decoded = decodeBase64Safe(config.removePrefix("ss://"))
        return decoded.substringAfter('@').substringBefore(':').takeIf { isValidIp(it) }
    }

    private fun handleTrojanConfig(config: String): String? {
        val decoded = decodeBase64Safe(config.removePrefix("trojan://"))
        return decoded.substringAfter('@').substringBefore(':').takeIf { isValidIp(it) }
    }

    private fun handleHysteriaConfig(config: String): String? {
        val decoded = decodeBase64Safe(config.removePrefix("hysteria2://"))
        return extractIpFromText(decoded)
    }

    private fun decodeBase64Safe(encoded: String): String {
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE))
        } catch (e: Exception) {
            try {
                String(Base64.decode(encoded, Base64.NO_WRAP))
            } catch (e: Exception) {
                encoded
            }
        }
    }

    private fun extractFromPlainConfig(config: String): String? {
        return when {
            config.contains("://") -> extractFromUrl(config)
            config.contains("Endpoint = ") -> extractWireguardEndpoint(config)
            config.trim().startsWith("{") -> extractFromJson(config)
            else -> null
        }
    }

    private fun extractFromUrl(url: String): String? {
        return url.substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
            .takeIf { isValidIp(it) }
    }

    private fun extractWireguardEndpoint(config: String): String? {
        return config.substringAfter("Endpoint = ")
            .substringBefore(':')
            .takeIf { isValidIp(it) }
    }

    private fun extractFromJson(jsonText: String): String? {
        return try {
            JSONObject(jsonText).let { json ->
                listOf("server", "address", "host").firstNotNullOfOrNull { key ->
                    json.optString(key).takeIf { isValidIp(it) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractIpFromText(text: String): String? {
        val ipRegex = """\b(25[0-5]|2[0-4]\d|[01]?\d\d?)\.(25[0-5]|2[0-4]\d|[01]?\d\d?)\.(25[0-5]|2[0-4]\d|[01]?\d\d?)\.(25[0-5]|2[0-4]\d|[01]?\d\d?)\b""".toRegex()
        return ipRegex.find(text)?.value?.takeIf { isValidIp(it) }
    }

    private fun isValidIp(ip: String): Boolean {
        if (ip.contains("[a-zA-Z]".toRegex())) return false
        return ip.split('.').size == 4 && ip.split('.').all { part ->
            part.toIntOrNull()?.let { it in 0..255 } ?: false
        }
    }

    private suspend fun getServerLocation(ip: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$IPINFO_BASE_URL/$ip?token=$IPINFO_TOKEN")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { reader ->
                        val response = reader.readText()
                        parseIpInfoResponse(response)
                    }
                } else {
                    Log.e(TAG, "ipinfo.io API error: ${connection.responseCode}")
                    "Unknown (API Error)"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch location from ipinfo.io", e)
                "Unknown (Network Error)"
            }
        }
    }

    private fun parseIpInfoResponse(json: String): String {
        return try {
            val jsonObj = JSONObject(json)
            val country = jsonObj.optString("country", "Unknown")
            val city = jsonObj.optString("city", "")
            val region = jsonObj.optString("region", "")
            val org = jsonObj.optString("org", "")
            
            // Format: "City, Region, Country · ISP"
            // Example: "Tokyo, Kanto, JP · Amazon.com"
            buildString {
                if (city.isNotEmpty()) append("$city, ")
                if (region.isNotEmpty() && region != city) append("$region, ")
                append(country)
                if (org.isNotEmpty()) append(" · ${org.substringBeforeLast(',').trim()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ipinfo.io response", e)
            "Unknown (Parse Error)"
        }
    }

    private fun getRandomConfigUrl(): String {
        val baseUrl = "https://raw.githubusercontent.com/barry-far/V2ray-Config/refs/heads/main/Sub"
        return "$baseUrl${(1..50).random()}.txt"
    }

    private fun fetchV2rayConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configContent = fetchConfigContent()
                val configLine = selectRandomConfigLine(configContent)
                val serverIp = extractIpFromConfig(configLine)
                val location = serverIp?.let { getServerLocation(it) } ?: "No IP detected"

                updateUI(configLine, location)
            } catch (e: Exception) {
                handleConfigError(e)
            }
        }
    }

    private suspend fun fetchConfigContent(): String {
        return URL(getRandomConfigUrl()).openStream().bufferedReader().use { it.readText() }
    }

    private fun selectRandomConfigLine(content: String): String {
        val validLines = content.lines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line -> PROTOCOL_PREFIXES.any { line.startsWith(it) } }
            .toList()
        
        return if (validLines.isNotEmpty()) {
            validLines.random()
        } else {
            "No valid configuration found"
        }
    }

    private suspend fun updateUI(config: String, location: String) {
        withContext(Dispatchers.Main) {
            textConfig.text = config
            textLoading.text = detectConfigType(config)
            textCountry.text = location
            progressIndicator.hide()
            progressIndicator.visibility = View.GONE
            BgtextConfig.visibility = View.VISIBLE
            BgtextCountry.visibility = View.VISIBLE
        }
    }

    private fun handleConfigError(error: Exception) {
        runOnUiThread {
            textConfig.text = "Error: ${error.localizedMessage}"
            textLoading.text = ""
            progressIndicator.hide()
            progressIndicator.visibility = View.GONE
        }
    }

    private fun detectConfigType(config: String): String {
        return when {
            config.startsWith("vmess://") -> "VMess"
            config.startsWith("vless://") -> "VLESS"
            config.startsWith("trojan://") -> "Trojan"
            config.startsWith("ss://") -> "Shadowsocks"
            config.startsWith("http://") -> "HTTP"
            config.startsWith("socks://") -> "SOCKS"
            config.startsWith("wireguard://") -> "WireGuard"
            config.startsWith("hysteria2://") -> "Hysteria2"
            else -> "Unknown"
        }
    }
}
