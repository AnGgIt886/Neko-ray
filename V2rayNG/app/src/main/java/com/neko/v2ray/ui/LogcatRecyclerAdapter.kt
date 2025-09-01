package com.neko.v2ray.ui

import android.content.Context
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.neko.v2ray.R
import com.neko.v2ray.databinding.ItemRecyclerLogcatBinding

class LogcatRecyclerAdapter(private val context: Context) : ListAdapter<ParsedLog, LogcatRecyclerAdapter.MainViewHolder>(DiffCallback()) {

    private var _highlightText: String = ""
    var highlightText: String
        get() = _highlightText
        set(value) {
            _highlightText = value
        }

    private val colorMap = mapOf(
        LogLevel.ERROR to ContextCompat.getColor(context, R.color.log_level_error),
        LogLevel.WARNING to ContextCompat.getColor(context, R.color.log_level_warning),
        LogLevel.INFO to ContextCompat.getColor(context, R.color.log_level_info),
        LogLevel.DEBUG to ContextCompat.getColor(context, R.color.log_level_debug),
        LogLevel.VERBOSE to ContextCompat.getColor(context, R.color.log_level_verbose),
        LogLevel.UNKNOWN to ContextCompat.getColor(context, R.color.log_level_default)
    )

    private val levelIndicatorMap = mapOf(
        LogLevel.ERROR to R.color.log_level_error,
        LogLevel.WARNING to R.color.log_level_warning,
        LogLevel.INFO to R.color.log_level_info,
        LogLevel.DEBUG to R.color.log_level_debug,
        LogLevel.VERBOSE to R.color.log_level_verbose,
        LogLevel.UNKNOWN to R.color.log_level_default
    )

    private class DiffCallback : DiffUtil.ItemCallback<ParsedLog>() {
        override fun areItemsTheSame(oldItem: ParsedLog, newItem: ParsedLog): Boolean {
            return oldItem.original == newItem.original
        }

        override fun areContentsTheSame(oldItem: ParsedLog, newItem: ParsedLog): Boolean {
            return oldItem == newItem
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        try {
            val parsedLog = getItem(position)
            
            holder.itemSubSettingBinding.logTag.text = parsedLog.tag
            
            if (_highlightText.isNotEmpty() && parsedLog.content.contains(_highlightText, true)) {
                val spannable = SpannableString(parsedLog.content)
                val startIndex = parsedLog.content.indexOf(_highlightText, ignoreCase = true)
                if (startIndex != -1) {
                    spannable.setSpan(
                        BackgroundColorSpan(ContextCompat.getColor(context, R.color.log_bg_span)),
                        startIndex,
                        startIndex + _highlightText.length,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                holder.itemSubSettingBinding.logContent.text = spannable
            } else {
                holder.itemSubSettingBinding.logContent.text = parsedLog.content
            }
            
            holder.itemSubSettingBinding.logContent.setTextColor(colorMap[parsedLog.level] ?: ContextCompat.getColor(context, R.color.log_level_default))
            
            val indicatorColor = ContextCompat.getColor(context, 
                levelIndicatorMap[parsedLog.level] ?: R.color.log_level_default)
            holder.itemSubSettingBinding.logLevelIndicator.setBackgroundColor(indicatorColor)
            
        } catch (e: Exception) {
            Log.e("LogcatRecyclerAdapter", "Error binding log view data", e)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerLogcatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    fun updateHighlightText(newText: String) {
        _highlightText = newText
        notifyItemRangeChanged(0, itemCount)
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerLogcatBinding) : 
        RecyclerView.ViewHolder(itemSubSettingBinding.root)
    
    companion object {
        fun parseLog(log: String): ParsedLog {
            if (log.isEmpty()) return ParsedLog("", "", "", LogLevel.UNKNOWN)
            
            val level = detectLogLevel(log)
            
            // Pattern untuk format timestamp Android: "MM-DD HH:MM:SS.millis"
            val timestampPattern = "\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}".toRegex()
            val timestampMatch = timestampPattern.find(log)
            
            var tag = "Unknown"
            var content = log
            
            if (timestampMatch != null) {
                val timestamp = timestampMatch.value
                val remaining = log.substringAfter(timestamp).trim()
                
                // Cari tag setelah timestamp (biasanya sebelum spasi pertama)
                val nextSpaceIndex = remaining.indexOf(' ')
                if (nextSpaceIndex > 0) {
                    tag = remaining.substring(0, nextSpaceIndex).trim()
                    content = remaining.substring(nextSpaceIndex).trim()
                } else {
                    content = remaining
                }
            }
            
            return ParsedLog(log, tag, content, level)
        }
        
        private fun detectLogLevel(log: String): LogLevel {
            // Deteksi level untuk V2Ray/Go logs
            return when {
                log.contains("\\[Error\\]".toRegex(RegexOption.IGNORE_CASE)) ||
                log.contains(" E/".toRegex(RegexOption.IGNORE_CASE)) ||
                log.contains("error", ignoreCase = true) -> LogLevel.ERROR
                
                log.contains("\\[Warn".toRegex(RegexOption.IGNORE_CASE)) ||
                log.contains(" W/".toRegex(RegexOption.IGNORE_CASE)) ||
                log.contains("warning", ignoreCase = true) -> LogLevel.WARNING
                
                log.contains("\\[Info\\]".toRegex(RegexOption.IGNORE_CASE)) ||
                log.contains(" I/".toRegex(RegexOption.IGNORE_CASE)) -> LogLevel.INFO
                
                log.contains("\\[Debug\\]".toRegex(RegexOption.IGNORE_CASE)) ||
                log.contains(" D/".toRegex(RegexOption.IGNORE_CASE)) -> LogLevel.DEBUG
                
                log.contains("\\[Verbose\\]".toRegex(RegexOption.IGNORE_CASE)) ||
                log.contains(" V/".toRegex(RegexOption.IGNORE_CASE)) -> LogLevel.VERBOSE
                
                else -> LogLevel.UNKNOWN
            }
        }
    }
}
