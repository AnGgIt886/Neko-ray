package com.neko.server

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neko.v2ray.R
import com.neko.v2ray.ui.BaseActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.random.Random

class VpnServerActivity : BaseActivity() {
    private lateinit var analyzer: VPNAnalyzer
    private lateinit var connectionAdapter: ConnectionAdapter
    private lateinit var serverAdapter: ServerAdapter
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var monitoringActive = false
    private var monitoringInterval = 5L // seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn_server)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        analyzer = VPNAnalyzer().apply {
            // Initialize with empty data
            addServer(VPNServer("vpn-server-1", "Singapore", 1000, 0, 
                     Duration.ofDays(0), 0.0))
        }
        
        setupConnectionRecyclerView()
        setupServerRecyclerView()
        
        findViewById<Button>(R.id.btn_add_connection).setOnClickListener {
            showAddConnectionDialog()
        }
        
        findViewById<Button>(R.id.btn_add_server).setOnClickListener {
            showAddServerDialog()
        }
        
        findViewById<Button>(R.id.btn_start_monitoring).setOnClickListener {
            toggleMonitoring()
        }
        
        showDashboard()
    }

    private fun toggleMonitoring() {
        monitoringActive = !monitoringActive
        val monitorBtn = findViewById<Button>(R.id.btn_start_monitoring)
        
        if (monitoringActive) {
            monitorBtn.text = "Stop Monitoring"
            startRealTimeMonitoring()
            Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
        } else {
            monitorBtn.text = "Start Monitoring"
            stopRealTimeMonitoring()
            Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRealTimeMonitoring() {
        executor.scheduleAtFixedRate({
            try {
                // In a real app, this would fetch actual VPN server data
                val newConnections = fetchRealTimeConnections()
                val serverStats = fetchServerStats()
                
                runOnUiThread {
                    analyzer.connections.clear()
                    analyzer.connections.addAll(newConnections)
                    
                    analyzer.servers.clear()
                    analyzer.servers.addAll(serverStats)
                    
                    updateDashboardStats()
                    connectionAdapter.notifyDataSetChanged()
                    serverAdapter.notifyDataSetChanged()
                    
                    // Show warning if any suspicious activity
                    val suspicious = analyzer.getSuspiciousConnections()
                    if (suspicious.isNotEmpty()) {
                        showAlert("Suspicious Activity", 
                            "Detected ${suspicious.size} high-bandwidth connections")
                    }
                }
            } catch (e: Exception) {
                Log.e("VPNMonitor", "Monitoring error", e)
                runOnUiThread {
                    Toast.makeText(this, "Monitoring error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }, 0, monitoringInterval, TimeUnit.SECONDS)
    }

    private fun stopRealTimeMonitoring() {
        executor.shutdownNow()
    }

    private fun fetchRealTimeConnections(): List<VPNConnection> {
        // In a real implementation, this would parse actual VPN logs
        // For demo purposes, we generate random data
        return List(Random.nextInt(5, 15)) {
            VPNConnection(
                userId = "user-${Random.nextInt(1000, 9999)}",
                serverIp = "10.8.0.${Random.nextInt(2, 254)}",
                startTime = LocalDateTime.now().minusMinutes(Random.nextLong(1, 60)),
                endTime = if (Random.nextBoolean()) LocalDateTime.now() else null,
                bytesTransferred = Random.nextLong(50 * 1024 * 1024, 500 * 1024 * 1024),
                protocol = listOf("OpenVPN", "WireGuard", "IPSec").random()
            )
        }
    }

    private fun fetchServerStats(): List<VPNServer> {
        // In a real implementation, this would get actual server stats
        return listOf(
            VPNServer(
                ip = "vpn-server-1",
                location = "Singapore",
                maxCapacity = 1000,
                currentConnections = analyzer.connections.size,
                uptime = Duration.ofDays(Random.nextLong(1, 30)),
                loadAverage = Random.nextDouble(0.1, 1.0)
            )
        )
    }

    private fun setupConnectionRecyclerView() {
        connectionAdapter = ConnectionAdapter(analyzer.connections)
        val recyclerView = findViewById<RecyclerView>(R.id.connections_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = connectionAdapter
    }
    
    private fun setupServerRecyclerView() {
        serverAdapter = ServerAdapter(analyzer.servers)
        val recyclerView = findViewById<RecyclerView>(R.id.servers_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = serverAdapter
    }
    
    private fun showDashboard() {
        findViewById<LinearLayout>(R.id.dashboard_view).visibility = View.VISIBLE
        findViewById<RecyclerView>(R.id.connections_recycler_view).visibility = View.GONE
        findViewById<RecyclerView>(R.id.servers_recycler_view).visibility = View.GONE
        updateDashboardStats()
    }
    
    private fun updateDashboardStats() {
        findViewById<TextView>(R.id.tv_active_connections).text = analyzer.getActiveConnectionsCount().toString()
        findViewById<TextView>(R.id.tv_total_bandwidth).text = analyzer.getTotalBandwidthUsage()
        
        val mostLoaded = analyzer.findMostLoadedServer()
        mostLoaded?.let {
            findViewById<TextView>(R.id.tv_most_loaded_server).text = "${it.ip} (${it.location})"
            val progressBar = findViewById<ProgressBar>(R.id.progress_server_load)
            progressBar.progress = (it.loadAverage * 100).toInt()
            progressBar.progressDrawable.colorFilter = PorterDuffColorFilter(
                if (it.loadAverage > 0.8) {
                    ContextCompat.getColor(this, android.R.color.holo_red_light)
                } else {
                    ContextCompat.getColor(this, android.R.color.holo_green_light)
                },
                PorterDuff.Mode.SRC_IN
            )
        }
    }
    
    private fun showConnections() {
        findViewById<LinearLayout>(R.id.dashboard_view).visibility = View.GONE
        findViewById<RecyclerView>(R.id.connections_recycler_view).visibility = View.VISIBLE
        findViewById<RecyclerView>(R.id.servers_recycler_view).visibility = View.GONE
        connectionAdapter.notifyDataSetChanged()
    }
    
    private fun showServers() {
        findViewById<LinearLayout>(R.id.dashboard_view).visibility = View.GONE
        findViewById<RecyclerView>(R.id.connections_recycler_view).visibility = View.GONE
        findViewById<RecyclerView>(R.id.servers_recycler_view).visibility = View.VISIBLE
        serverAdapter.notifyDataSetChanged()
    }
    
    private fun showAddConnectionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_connection, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add New Connection")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val userId = dialogView.findViewById<EditText>(R.id.et_user_id).text.toString()
                val serverIp = dialogView.findViewById<EditText>(R.id.et_server_ip).text.toString()
                val mb = dialogView.findViewById<EditText>(R.id.et_bytes_mb).text.toString().toLongOrNull() ?: 0
                val protocol = dialogView.findViewById<Spinner>(R.id.spinner_protocol).selectedItem.toString()
                
                analyzer.addConnection(VPNConnection(
                    userId, serverIp, LocalDateTime.now(), null, mb * 1024 * 1024, protocol
                ))
                
                updateDashboardStats()
                connectionAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Connection added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // Setup protocol spinner
        val spinner = dialogView.findViewById<Spinner>(R.id.spinner_protocol)
        ArrayAdapter.createFromResource(
            this,
            R.array.protocol_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }
        
        dialog.show()
    }
    
    private fun showAddServerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add New Server")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val ip = dialogView.findViewById<EditText>(R.id.et_server_ip).text.toString()
                val location = dialogView.findViewById<EditText>(R.id.et_location).text.toString()
                val maxCap = dialogView.findViewById<EditText>(R.id.et_max_capacity).text.toString().toIntOrNull() ?: 0
                val currentConn = dialogView.findViewById<EditText>(R.id.et_current_connections).text.toString().toIntOrNull() ?: 0
                val uptime = dialogView.findViewById<EditText>(R.id.et_uptime_days).text.toString().toLongOrNull() ?: 0
                val loadAvg = dialogView.findViewById<EditText>(R.id.et_load_average).text.toString().toDoubleOrNull() ?: 0.0
                
                analyzer.addServer(VPNServer(
                    ip, location, maxCap, currentConn, Duration.ofDays(uptime), loadAvg
                ))
                
                updateDashboardStats()
                serverAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Server added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_server, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_dashboard -> {
                showDashboard()
                true
            }
            R.id.menu_connections -> {
                showConnections()
                true
            }
            R.id.menu_servers -> {
                showServers()
                true
            }
            R.id.menu_report -> {
                showReport()
                true
            }
            R.id.menu_settings -> {
                showSettings()
                true
            }
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showReport() {
        val report = analyzer.generateReport()
        AlertDialog.Builder(this)
            .setTitle("VPN Analysis Report")
            .setMessage(report)
            .setPositiveButton("OK", null)
            .setNeutralButton("Export") { _, _ ->
                exportReport(report)
            }
            .show()
    }
    
    private fun exportReport(report: String) {
        // In a real app, this would save to file or share
        Toast.makeText(this, "Report exported", Toast.LENGTH_SHORT).show()
    }
    
    private fun showSettings() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val intervalSeek = dialogView.findViewById<SeekBar>(R.id.seek_monitor_interval)
        intervalSeek.progress = monitoringInterval.toInt()
        
        AlertDialog.Builder(this)
            .setTitle("Monitoring Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                monitoringInterval = intervalSeek.progress.toLong()
                if (monitoringActive) {
                    stopRealTimeMonitoring()
                    startRealTimeMonitoring()
                }
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroy() {
        stopRealTimeMonitoring()
        super.onDestroy()
    }
}

// Data classes
data class VPNConnection(
    val userId: String,
    val serverIp: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime?,
    val bytesTransferred: Long,
    val protocol: String
)

data class VPNServer(
    val ip: String,
    val location: String,
    val maxCapacity: Int,
    val currentConnections: Int,
    val uptime: Duration,
    val loadAverage: Double
) {
    val capacityPercentage: Int
        get() = (currentConnections.toDouble() / maxCapacity * 100).roundToInt()
}

// Analyzer class
class VPNAnalyzer {
    val connections = mutableListOf<VPNConnection>()
    val servers = mutableListOf<VPNServer>()

    fun addConnection(connection: VPNConnection) {
        connections.add(connection)
    }

    fun addServer(server: VPNServer) {
        servers.add(server)
    }

    fun analyzeBandwidthUsage(): Map<String, Long> {
        return connections.groupBy { it.userId }
            .mapValues { (_, conns) -> conns.sumOf { it.bytesTransferred } }
    }

    fun findMostLoadedServer(): VPNServer? {
        return servers.maxByOrNull { it.loadAverage }
    }

    fun generateReport(): String {
        return buildString {
            appendLine("=== VPN Analysis Report ===")
            appendLine("Generated: ${LocalDateTime.now()}")
            appendLine("Total Connections: ${connections.size}")
            appendLine("Active Connections: ${getActiveConnectionsCount()}")
            appendLine("Total Bandwidth: ${getTotalBandwidthUsage()}")
            
            val topUsers = analyzeBandwidthUsage()
                .entries.sortedByDescending { it.value }.take(5)
            
            appendLine("\nTop Bandwidth Users:")
            topUsers.forEach { 
                appendLine("- ${it.key}: ${it.value / (1024 * 1024)} MB") 
            }
            
            appendLine("\nProtocol Distribution:")
            getProtocolDistribution().forEach { (proto, count) ->
                appendLine("- $proto: $count connections")
            }
            
            appendLine("\nServer Status:")
            servers.forEach {
                appendLine("${it.ip} (${it.location}):")
                appendLine("  Connections: ${it.currentConnections}/${it.maxCapacity}")
                appendLine("  Load: ${"%.2f".format(it.loadAverage)}")
                appendLine("  Uptime: ${it.uptime.toDays()} days")
            }
            
            val suspicious = getSuspiciousConnections()
            if (suspicious.isNotEmpty()) {
                appendLine("\n⚠️ Suspicious Activity:")
                suspicious.forEach {
                    appendLine("- ${it.userId} used ${it.bytesTransferred / (1024 * 1024)} MB")
                }
            }
        }
    }

    fun getActiveConnectionsCount(): Int = connections.count { it.endTime == null }
    
    fun getTotalBandwidthUsage(): String {
        val totalMB = connections.sumOf { it.bytesTransferred } / (1024 * 1024)
        return "$totalMB MB"
    }
    
    fun getProtocolDistribution(): Map<String, Int> {
        return connections.groupBy { it.protocol }
            .mapValues { (_, conns) -> conns.size }
    }
    
    fun getSuspiciousConnections(thresholdMB: Long = 500): List<VPNConnection> {
        return connections.filter { 
            it.bytesTransferred > thresholdMB * 1024 * 1024 
        }
    }
}

// Adapters
class ConnectionAdapter(private val connections: List<VPNConnection>) : 
    RecyclerView.Adapter<ConnectionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUserId: TextView = view.findViewById(R.id.tv_user_id)
        val tvServerIp: TextView = view.findViewById(R.id.tv_server_ip)
        val tvProtocol: TextView = view.findViewById(R.id.tv_protocol)
        val tvData: TextView = view.findViewById(R.id.tv_data_transferred)
        val tvDuration: TextView = view.findViewById(R.id.tv_duration)
        val layout: LinearLayout = view.findViewById(R.id.connection_item_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_connection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conn = connections[position]
        holder.tvUserId.text = conn.userId
        holder.tvServerIp.text = conn.serverIp
        holder.tvProtocol.text = conn.protocol
        holder.tvData.text = "${conn.bytesTransferred / (1024 * 1024)} MB"
        
        val duration = if (conn.endTime != null) {
            "${Duration.between(conn.startTime, conn.endTime).toMinutes()} mins"
        } else {
            "Active"
        }
        holder.tvDuration.text = duration
        
        // Highlight suspicious connections
        if (conn.bytesTransferred > 500 * 1024 * 1024) {
            holder.layout.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.warning_light))
        } else {
            holder.layout.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.transparent))
        }
    }

    override fun getItemCount() = connections.size
}

class ServerAdapter(private val servers: List<VPNServer>) : 
    RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIp: TextView = view.findViewById(R.id.tv_server_ip)
        val tvLocation: TextView = view.findViewById(R.id.tv_location)
        val tvConnections: TextView = view.findViewById(R.id.tv_connections)
        val progressCapacity: ProgressBar = view.findViewById(R.id.progress_capacity)
        val progressLoad: ProgressBar = view.findViewById(R.id.progress_load)
        val tvUptime: TextView = view.findViewById(R.id.tv_uptime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = servers[position]
        holder.tvIp.text = server.ip
        holder.tvLocation.text = server.location
        holder.tvConnections.text = "${server.currentConnections}/${server.maxCapacity}"
        holder.tvUptime.text = "${server.uptime.toDays()} days"
        
        holder.progressCapacity.progress = server.capacityPercentage
        holder.progressCapacity.progressDrawable.colorFilter = PorterDuffColorFilter(
            if (server.capacityPercentage > 80) {
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light)
            } else {
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light)
            },
            PorterDuff.Mode.SRC_IN
        )
        
        holder.progressLoad.progress = (server.loadAverage * 100).toInt()
        holder.progressLoad.progressDrawable.colorFilter = PorterDuffColorFilter(
            if (server.loadAverage > 0.8) {
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light)
            } else {
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light)
            },
            PorterDuff.Mode.SRC_IN
        )
    }

    override fun getItemCount() = servers.size
}
