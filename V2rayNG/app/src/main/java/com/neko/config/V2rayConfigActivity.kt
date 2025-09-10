package com.neko.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.neko.v2ray.R
import com.neko.v2ray.ui.BaseActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.io.IOException
import java.net.Socket
import java.net.UnknownHostException
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class V2rayConfigActivity : BaseActivity() {

    // UI Components
    private lateinit var textConfig: TextView
    private lateinit var textLoading: TextView
    private lateinit var btnGenerate: Button
    private lateinit var btnCopy: ImageView
    private lateinit var btnPing: ImageView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var textCountry: TextView
    private lateinit var textPingResult: TextView
    private lateinit var BgtextConfig: LinearLayout
    private lateinit var BgtextCountry: LinearLayout
    private lateinit var BgtextPing: LinearLayout

    private var currentConfig: String = ""
    private var currentIp: String? = null
    private var currentPort: Int = 443 // Default port

    companion object {
        private const val TAG = "V2rayConfigActivity"
        private const val IPINFO_BASE_URL = "https://ipinfo.io"
        private const val PING_TIMEOUT = 3000 // 3 seconds
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
        btnPing = findViewById(R.id.btnPing)
        progressIndicator = findViewById(R.id.progressIndicator)
        textCountry = findViewById(R.id.textCountry)
        textPingResult = findViewById(R.id.textPingResult)
        BgtextConfig = findViewById(R.id.BgtextConfig)
        BgtextCountry = findViewById(R.id.BgtextCountry)
        BgtextPing = findViewById(R.id.BgtextPing)

        BgtextConfig.visibility = View.GONE
        BgtextCountry.visibility = View.GONE
        BgtextPing.visibility = View.GONE
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

        btnPing.setOnClickListener {
            currentIp?.let { ip ->
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        testPing(ip, currentPort)
                    } catch (e: Exception) {
                        Toast.makeText(this@V2rayConfigActivity, "Ping test failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: run {
                Toast.makeText(this@V2rayConfigActivity, "No IP address available for ping test", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetUI() {
        textLoading.text = "Loading..."
        textConfig.text = ""
        textCountry.text = ""
        textPingResult.text = ""
        BgtextCountry.visibility = View.GONE
        BgtextConfig.visibility = View.GONE
        BgtextPing.visibility = View.GONE
        currentConfig = ""
        currentIp = null
        currentPort = 443 // Reset to default
    }

    private fun copyConfigToClipboard() {
        val text = textConfig.text.toString()
        if (text.isNotBlank()) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).apply {
                setPrimaryClip(ClipData.newPlainText("V2Ray Config", text))
                Toast.makeText(this@V2rayConfigActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this@V2rayConfigActivity, "No config to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun testPing(ip: String, port: Int) {
        withContext(Dispatchers.Main) {
            textPingResult.text = "Testing ping to port $port..."
            BgtextPing.visibility = View.VISIBLE
        }

        val pingResult = withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), PING_TIMEOUT)
                socket.close()
                val endTime = System.currentTimeMillis()
                val pingTime = endTime - startTime
                "Ping: ${pingTime}ms (port $port)"
            } catch (e: UnknownHostException) {
                "Ping failed: Unknown host (port $port)"
            } catch (e: IOException) {
                "Ping failed: Timeout or no connection (port $port)"
            } catch (e: Exception) {
                "Ping failed: ${e.localizedMessage} (port $port)"
            }
        }

        withContext(Dispatchers.Main) {
            textPingResult.text = pingResult
        }
    }

    private suspend fun extractIpAndPortFromConfig(config: String): Pair<String?, Int> {
        var ip: String? = null
        var port = 443 // Default port

        extractDomainFromConfig(config)?.let { domain ->
            resolveDomainToIp(domain)?.let { resolvedIp ->
                ip = resolvedIp
            }
        }

        if (ip == null) {
            when {
                isEncodedConfig(config) -> {
                    val result = extractFromEncodedConfigWithPort(config)
                    ip = result.first
                    port = result.second ?: port
                }
                else -> {
                    val result = extractFromPlainConfigWithPort(config)
                    ip = result.first
                    port = result.second ?: port
                }
            } ?: run {
                ip = extractIpFromText(config)
            }
        }

        // If IP is found but port is not, find port from config
        if (ip != null && port == 443) {
            port = extractPortFromConfig(config) ?: port
        }

        return Pair(ip, port)
    }

    private fun extractPortFromConfig(config: String): Int? {
        return when {
            config.startsWith("vmess://") -> extractPortFromVmess(config)
            config.startsWith("vless://") -> extractPortFromVless(config)
            config.startsWith("ss://") -> extractPortFromShadowsocks(config)
            config.startsWith("trojan://") -> extractPortFromTrojan(config)
            config.startsWith("hysteria2://") -> extractPortFromHysteria(config)
            else -> extractPortFromText(config)
        }
    }

    private fun extractPortFromVmess(config: String): Int? {
        val decoded = decodeBase64Safe(config.removePrefix("vmess://"))
        return try {
            JSONObject(decoded).optString("port").toIntOrNull()
        } catch (e: Exception) {
            extractPortFromText(decoded)
        }
    }

    private fun extractPortFromVless(config: String): Int? {
        val afterPrefix = config.removePrefix("vless://")
        val beforeParams = afterPrefix.substringBefore('?')
        return beforeParams.substringAfterLast(':').substringBefore('/').toIntOrNull()
            ?: extractPortFromText(afterPrefix)
    }

    private fun extractPortFromShadowsocks(config: String): Int? {
        val afterPrefix = config.removePrefix("ss://")
        val decoded = decodeBase64Safe(afterPrefix.substringBefore('#'))
        return decoded.substringAfterLast('@').substringAfter(':').substringBefore('/').toIntOrNull()
            ?: extractPortFromText(decoded)
    }

    private fun extractPortFromTrojan(config: String): Int? {
        val afterPrefix = config.removePrefix("trojan://")
        val beforeParams = afterPrefix.substringBefore('?')
        return beforeParams.substringAfterLast(':').substringBefore('/').toIntOrNull()
            ?: extractPortFromText(afterPrefix)
    }

    private fun extractPortFromHysteria(config: String): Int? {
        val afterPrefix = config.removePrefix("hysteria2://")
        return afterPrefix.substringAfterLast(':').substringBefore('/').toIntOrNull()
            ?: extractPortFromText(afterPrefix)
    }

    private fun extractPortFromText(text: String): Int? {
        val portRegex = """:(\d+)(?=/|\?|$|#)""".toRegex()
        return portRegex.find(text)?.groups?.get(1)?.value?.toIntOrNull()
    }

    private fun extractFromEncodedConfigWithPort(config: String): Pair<String?, Int?> {
        return try {
            when {
                config.startsWith("vmess://") -> handleVmessConfigWithPort(config)
                config.startsWith("vless://") -> handleVlessConfigWithPort(config)
                config.startsWith("ss://") -> handleShadowsocksConfigWithPort(config)
                config.startsWith("trojan://") -> handleTrojanConfigWithPort(config)
                config.startsWith("hysteria2://") -> handleHysteriaConfigWithPort(config)
                else -> Pair(null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Config decoding failed", e)
            Pair(extractIpFromText(config), extractPortFromText(config))
        }
    }

    private fun handleVmessConfigWithPort(config: String): Pair<String?, Int?> {
        val decoded = decodeBase64Safe(config.removePrefix("vmess://"))
        return try {
            JSONObject(decoded).let { json ->
                val ip = listOf("add", "server", "address").firstNotNullOfOrNull { key ->
                    json.optString(key).takeIf { isValidIp(it) }
                }
                val port = json.optString("port").toIntOrNull()
                Pair(ip ?: extractIpFromText(decoded), port ?: extractPortFromText(decoded))
            }
        } catch (e: Exception) {
            Pair(extractIpFromText(decoded), extractPortFromText(decoded))
        }
    }

    private fun handleVlessConfigWithPort(config: String): Pair<String?, Int?> {
        val decoded = decodeBase64Safe(config.removePrefix("vless://"))
        val ip = extractIpFromText(decoded)
        val port = extractPortFromText(decoded)
        return Pair(ip, port)
    }

    private fun handleShadowsocksConfigWithPort(config: String): Pair<String?, Int?> {
        val decoded = decodeBase64Safe(config.removePrefix("ss://"))
        val parts = decoded.substringAfter('@').split(':')
        val ip = parts.getOrNull(0)?.takeIf { isValidIp(it) }
        val port = parts.getOrNull(1)?.toIntOrNull()
        return Pair(ip ?: extractIpFromText(decoded), port ?: extractPortFromText(decoded))
    }

    private fun handleTrojanConfigWithPort(config: String): Pair<String?, Int?> {
        val decoded = decodeBase64Safe(config.removePrefix("trojan://"))
        val parts = decoded.substringAfter('@').split(':')
        val ip = parts.getOrNull(0)?.takeIf { isValidIp(it) }
        val port = parts.getOrNull(1)?.toIntOrNull()
        return Pair(ip ?: extractIpFromText(decoded), port ?: extractPortFromText(decoded))
    }

    private fun handleHysteriaConfigWithPort(config: String): Pair<String?, Int?> {
        val decoded = decodeBase64Safe(config.removePrefix("hysteria2://"))
        val ip = extractIpFromText(decoded)
        val port = extractPortFromText(decoded)
        return Pair(ip, port)
    }

    private fun extractFromPlainConfigWithPort(config: String): Pair<String?, Int?> {
        return when {
            config.contains("://") -> extractFromUrlWithPort(config)
            config.contains("Endpoint = ") -> extractWireguardEndpointWithPort(config)
            config.trim().startsWith("{") -> extractFromJsonWithPort(config)
            else -> Pair(null, null)
        }
    }

    private fun extractFromUrlWithPort(url: String): Pair<String?, Int?> {
        val afterProtocol = url.substringAfter("://")
        val hostPort = afterProtocol.substringBefore('/').substringBefore('?')
        val parts = hostPort.split(':')
        val ip = parts.getOrNull(0)?.takeIf { isValidIp(it) }
        val port = parts.getOrNull(1)?.toIntOrNull()
        return Pair(ip, port)
    }

    private fun extractWireguardEndpointWithPort(config: String): Pair<String?, Int?> {
        val endpoint = config.substringAfter("Endpoint = ").substringBefore('#')
        val parts = endpoint.split(':')
        val ip = parts.getOrNull(0)?.takeIf { isValidIp(it) }
        val port = parts.getOrNull(1)?.toIntOrNull()
        return Pair(ip, port)
    }

    private fun extractFromJsonWithPort(jsonText: String): Pair<String?, Int?> {
        return try {
            JSONObject(jsonText).let { json ->
                val ip = listOf("server", "address", "host").firstNotNullOfOrNull { key ->
                    json.optString(key).takeIf { isValidIp(it) }
                }
                val port = listOf("port", "server_port").firstNotNullOfOrNull { key ->
                    json.optInt(key).takeIf { it > 0 }
                }
                Pair(ip, port)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
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

    private suspend fun getServerLocation(ip: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$IPINFO_BASE_URL/$ip/json")
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
                val (serverIp, serverPort) = extractIpAndPortFromConfig(configLine)
                val location = serverIp?.let { getServerLocation(it) } ?: "No IP detected"

                currentConfig = configLine
                currentIp = serverIp
                currentPort = serverPort

                updateUI(configLine, location, currentPort)
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

    private suspend fun updateUI(config: String, location: String, port: Int) {
        withContext(Dispatchers.Main) {
            textConfig.text = config
            textLoading.text = "${detectConfigType(config)} (port: $port)"
            textCountry.text = location
            progressIndicator.hide()
            progressIndicator.visibility = View.GONE
            BgtextConfig.visibility = View.VISIBLE
            BgtextCountry.visibility = View.VISIBLE
            
            // Show ping button only if we have a valid IP
            if (currentIp != null) {
                BgtextPing.visibility = View.VISIBLE
                textPingResult.text = "Click ping button to test port $port"
            }
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
