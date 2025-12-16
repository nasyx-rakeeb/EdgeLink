package com.android.system.edgelink.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.system.edgelink.R
import com.android.system.edgelink.service.EdgeLinkService
import com.android.system.edgelink.util.AppLauncher
import com.android.system.edgelink.util.AppPrefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private val appLauncher by lazy { AppLauncher(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_settings)
        prefs = AppPrefs(this)
        setupMasterSwitch()
        setupControls()
    }

    private fun setupMasterSwitch() {
        val toggle = findViewById<Switch>(R.id.master_switch)
        toggle.isChecked = prefs.isEnabled()
        toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.setEnabled(isChecked)
            restartService()
        }
    }

    private fun setupControls() {
        val group = findViewById<RadioGroup>(R.id.position_group)
        if (prefs.getEdgePosition() == 1) findViewById<RadioButton>(R.id.radio_left).isChecked = true
        else findViewById<RadioButton>(R.id.radio_right).isChecked = true

        group.setOnCheckedChangeListener { _, checkedId ->
            prefs.setEdgePosition(if (checkedId == R.id.radio_left) 1 else 0)
            restartService()
        }

        setupSeekBar(R.id.vertical_seekbar, prefs.getVerticalOffset()) { prefs.setVerticalOffset(it) }
        setupSeekBar(R.id.panel_height_seekbar, prefs.getPanelHeightPercent()) { prefs.setPanelHeightPercent(it.coerceAtLeast(20)) }
        setupSeekBar(R.id.handle_opacity_seekbar, prefs.getHandleOpacity()) { prefs.setHandleOpacity(it) }
        setupSeekBar(R.id.panel_opacity_seekbar, prefs.getPanelOpacity()) { prefs.setPanelOpacity(it) }

        val currentWidth = prefs.getHandleWidth()
        setupSeekBar(R.id.width_seekbar, ((currentWidth - 10) * 1.11f).toInt()) {
            prefs.setHandleWidth(10 + (it * 0.9f).toInt())
        }

        val currentHeight = prefs.getHandleHeight()
        setupSeekBar(R.id.height_seekbar, ((currentHeight - 50) / 5.5f).toInt()) {
            prefs.setHandleHeight(50 + (it * 5.5f).toInt())
        }

        // --- NEW: PIN APPS BUTTON ---
        findViewById<Button>(R.id.btn_pin_apps).setOnClickListener {
            showPinAppsDialog()
        }
    }

    private fun showPinAppsDialog() {
        val allApps = appLauncher.getAllLaunchableApps().sortedBy { it.label }
        val pinnedSet = prefs.getPinnedApps()
        
        val appNames = allApps.map { it.label }.toTypedArray()
        val checkedItems = allApps.map { pinnedSet.contains(it.packageName) }.toBooleanArray()
        
        AlertDialog.Builder(this)
            .setTitle("Pin Apps to Top")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                // Update Prefs immediately
                val app = allApps[which]
                prefs.toggleAppPin(app.packageName)
            }
            .setPositiveButton("Done") { _, _ -> 
                // No action needed, prefs updated live
            }
            .show()
    }

    private fun setupSeekBar(id: Int, initialValue: Int, onUpdate: (Int) -> Unit) {
        val seekBar = findViewById<SeekBar>(id)
        val max = seekBar.max
        seekBar.progress = initialValue.coerceIn(0, max)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onUpdate(progress)
                    restartService()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun restartService() {
        val intent = Intent(this, EdgeLinkService::class.java)
        stopService(intent)
        if (prefs.isEnabled()) startService(intent)
    }
}
