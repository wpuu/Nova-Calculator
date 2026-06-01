package org.solovyev.android.calculator

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import android.view.View
import android.view.MotionEvent
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.OrientationEventListener
import android.animation.ObjectAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
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
import java.io.File
import androidx.camera.video.MediaStoreOutputOptions

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

    // 连拍控制
    private val handler = Handler(Looper.getMainLooper())
    private var isBursting = false
    private val burstRunnable = object : Runnable {
        override fun run() {
            if (isBursting) {
                takePhoto()
                handler.postDelayed(this, 300) // 300ms 一张
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 息屏/锁屏处理：允许在锁屏上方显示，并唤醒屏幕
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
        
        // 保持屏幕常亮
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
        startCamera()
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                val rotation = when (orientation) {
                    in 45..134 -> 270f // Reverse Landscape
                    in 135..224 -> 180f // Reverse Portrait
                    in 225..314 -> 90f // Landscape
                    else -> 0f // Portrait
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
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
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

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event?.repeatCount == 0) {
                    // 单按，如果后续没有被长按覆盖，就拍一张。这里为了简单，直接拍一张。
                    takePhoto()
                } else if (event?.repeatCount ?: 0 > 5 && !isBursting) {
                    // 长按触发连拍
                    isBursting = true
                    handler.post(burstRunnable)
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
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
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
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
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UnderwaterCamera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    // Toast.makeText(baseContext, "拍照失败", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    if (savedUri != null) {
                        runOnUiThread {
                            ivPreview?.visibility = View.VISIBLE
                            ivPreview?.setImageURI(savedUri)
                        }
                    }
                    // Toast.makeText(baseContext, "拍照成功", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun toggleRecording() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            tvVideo?.text = "录像"
            tvVideo?.setTextColor(Color.WHITE)
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UnderwaterCamera")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        this@UnderwaterCameraActivity,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        tvVideo?.text = "停止"
                        tvVideo?.setTextColor(Color.RED)
                    }
                    is VideoRecordEvent.Finalize -> {
                        tvVideo?.text = "录像"
                        tvVideo?.setTextColor(Color.WHITE)
                        if (!recordEvent.hasError()) {
                            // Toast.makeText(baseContext, "录像保存成功", Toast.LENGTH_SHORT).show()
                        } else {
                            recording?.close()
                            recording = null
                        }
                    }
                }
            }
    }
}
