package org.solovyev.android.calculator

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.solovyev.android.calculator.autoclicker.AutoClickerPlatform
import org.solovyev.android.calculator.autoclicker.AutoClickerService

/**
 * Explicit commercial tools page.
 *
 * Hidden capture controls and evidence export are intentionally absent from the
 * commercial product line. Camera/microphone permissions are requested only inside the
 * visible Underwater Camera workflow when the user chooses that tool.
 */
class SettingsActivity : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private var updatingAutoClickerSwitch = false

    private fun refreshAutoClickerSwitch() {
        val switchEnabled = findViewById<Switch>(R.id.switchAutoClickerEnabled) ?: return
        if (!AutoClickerPlatform.isSupportedSdk(Build.VERSION.SDK_INT)) {
            if (switchEnabled.isChecked) {
                updatingAutoClickerSwitch = true
                switchEnabled.isChecked = false
                updatingAutoClickerSwitch = false
            }
            return
        }
        val intent = Preferences.AutoClicker.intent.getPreference(prefs)
        if (switchEnabled.isChecked == intent) return
        updatingAutoClickerSwitch = true
        switchEnabled.isChecked = intent
        updatingAutoClickerSwitch = false
    }

    private val autoClickerPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Preferences.AutoClicker.enabled.getKey() ||
                key == Preferences.AutoClicker.intent.getKey()) {
                runOnUiThread { refreshAutoClickerSwitch() }
            }
        }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(autoClickerPreferenceListener)
        refreshAutoClickerSwitch()
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(autoClickerPreferenceListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshAutoClickerSwitch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnChangeTheme).setOnClickListener {
            val themes = arrayOf(
                "高端黑金（金属微雕）",
                "高端黑金（圆润微凸）",
                "高端黑金（深色微透）",
                "苹果风格",
                "经典 Material"
            )
            val themeValues = arrayOf(
                "premium_theme",
                "premium_neumorphism_theme",
                "premium_glass_theme",
                "ios_theme",
                "material_theme"
            )

            AlertDialog.Builder(this)
                .setTitle("选择计算器主题")
                .setItems(themes) { _, which ->
                    prefs.edit()
                        .putString("gui.theme", themeValues[which])
                        .apply()
                    Toast.makeText(this, "主题已切换为：${themes[which]}", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .show()
        }

        findViewById<Button>(R.id.btnUnderwaterCamera).setOnClickListener {
            startActivity(android.content.Intent(this, UnderwaterCameraActivity::class.java))
        }

        val autoClickerSupported = AutoClickerPlatform.isSupportedSdk(Build.VERSION.SDK_INT)
        val switchEnabled = findViewById<Switch>(R.id.switchAutoClickerEnabled)
        if (!autoClickerSupported) {
            prefs.edit()
                .putBoolean(Preferences.AutoClicker.intent.getKey(), false)
                .putBoolean(Preferences.AutoClicker.enabled.getKey(), false)
                .apply()
        }
        switchEnabled.isChecked = autoClickerSupported &&
            Preferences.AutoClicker.intent.getPreference(prefs)
        switchEnabled.isEnabled = autoClickerSupported
        if (!autoClickerSupported) {
            switchEnabled.text = "Android 7.0+"
        }
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (updatingAutoClickerSwitch || !autoClickerSupported) {
                return@setOnCheckedChangeListener
            }
            prefs.edit()
                .putBoolean(Preferences.AutoClicker.intent.getKey(), isChecked)
                .apply()
            if (isChecked && !AutoClickerService.isAccessibilityEnabled(this)) {
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }

        val etInterval = findViewById<EditText>(R.id.etClickInterval)
        val etDuration = findViewById<EditText>(R.id.etClickDuration)
        val saveAutoClicker = findViewById<Button>(R.id.btnSaveAutoClickerParams)
        etInterval.isEnabled = autoClickerSupported
        etDuration.isEnabled = autoClickerSupported
        saveAutoClicker.isEnabled = autoClickerSupported

        val savedInterval = Preferences.AutoClicker.interval.getPreference(prefs)
        val savedDuration = Preferences.AutoClicker.duration.getPreference(prefs)
        etInterval.setText(
            try {
                if (savedInterval.toLong() in 40..5000) savedInterval else "40"
            } catch (_: Exception) {
                "40"
            }
        )
        etDuration.setText(
            try {
                if (savedDuration.toLong() in 5..3600) savedDuration else "60"
            } catch (_: Exception) {
                "60"
            }
        )

        saveAutoClicker.setOnClickListener {
            if (!autoClickerSupported) {
                return@setOnClickListener
            }
            val rawInterval = etInterval.text.toString().trim()
            val rawDuration = etDuration.text.toString().trim()

            val finalInterval = try {
                val v = rawInterval.toLong()
                when {
                    v < 40 -> "40"
                    v > 5000 -> "5000"
                    else -> rawInterval
                }
            } catch (_: Exception) {
                "40"
            }

            val finalDuration = try {
                val v = rawDuration.toLong()
                when {
                    v < 5 -> "5"
                    v > 3600 -> "3600"
                    else -> rawDuration
                }
            } catch (_: Exception) {
                "60"
            }

            prefs.edit()
                .putString(Preferences.AutoClicker.interval.getKey(), finalInterval)
                .putString(Preferences.AutoClicker.duration.getKey(), finalDuration)
                .apply()

            etInterval.setText(finalInterval)
            etDuration.setText(finalDuration)

            Toast.makeText(
                this,
                "连点参数已保存：间隔 ${finalInterval}ms，时长 ${finalDuration} 秒",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
