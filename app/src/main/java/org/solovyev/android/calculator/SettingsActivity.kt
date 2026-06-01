package org.solovyev.android.calculator

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.preference.PreferenceManager
import java.io.File
import java.io.FileInputStream

class SettingsActivity : AppCompatActivity() {

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
            if (cameraGranted && audioGranted) {
                Toast.makeText(this, "权限已就绪", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnRequestPermission).setOnClickListener {
            val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) requestPermissionsLauncher.launch(missing.toTypedArray())
            else Toast.makeText(this, "所有权限已开启", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnChangeTheme).setOnClickListener {
            val themes = arrayOf(
                "高端黑金 (金属微雕)", 
                "高端黑金 (圆润微凸)", 
                "高端黑金 (深色微透)", 
                "苹果原生", 
                "经典 Material (原版)"
            )
            val themeValues = arrayOf(
                "premium_theme", 
                "premium_neumorphism_theme", 
                "premium_glass_theme", 
                "ios_theme", 
                "material_theme"
            )
            
            AlertDialog.Builder(this)
                .setTitle("选择隐蔽计算器皮肤")
                .setItems(themes) { _, which ->
                    val selectedTheme = themeValues[which]
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putString("gui.theme", selectedTheme)
                        .apply()
                    Toast.makeText(this, "皮肤已切换为: ${themes[which]}", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .show()
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportEvidenceSafely()
        }

        findViewById<Button>(R.id.btnUnderwaterCamera).setOnClickListener {
            val intent = android.content.Intent(this, UnderwaterCameraActivity::class.java)
            startActivity(intent)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val tvCodePhoto = findViewById<TextView>(R.id.tvCodePhoto)
        val tvCodeVideoStart = findViewById<TextView>(R.id.tvCodeVideoStart)
        val tvCodeVideoStop = findViewById<TextView>(R.id.tvCodeVideoStop)
        val tvCodeAudioStart = findViewById<TextView>(R.id.tvCodeAudioStart)
        val tvCodeAudioStop = findViewById<TextView>(R.id.tvCodeAudioStop)
        val tvCodeSettings = findViewById<TextView>(R.id.tvCodeSettings)

        tvCodePhoto.text = Preferences.Security.secretCodePhoto.getPreference(prefs)
        tvCodeVideoStart.text = Preferences.Security.secretCodeVideoStart.getPreference(prefs)
        tvCodeVideoStop.text = Preferences.Security.secretCodeVideoStop.getPreference(prefs)
        tvCodeAudioStart.text = Preferences.Security.secretCodeAudioStart.getPreference(prefs)
        tvCodeAudioStop.text = Preferences.Security.secretCodeAudioStop.getPreference(prefs)
        tvCodeSettings.text = Preferences.Security.secretCodeSettings.getPreference(prefs)

        val startSetup = { target: String ->
            org.solovyev.android.calculator.Keyboard.setupModeTarget = target
            org.solovyev.android.calculator.Keyboard.instance?.clearEditor()
            Toast.makeText(this, "前往计算器：输入3到4位新指令并按 '=' 确认", Toast.LENGTH_LONG).show()
            finish()
        }

        findViewById<LinearLayout>(R.id.llPhoto).setOnClickListener { startSetup("photo") }
        findViewById<LinearLayout>(R.id.llVideoStart).setOnClickListener { startSetup("video_start") }
        findViewById<LinearLayout>(R.id.llVideoStop).setOnClickListener { startSetup("video_stop") }
        findViewById<LinearLayout>(R.id.llAudioStart).setOnClickListener { startSetup("audio_start") }
        findViewById<LinearLayout>(R.id.llAudioStop).setOnClickListener { startSetup("audio_stop") }
        findViewById<LinearLayout>(R.id.llSettings).setOnClickListener { startSetup("settings") }
    }

    // 核心重构：Android 11+ MediaStore 官方无权限注入
    private fun exportEvidenceSafely() {
        var exportedCount = 0
        try {
            val resolver = contentResolver
            // 获取沙盒里的所有证据文件
            val targetFiles = filesDir.listFiles()?.filter { it.name.endsWith(".dat") } ?: emptyList()

            if (targetFiles.isEmpty()) {
                Toast.makeText(this, "沙盒为空，没有可提取的证据", Toast.LENGTH_SHORT).show()
                return
            }

            for (file in targetFiles) {
                // 判断是照片、录像还是录音
                val isImage = file.name.contains("img")
                val isVideo = file.name.contains("vid")
                val extension = if (isImage) ".jpg" else if (isVideo) ".mp4" else ".m4a"
                val mimeType = if (isImage) "image/jpeg" else if (isVideo) "video/mp4" else "audio/mp4"
                val newFileName = file.name.replace(".dat", extension)

                // 使用 MediaStore 建立传输通道
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    // 强制指定存入系统公共 Download 下的 Vault 文件夹
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VaultExport")
                    }
                }

                // 获取插入系统大门的钥匙 (URI)
                val externalUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val insertUri = resolver.insert(externalUri, contentValues)

                if (insertUri != null) {
                    // 开始导流：把沙盒里的数据通过管道流进公共大门
                    resolver.openOutputStream(insertUri)?.use { outputStream ->
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    exportedCount++
                    // 安全提取后，阅后即焚
                    file.delete()
                }
            }

            Toast.makeText(this, "成功提取 $exportedCount 份证据！\n已存入系统 Download/VaultExport 文件夹", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "提取异常: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}