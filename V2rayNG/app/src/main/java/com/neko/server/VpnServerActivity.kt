package com.neko.server

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neko.v2ray.R
import com.neko.v2ray.ui.BaseActivity
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

class VpnServerActivity : BaseActivity() {
    private lateinit var analyzer: VPNAnalyzer
    private lateinit var connectionAdapter: ConnectionAdapter
    private lateinit var serverAdapter: ServerAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn_server)
        
        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        
        analyzer = VPNAnalyzer().apply {
            // Sample data
            addServer(VPNServer("192.168.1.10", "Singapore", 1000, 750, 
                     Duration.ofDays(15), 0.65))
            addConnection(VPNConnection("user1", "192.168.1.10", 
                     LocalDateTime.now(), null, 524288000, "OpenVPN"))
        }
        
        // Setup RecyclerViews
        setupConnectionRecyclerView()
        setupServerRecyclerView()
        
        // Setup buttons
        findViewById<Button>(R.id.btn_add_connection).setOnClickListener {
            showAddConnectionDialog()
        }
        
        findViewById<Button>(R.id.btn_add_server).setOnClickListener {
            showAddServerDialog()
        }
        
        // Show dashboard initially
        showDashboard()
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
        findViewById<TextView>(R.id.tv_total_connections).text = analyzer.connections.size.toString()
        findViewById<TextView>(R.id.tv_total_servers).text = analyzer.servers.size.toString()
        
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
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showReport() {
        val report = analyzer.generateReport()
        AlertDialog.Builder(this)
            .setTitle("VPN Analysis Report")
            .setMessage(report)
            .setPositiveButton("OK", null)
            .show()
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
            appendLine("Total Connections: ${connections.size}")
            appendLine("Total Servers: ${servers.size}")
            
            val topUsers = analyzeBandwidthUsage()
                .entries.sortedByDescending { it.value }.take(3)
            
            appendLine("\nTop Bandwidth Users:")
            topUsers.forEach { 
                appendLine("- ${it.key}: ${it.value / (1024 * 1024)} MB") 
            }
            
            appendLine("\nServer Status:")
            servers.forEach {
                appendLine("${it.ip} (${it.location}): ${it.capacityPercentage}% capacity")
            }
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
