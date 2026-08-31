package org.solovyev.android.calculator

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
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

    companion object {
        /**
         * Bump this key when the AccessibilityService purpose/data handling changes so existing
         * users must see and affirm the new disclosure before AutoTap can be armed again.
         */
        private const val PREF_AUTOTAP_ACCESSIBILITY_DISCLOSURE_V1 =
            "nova.autotap.accessibility_disclosure.v1.accepted"
    }

    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private var updatingAutoClickerSwitch = false

    private fun refreshAutoClickerSwitch() {
        val switchEnabled = findViewById<Switch>(R.id.switchAutoClickerEnabled) ?: return
        if (!AutoClickerPlatform.isSupportedSdk(Build.VERSION.SDK_INT)) {
            if (switchEnabled.isChecked) {
                setAutoClickerSwitchChecked(switchEnabled, false)
            }
            return
        }
        val intent = Preferences.AutoClicker.intent.getPreference(prefs)
        if (switchEnabled.isChecked == intent) return
        setAutoClickerSwitchChecked(switchEnabled, intent)
    }

    private fun setAutoClickerSwitchChecked(switchEnabled: Switch, checked: Boolean) {
        if (switchEnabled.isChecked == checked) return
        updatingAutoClickerSwitch = true
        switchEnabled.isChecked = checked
        updatingAutoClickerSwitch = false
    }

    private fun setAutoClickerIntent(enabled: Boolean) {
        prefs.edit()
            .putBoolean(Preferences.AutoClicker.intent.getKey(), enabled)
            .apply()
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "无法打开系统无障碍设置，请在系统设置中手动打开。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openPrivacyPolicy() {
        val gatewayUrl = BuildConfig.NOVA_AI_GATEWAY_URL.trim()
        val privacyUri = try {
            val gateway = Uri.parse(gatewayUrl)
            if (gateway.scheme != "https" || gateway.host.isNullOrBlank()) {
                null
            } else {
                gateway.buildUpon()
                    .encodedPath("/api/privacy")
                    .clearQuery()
                    .fragment(null)
                    .build()
            }
        } catch (_: Exception) {
            null
        }

        if (privacyUri == null) {
            Toast.makeText(this, "隐私政策将在正式服务配置后提供", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, privacyUri))
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开隐私政策页面", Toast.LENGTH_LONG).show()
        }
    }

    private fun showOpenSourceLicenses() {
        val notice = readLegalAsset("legal/NOTICE.txt")
        val license = readLegalAsset("legal/LICENSE.txt")
        if (notice == null || license == null) {
            Toast.makeText(this, "开源许可文件不可用", Toast.LENGTH_LONG).show()
            return
        }

        val content = TextView(this).apply {
            text = "$notice\n\n$license"
            textSize = 12f
            setTextIsSelectable(true)
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(this).apply {
            addView(content)
        }
        AlertDialog.Builder(this)
            .setTitle("开源许可")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun readLegalAsset(path: String): String? {
        return try {
            assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Google Play prominent-disclosure flow for a non-accessibility-tool use of AccessibilityService.
     * No user intent is persisted and no system permission screen is opened until the user takes the
     * affirmative "同意并继续" action.
     */
    private fun requestAutoClickerEnable(switchEnabled: Switch) {
        if (prefs.getBoolean(PREF_AUTOTAP_ACCESSIBILITY_DISCLOSURE_V1, false)) {
            setAutoClickerIntent(true)
            if (!AutoClickerService.isAccessibilityEnabled(this)) {
                openAccessibilitySettings()
            }
            return
        }

        // The switch is only a request to start the consent flow. Keep the feature visibly off
        // until the user has affirmatively accepted the separate AccessibilityService disclosure.
        setAutoClickerSwitchChecked(switchEnabled, false)
        setAutoClickerIntent(false)

        val dialog = AlertDialog.Builder(this)
            .setTitle("连点辅助需要无障碍服务")
            .setMessage(
                "Nova 的连点辅助仅在你明确开启后使用 Android 无障碍服务。\n\n" +
                    "服务会：\n" +
                    "• 接收窗口变化事件，用于在全屏或横竖屏切换后恢复两个悬浮点击标记；\n" +
                    "• 监听音量+和音量-，作为你主动开始/停止连点的控制键；\n" +
                    "• 按你拖动设置的两个屏幕坐标发送点击手势。\n\n" +
                    "Nova 的无障碍服务不能读取窗口内容（canRetrieveWindowContent=false），" +
                    "不会读取屏幕文字、输入内容或账号信息，也不会通过无障碍服务收集、" +
                    "上传或分享个人数据。AI 不会使用无障碍服务自主决定点击位置或执行操作。\n\n" +
                    "选择“同意并继续”后才会打开系统无障碍设置；你仍需在系统中手动授权，" +
                    "并可随时在系统设置或本页关闭连点辅助。"
            )
            .setPositiveButton("同意并继续") { _, _ ->
                prefs.edit()
                    .putBoolean(PREF_AUTOTAP_ACCESSIBILITY_DISCLOSURE_V1, true)
                    .putBoolean(Preferences.AutoClicker.intent.getKey(), true)
                    .apply()
                setAutoClickerSwitchChecked(switchEnabled, true)
                if (!AutoClickerService.isAccessibilityEnabled(this)) {
                    openAccessibilitySettings()
                }
            }
            .setNegativeButton("不同意") { _, _ ->
                setAutoClickerIntent(false)
                setAutoClickerSwitchChecked(switchEnabled, false)
            }
            .create()

        dialog.setOnCancelListener {
            setAutoClickerIntent(false)
            setAutoClickerSwitchChecked(switchEnabled, false)
        }
        dialog.show()
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
            startActivity(Intent(this, UnderwaterCameraActivity::class.java))
        }
        findViewById<Button>(R.id.btnPrivacyPolicy).setOnClickListener {
            openPrivacyPolicy()
        }
        findViewById<Button>(R.id.btnOpenSourceLicenses).setOnClickListener {
            showOpenSourceLicenses()
        }

        val autoClickerSupported = AutoClickerPlatform.isSupportedSdk(Build.VERSION.SDK_INT)
        val switchEnabled = findViewById<Switch>(R.id.switchAutoClickerEnabled)
        if (!autoClickerSupported) {
            prefs.edit()
                .putBoolean(Preferences.AutoClicker.intent.getKey(), false)
                .putBoolean(Preferences.AutoClicker.enabled.getKey(), false)
                .apply()
        } else if (!prefs.getBoolean(PREF_AUTOTAP_ACCESSIBILITY_DISCLOSURE_V1, false)
            && Preferences.AutoClicker.intent.getPreference(prefs)) {
            // Commercial upgrades from an older build must not inherit a pre-disclosure armed state.
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
            if (!isChecked) {
                setAutoClickerIntent(false)
                return@setOnCheckedChangeListener
            }
            requestAutoClickerEnable(switchEnabled)
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
