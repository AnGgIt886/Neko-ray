package com.neko.v2ray.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.neko.v2ray.AppConfig
import com.neko.v2ray.R
import com.neko.v2ray.databinding.ActivityRoutingSettingBinding
import com.neko.v2ray.dto.RulesetItem
import com.neko.v2ray.extension.toast
import com.neko.v2ray.util.MmkvManager
import com.neko.v2ray.util.MmkvManager.settingsStorage
import com.neko.v2ray.util.SettingsManager
import com.neko.v2ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutingSettingActivity : BaseActivity() {
    private val binding by lazy { ActivityRoutingSettingBinding.inflate(layoutInflater) }

    var rulesets: MutableList<RulesetItem> = mutableListOf()
    private val adapter by lazy { RoutingSettingRecyclerAdapter(this) }
    private val routing_domain_strategy: Array<out String> by lazy {
        resources.getStringArray(R.array.routing_domain_strategy)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.routing_settings_title)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val found = Utils.arrayFind(routing_domain_strategy, settingsStorage?.decodeString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: "")
        found.let { binding.spDomainStrategy.setSelection(if (it >= 0) it else 0) }
        binding.spDomainStrategy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsStorage.encode(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, routing_domain_strategy[position])
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_routing_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_rule -> {
            startActivity(Intent(this, RoutingEditActivity::class.java))
            true
        }

        R.id.user_asset_setting -> {
            startActivity(Intent(this, UserAssetActivity::class.java))
            true
        }

        R.id.import_rulesets -> {
            AlertDialog.Builder(this).setMessage(R.string.routing_settings_import_rulesets_tip)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        SettingsManager.resetRoutingRulesets(this@RoutingSettingActivity)
                        launch(Dispatchers.Main) {
                            refreshData()
                            toast(R.string.toast_success)
                        }
                    }
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    //do noting
                }
                .show()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun refreshData() {
        rulesets = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
        adapter.notifyDataSetChanged()
    }
}
