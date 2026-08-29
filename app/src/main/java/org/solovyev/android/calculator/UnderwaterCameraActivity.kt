package org.solovyev.android.calculator

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit, visible camera tool optimized for waterproof cases where touch input can be hard.
 *
 * Volume up: photo / long-press burst.
 * Volume down: start/stop video.
 *
 * Camera permission is requested only after the user enters this screen. Microphone permission
 * is requested only when the user starts video and explicitly chooses to record sound.
 */
class UnderwaterCameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var tvVideo: TextView? = null
    private var tvPhoto: TextView? = null
    private var tvScreenOff: TextView? = null
    private var btnExitContainer: View? = null
    private var ivPreview: ImageView? = null
    private var flashOverlay: View? = null
    private var exitAnimator: ValueAnimator? = null

    private var orientationEventListener: OrientationEventListener? = null
    private var currentUIRotation = 0f
    private var cameraStarted = false
    private var pendingVideoAfterMicPermission = false

    private val handler = Handler(Looper.getMainLooper())
    private var isBursting = false
    private val burstRunnable = object : Runnable {
        override fun run() {
            if (isBursting) {
                takePhoto()
                handler.postDelayed(this, 300)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraIfPermitted()
        } else {
            Toast.makeText(
                this,
                "未授予相机权限，水下相机无法使用",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!pendingVideoAfterMicPermission) return@registerForActivityResult
        pendingVideoAfterMicPermission = false
        if (!granted) {
            Toast.makeText(this, "未授予麦克风权限，将进行无声录像", Toast.LENGTH_SHORT).show()
        }
        startVideoRecording(withAudio = granted)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_underwater_camera)

        viewFinder = findViewById(R.id.viewFinder)
        tvVideo = findViewById(R.id.tvVideo)
        tvPhoto = findViewById(R.id.tvPhoto)
        tvScreenOff = findViewById(R.id.tvScreenOff)
        ivPreview = findViewById(R.id.ivPreview)
        flashOverlay = findViewById(R.id.flashOverlay)
        val exitProgressBar = findViewById<ProgressBar>(R.id.exitProgressBar)
        btnExitContainer = findViewById(R.id.btnExitContainer)

        btnExitContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    exitAnimator = ValueAnimator.ofInt(0, 100).apply {
                        duration = 1500
                        addUpdateListener { animator ->
                            exitProgressBar.progress = animator.animatedValue as Int
                            if (exitProgressBar.progress == 100) {
                                finish()
                            }
                        }
                        start()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    exitAnimator?.cancel()
                    exitProgressBar.progress = 0
                }
            }
            true
        }

        setupOrientationListener()
        ensureCameraPermission()
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCameraIfPermitted()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("启用水下相机")
            .setMessage("水下相机需要相机权限才能预览、拍照和录像。只有进入此工具后才会使用相机。")
            .setPositiveButton("继续") { _, _ ->
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("取消") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when (orientation) {
                    in 45..134 -> 270f
                    in 135..224 -> 180f
                    in 225..314 -> 90f
                    else -> 0f
                }

                if (rotation != currentUIRotation) {
                    currentUIRotation = rotation
                    val viewsToRotate = listOf(tvPhoto, tvVideo, tvScreenOff, btnExitContainer)
                    viewsToRotate.forEach { view ->
                        view?.let {
                            ObjectAnimator.ofFloat(it, View.ROTATION, it.rotation, rotation).apply {
                                duration = 300
                                start()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
    }

    override fun onPause() {
        super.onPause()
        orientationEventListener?.disable()
        isBursting = false
        handler.removeCallbacks(burstRunnable)
    }

    override fun onDestroy() {
        exitAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        recording?.close()
        recording = null
        orientationEventListener?.disable()
        super.onDestroy()
    }

    private fun startCameraIfPermitted() {
        if (cameraStarted) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        cameraStarted = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.FHD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    videoCapture
                )
            } catch (exc: Exception) {
                cameraStarted = false
                Toast.makeText(this, "相机启动失败，请确认相机未被其他应用占用", Toast.LENGTH_LONG)
                    .show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!cameraStarted) return true
                if (event?.repeatCount == 0) {
                    takePhoto()
                } else if ((event?.repeatCount ?: 0) > 5 && !isBursting) {
                    isBursting = true
                    handler.post(burstRunnable)
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!cameraStarted) return true
                if (event?.repeatCount == 0) {
                    toggleRecording()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            isBursting = false
            handler.removeCallbacks(burstRunnable)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        flashOverlay?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            animate().alpha(0f).setDuration(200).withEndAction {
                visibility = View.GONE
            }.start()
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UnderwaterCamera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "拍照失败", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { savedUri ->
                        ivPreview?.visibility = View.VISIBLE
                        ivPreview?.setImageURI(savedUri)
                    }
                }
            }
        )
    }

    private fun toggleRecording() {
        val current = recording
        if (current != null) {
            current.stop()
            recording = null
            tvVideo?.text = "录像"
            tvVideo?.setTextColor(Color.WHITE)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startVideoRecording(withAudio = true)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("录像声音")
            .setMessage("录像可以使用麦克风同时录制声音。麦克风只在你主动录像时使用；也可以选择无声录像。")
            .setPositiveButton("允许声音") { _, _ ->
                pendingVideoAfterMicPermission = true
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("无声录像") { _, _ ->
                startVideoRecording(withAudio = false)
            }
            .show()
    }

    private fun startVideoRecording(withAudio: Boolean) {
        val capture = videoCapture ?: return
        if (recording != null) return

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UnderwaterCamera")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var prepared = capture.output.prepareRecording(this, outputOptions)
        if (withAudio && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            prepared = prepared.withAudioEnabled()
        }

        recording = prepared.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    tvVideo?.text = "停止"
                    tvVideo?.setTextColor(Color.RED)
                }
                is VideoRecordEvent.Finalize -> {
                    tvVideo?.text = "录像"
                    tvVideo?.setTextColor(Color.WHITE)
                    if (event.hasError()) {
                        recording?.close()
                    }
                    recording = null
                }
            }
        }
    }
}
