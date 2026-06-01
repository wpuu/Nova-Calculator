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

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        hiddenPreview: PreviewView?
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
            } catch (e: Exception) {
                Log.e("CameraX", "相机绑定失败", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startHiddenVideoRecording(
        context: Context,
        onStartSuccess: Runnable,
        onStopCallback: Runnable
    ) {
        val currentVideoCapture = videoCapture
        if (currentVideoCapture == null || recording != null) return

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(context.filesDir, "sys_vid_$timeStamp.dat")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            recording = currentVideoCapture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        recording = null
                        isVideoRecording = false
                        onStopCallback.run()
                    }
                }
            isVideoRecording = true
            onStartSuccess.run()
        }
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
        onCaptureCompleted: Runnable
    ) {
        val currentImageCapture = imageCapture ?: return
        
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
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        currentImageCapture.takePicture(
            outputOptions, 
            ContextCompat.getMainExecutor(context), 
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                    } catch (e: Exception) {}
                    onCaptureCompleted.run()
                }

                override fun onError(exc: ImageCaptureException) {
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                    } catch (e: Exception) {}
                }
            }
        )
    }
}
