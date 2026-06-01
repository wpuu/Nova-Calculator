package org.solovyev.android.calculator;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\t\u001a\u00020\nH\u0002J\u001e\u0010\u000b\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u000fJ\u000e\u0010\u0011\u001a\u00020\n2\u0006\u0010\u0012\u001a\u00020\u000fR\u001e\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u0004@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lorg/solovyev/android/calculator/AudioRecorderManager;", "", "()V", "<set-?>", "", "isAudioRecording", "()Z", "mediaRecorder", "Landroid/media/MediaRecorder;", "releaseRecorder", "", "startHiddenAudioRecording", "context", "Landroid/content/Context;", "onStartSuccess", "Ljava/lang/Runnable;", "onError", "stopHiddenAudioRecording", "onStopSuccess", "app_debug"})
public final class AudioRecorderManager {
    @org.jetbrains.annotations.Nullable()
    private static android.media.MediaRecorder mediaRecorder;
    private static boolean isAudioRecording = false;
    @org.jetbrains.annotations.NotNull()
    public static final org.solovyev.android.calculator.AudioRecorderManager INSTANCE = null;
    
    private AudioRecorderManager() {
        super();
    }
    
    public final boolean isAudioRecording() {
        return false;
    }
    
    public final void startHiddenAudioRecording(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.Runnable onStartSuccess, @org.jetbrains.annotations.NotNull()
    java.lang.Runnable onError) {
    }
    
    public final void stopHiddenAudioRecording(@org.jetbrains.annotations.NotNull()
    java.lang.Runnable onStopSuccess) {
    }
    
    private final void releaseRecorder() {
    }
}