package com.neko.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import com.github.anastr.speedviewlib.PointerSpeedometer
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.neko.v2ray.R
import com.neko.v2ray.ui.BaseActivity
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.InetAddress
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class SpeedTestActivity : BaseActivity() {

    private lateinit var speedometer: PointerSpeedometer
    private lateinit var textPing: TextView
    private lateinit var textJitter: TextView
    private lateinit var textDownload: TextView
    private lateinit var textUpload: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var testJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Dummy data untuk upload test (dibuat sekali saja)
    private val dummyData by lazy { ByteArray(2 * 1024 * 1024) } // Reduced to 2MB untuk menghemat memori

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.uwu_speedtest)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val toolbarLayout = findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        speedometer = findViewById(R.id.speedometer)
        textPing = findViewById(R.id.textPing)
        textJitter = findViewById(R.id.textJitter)
        textDownload = findViewById(R.id.textDownload)
        textUpload = findViewById(R.id.textUpload)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        startButton.setOnClickListener { startSpeedTest() }
        stopButton.setOnClickListener { stopSpeedTest() }

        startButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        
        // Inisialisasi dummy data di background
        scope.launch(Dispatchers.IO) {
            dummyData.fill(0) // Isi dengan data dummy
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && 
               (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    private fun startSpeedTest() {
        if (!isInternetAvailable()) {
            Toast.makeText(this, "Please check your internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        startButton.visibility = View.GONE
        stopButton.visibility = View.VISIBLE

        textPing.text = "Testing..."
        textJitter.text = "Testing..."
        textDownload.text = "Testing..."
        textUpload.text = "Testing..."
        speedometer.visibility = View.VISIBLE
        speedometer.alpha = 1f
        speedometer.speedTo(0f)

        testJob = scope.launch {
            try {
                // Test ping dan jitter
                val (ping, jitter) = withContext(Dispatchers.IO) {
                    measurePingAndJitter("8.8.8.8", 5)
                }
                textPing.text = "$ping ms"
                textJitter.text = "$jitter ms"

                // Test download speed
                val downloadSpeed = measureDownloadSpeed()
                textDownload.text = "%.2f Mbps".format(downloadSpeed)

                // Update speedometer dengan hasil download
                speedometer.speedTo(downloadSpeed.toFloat())
                delay(1000) // Jeda singkat sebelum upload test

                // Test upload speed
                val uploadSpeed = measureUploadSpeed()
                textUpload.text = "%.2f Mbps".format(uploadSpeed)

                // Update speedometer dengan hasil upload
                speedometer.speedTo(uploadSpeed.toFloat())

            } catch (e: CancellationException) {
                // Test dibatalkan oleh user, tidak perlu menampilkan error
                Log.d("SpeedTest", "Test cancelled by user")
            } catch (e: Exception) {
                Log.e("SpeedTest", "Error during speed test: ${e.message}", e)
                Toast.makeText(this@SpeedTestActivity, "Error: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                resetUI()
            } finally {
                withContext(Dispatchers.Main) {
                    // Animasi fade out untuk speedometer
                    speedometer.animate()
                        .alpha(0f)
                        .setDuration(600)
                        .withEndAction {
                            speedometer.visibility = View.GONE
                            startButton.visibility = View.VISIBLE
                            stopButton.visibility = View.GONE
                        }
                        .start()
                }
            }
        }
    }

    private fun stopSpeedTest() {
        testJob?.cancel("Test stopped by user")
        testJob = null
        resetUI()
    }

    private fun resetUI() {
        startButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        speedometer.visibility = View.GONE
        speedometer.alpha = 1f
    }

    private suspend fun measureDownloadSpeed(): Double = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://github.com/topjohnwu/Magisk/releases/download/canary-28103/app-release.apk",
            "https://download.mozilla.org/?product=firefox-latest-ssl&os=win64&lang=en-US",
            "https://dl.google.com/android/studio/install/android-studio-ide-2023.3.1.20-windows.exe"
        )

        var totalBytes = 0L
        var speedMbps = 0.0
        var successful = false

        // Coba beberapa URL jika yang pertama gagal
        for (url in urls) {
            if (isActive.not()) break // Periksa apakah coroutine masih aktif

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) continue

                    val body = response.body
                    val contentLength = body?.contentLength() ?: 0L
                    val stream = body?.byteStream() ?: continue

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    val startTime = System.nanoTime()
                    var lastUpdateTime = System.currentTimeMillis()

                    while (stream.read(buffer).also { bytesRead = it } != -1 && isActive) {
                        totalBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 300) {
                            val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
                            speedMbps = (totalBytes * 8) / (elapsedSeconds * 1000 * 1000)
                            
                            withContext(Dispatchers.Main) {
                                speedometer.speedTo(speedMbps.toFloat())
                            }
                            lastUpdateTime = now
                        }

                        // Batasi test download maksimal 10MB atau 15 detik
                        val elapsedTime = (System.nanoTime() - startTime) / 1_000_000_000.0
                        if (totalBytes > 10 * 1024 * 1024 || elapsedTime > 15.0) {
                            break
                        }
                    }

                    successful = true
                    break // Keluar dari loop jika berhasil
                }
            } catch (e: Exception) {
                Log.w("DownloadTest", "Failed with URL: $url, error: ${e.message}")
                continue // Coba URL berikutnya
            }
        }

        return@withContext if (successful) speedMbps else 0.0
    }

    private suspend fun measureUploadSpeed(): Double = withContext(Dispatchers.IO) {
        val uploadUrls = listOf(
            "https://httpbin.org/post",
            "https://postman-echo.com/post",
            "https://httpbun.com/post"
        )

        val mediaType = "application/octet-stream".toMediaType()
        var speedMbps = 0.0
        var successful = false

        for (uploadUrl in uploadUrls) {
            if (isActive.not()) break

            try {
                val requestBody = object : RequestBody() {
                    override fun contentType() = mediaType
                    override fun contentLength() = dummyData.size.toLong()

                    override fun writeTo(sink: okio.BufferedSink) {
                        val chunkSize = 8192
                        var uploaded = 0L
                        val startTime = System.nanoTime()
                        var lastUpdate = System.currentTimeMillis()

                        while (uploaded < dummyData.size && isActive) {
                            val size = min(chunkSize, dummyData.size - uploaded.toInt())
                            sink.write(dummyData, uploaded.toInt(), size)
                            uploaded += size

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 300) {
                                val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
                                speedMbps = (uploaded * 8) / (elapsedSeconds * 1000 * 1000)
                                
                                // Pindahkan update UI ke coroutine scope utama
                                scope.launch(Dispatchers.Main) {
                                    speedometer.speedTo(speedMbps.toFloat())
                                }
                                lastUpdate = now
                            }

                            // Batasi test upload maksimal 15 detik
                            val elapsedTime = (System.nanoTime() - startTime) / 1_000_000_000.0
                            if (elapsedTime > 15.0) {
                                break
                            }
                        }
                    }
                }

                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        successful = true
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w("UploadTest", "Failed with URL: $uploadUrl, error: ${e.message}")
                continue
            }
        }

        return@withContext if (successful) speedMbps else 0.0
    }

    private suspend fun measurePingAndJitter(host: String, count: Int): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val times = mutableListOf<Long>()
        repeat(count) {
            if (isActive.not()) return@withContext Pair(0, 0)
            
            val start = System.nanoTime()
            try {
                if (InetAddress.getByName(host).isReachable(2000)) { // Timeout 2 detik
                    val time = (System.nanoTime() - start) / 1_000_000
                    times.add(time)
                } else {
                    times.add(2000L) // Timeout
                }
            } catch (e: Exception) {
                times.add(2000L) // Error
            }
            delay(300)
        }

        val avg = if (times.isNotEmpty()) times.average().roundToInt() else 0
        val jitter = if (times.size > 1) {
            times.zipWithNext { a, b -> abs(a - b) }.average().roundToInt()
        } else {
            0
        }
        Pair(avg, jitter)
    }

    override fun onDestroy() {
        super.onDestroy()
        testJob?.cancel()
        scope.cancel()
    }

    override fun onBackPressed() {
        stopSpeedTest()
        super.onBackPressed()
    }
}
