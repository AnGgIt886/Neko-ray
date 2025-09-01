package com.neko.v2ray.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.neko.v2ray.AppConfig
import com.neko.v2ray.AppConfig.ANG_PACKAGE
import com.neko.v2ray.R
import com.neko.v2ray.databinding.ActivityLogcatBinding
import com.neko.v2ray.extension.toastSuccess
import com.neko.v2ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.LinkedList

class LogcatActivity : BaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }

    private var logsetsAll: MutableList<ParsedLog> = LinkedList()
    private var logsets: MutableList<ParsedLog> = LinkedList()
    private val adapter by lazy { LogcatRecyclerAdapter(this) }
    private var searchJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var isAutoRefreshEnabled = false
    private var currentQuery: String = ""
    private var currentLevelFilter: LogLevel? = null
    private val displayedLogsLimit = 2000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val toolbarLayout = findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.emptyState.visibility = View.GONE
        binding.loadingIndicator.visibility = View.GONE

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter

        binding.refreshLayout.setOnRefreshListener(this)

        val fabScroll = findViewById<FloatingActionButton>(R.id.fab_scroll)
        fabScroll.setOnClickListener {
            if (isScrolledToBottom()) {
                safeSmoothScrollToPosition(0)
                fabScroll.setImageResource(R.drawable.ic_baseline_arrow_downward_24)
            } else {
                safeSmoothScrollToPosition(adapter.itemCount - 1)
                fabScroll.setImageResource(R.drawable.ic_baseline_arrow_upward_24)
            }
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (isScrolledToBottom()) {
                    fabScroll.setImageResource(R.drawable.ic_baseline_arrow_upward_24)
                } else {
                    fabScroll.setImageResource(R.drawable.ic_baseline_arrow_downward_24)
                }
            }
        })

        logsets.add(LogcatRecyclerAdapter.parseLog(getString(R.string.pull_down_to_refresh)))
    }

    private fun getLogcat() {
        tryOrShowError({
            binding.refreshLayout.isRefreshing = true
            binding.loadingIndicator.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                val command = listOf(
                    "logcat", "-d", "-v", "time", "-s",
                    "GoLog,tun2socks,${ANG_PACKAGE},AndroidRuntime,System.err"
                )
                
                val process = Runtime.getRuntime().exec(command.toTypedArray())
                val allText = process.inputStream.bufferedReader().useLines { lines ->
                    lines.take(displayedLogsLimit).toList().reversed()
                }
                
                val parsedLogs = allText.map { LogcatRecyclerAdapter.parseLog(it) }
                
                withContext(Dispatchers.Main) {
                    logsetsAll = parsedLogs.toMutableList()
                    applyFilters()
                    binding.refreshLayout.isRefreshing = false
                    binding.loadingIndicator.visibility = View.GONE
                    showEmptyState(logsets.isEmpty())
                }
            }
        }, "Failed to retrieve logs")
    }

    private fun clearLogcat() {
        tryOrShowError({
            binding.loadingIndicator.visibility = View.VISIBLE
            
            lifecycleScope.launch(Dispatchers.Default) {
                val command = listOf("logcat", "-c")
                withContext(Dispatchers.IO) {
                    val process = Runtime.getRuntime().exec(command.toTypedArray())
                    process.waitFor()
                }
                launch(Dispatchers.Main) {
                    logsetsAll.clear()
                    logsets.clear()
                    refreshData()
                    binding.loadingIndicator.visibility = View.GONE
                    showEmptyState(true)
                }
            }
        }, "Failed to clear logs")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    currentQuery = newText ?: ""
                    filterLogs(currentQuery)
                    return false
                }
            })
            searchView.setOnCloseListener {
                currentQuery = ""
                filterLogs("")
                false
            }
        }

        val autoRefreshItem = menu.findItem(R.id.auto_refresh)
        autoRefreshItem.isChecked = isAutoRefreshEnabled

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            val logText = logsetsAll.joinToString("\n") { it.original }
            Utils.setClipboard(this, logText)
            toastSuccess(R.string.toast_success)
            true
        }

        R.id.clear_all -> {
            clearLogcat()
            true
        }
        
        R.id.export_logs -> {
            exportLogs()
            true
        }

        R.id.auto_refresh -> {
            isAutoRefreshEnabled = !isAutoRefreshEnabled
            item.isChecked = isAutoRefreshEnabled
            toggleAutoRefresh(isAutoRefreshEnabled)
            true
        }

        R.id.filter_all -> {
            currentLevelFilter = null
            filterLogs(currentQuery)
            true
        }

        R.id.filter_error -> {
            currentLevelFilter = LogLevel.ERROR
            filterLogs(currentQuery)
            true
        }

        R.id.filter_warning -> {
            currentLevelFilter = LogLevel.WARNING
            filterLogs(currentQuery)
            true
        }

        R.id.filter_info -> {
            currentLevelFilter = LogLevel.INFO
            filterLogs(currentQuery)
            true
        }

        R.id.filter_debug -> {
            currentLevelFilter = LogLevel.DEBUG
            filterLogs(currentQuery)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun filterLogs(content: String?) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch(Dispatchers.Default) {
            delay(500)
            
            val key = content?.trim() ?: ""
            
            var filtered = logsetsAll.toMutableList()
            
            currentLevelFilter?.let { level ->
                filtered = filtered.filter { it.level == level }.toMutableList()
            }
            
            if (key.isNotEmpty()) {
                filtered = filtered.filter { 
                    it.content.contains(key, ignoreCase = true) || 
                    it.tag.contains(key, ignoreCase = true)
                }.take(displayedLogsLimit).toMutableList()
            }
            
            withContext(Dispatchers.Main) {
                adapter.updateHighlightText(key)
                logsets = filtered
                refreshData()
                showEmptyState(filtered.isEmpty() && (key.isNotEmpty() || currentLevelFilter != null))
            }
        }
    }

    private fun applyFilters() {
        var filtered = logsetsAll.toMutableList()
        
        currentLevelFilter?.let { level ->
            filtered = filtered.filter { it.level == level }.toMutableList()
        }
        
        if (currentQuery.isNotEmpty()) {
            filtered = filtered.filter { 
                it.content.contains(currentQuery, ignoreCase = true) || 
                it.tag.contains(currentQuery, ignoreCase = true)
            }.take(displayedLogsLimit).toMutableList()
        }
        
        logsets = filtered
        refreshData()
        showEmptyState(filtered.isEmpty() && (currentQuery.isNotEmpty() || currentLevelFilter != null))
    }

    private fun showEmptyState(show: Boolean) {
        if (show) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun exportLogs() {
        binding.loadingIndicator.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(getExternalFilesDir(null), "logs_${System.currentTimeMillis()}.txt")
                val logText = logsetsAll.joinToString("\n") { it.original }
                file.writeText(logText)
                
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(
                        this@LogcatActivity, 
                        "Logs exported to ${file.name}", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(
                        this@LogcatActivity, 
                        "Failed to export logs", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun toggleAutoRefresh(enable: Boolean) {
        autoRefreshJob?.cancel()
        if (enable) {
            autoRefreshJob = lifecycleScope.launch {
                while (isActive) {
                    delay(5000)
                    getLogcat()
                }
            }
        }
    }

    private fun isScrolledToBottom(): Boolean {
        val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
        val visibleItemCount = layoutManager.childCount
        val totalItemCount = layoutManager.itemCount
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        
        return (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
    }

    private fun safeSmoothScrollToPosition(position: Int) {
        val itemCount = adapter.itemCount
        if (itemCount == 0 || position < 0 || position >= itemCount) {
            return
        }
        binding.recyclerView.smoothScrollToPosition(position)
    }

    private inline fun <T> tryOrShowError(crossinline block: () -> T, errorMessage: String) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, errorMessage, e)
            lifecycleScope.launch(Dispatchers.Main) {
                binding.refreshLayout.isRefreshing = false
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(this@LogcatActivity, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRefresh() {
        getLogcat()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
        autoRefreshJob?.cancel()
    }

    fun refreshData() {
        adapter.submitList(logsets.toList())
        if (currentQuery.isEmpty() && currentLevelFilter == null) {
            binding.recyclerView.post {
                safeSmoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }
}
