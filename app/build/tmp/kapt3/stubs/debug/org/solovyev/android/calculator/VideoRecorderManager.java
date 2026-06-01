package org.solovyev.android.calculator;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J \u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0017J\u001e\u0010\u0018\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u001aJ\u000e\u0010\u001c\u001a\u00020\u00112\u0006\u0010\u001d\u001a\u00020\u001aJ\u001e\u0010\u001e\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u001f\u001a\u00020\u001a2\u0006\u0010 \u001a\u00020\u001aR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u0007\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0006@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0016\u0010\r\u001a\n\u0012\u0004\u0012\u00020\u000f\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006!"}, d2 = {"Lorg/solovyev/android/calculator/VideoRecorderManager;", "", "()V", "imageCapture", "Landroidx/camera/core/ImageCapture;", "<set-?>", "", "isVideoRecording", "()Z", "orientationEventListener", "Landroid/view/OrientationEventListener;", "recording", "Landroidx/camera/video/Recording;", "videoCapture", "Landroidx/camera/video/VideoCapture;", "Landroidx/camera/video/Recorder;", "bindCamera", "", "context", "Landroid/content/Context;", "lifecycleOwner", "Landroidx/lifecycle/LifecycleOwner;", "hiddenPreview", "Landroidx/camera/view/PreviewView;", "startHiddenVideoRecording", "onStartSuccess", "Ljava/lang/Runnable;", "onStopCallback", "stopHiddenVideoRecording", "onStopCommand", "takeHiddenPhoto", "onCaptureInitiated", "onCaptureCompleted", "app_debug"})
public final class VideoRecorderManager {
    @org.jetbrains.annotations.Nullable()
    private static androidx.camera.core.ImageCapture imageCapture;
    @org.jetbrains.annotations.Nullable()
    private static androidx.camera.video.VideoCapture<androidx.camera.video.Recorder> videoCapture;
    @org.jetbrains.annotations.Nullable()
    private static androidx.camera.video.Recording recording;
    @org.jetbrains.annotations.Nullable()
    private static android.view.OrientationEventListener orientationEventListener;
    private static boolean isVideoRecording = false;
    @org.jetbrains.annotations.NotNull()
    public static final org.solovyev.android.calculator.VideoRecorderManager INSTANCE = null;
    
    private VideoRecorderManager() {
        super();
    }
    
    public final boolean isVideoRecording() {
        return false;
    }
    
    public final void bindCamera(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    androidx.lifecycle.LifecycleOwner lifecycleOwner, @org.jetbrains.annotations.Nullable()
    androidx.camera.view.PreviewView hiddenPreview) {
    }
    
    public final void startHiddenVideoRecording(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.Runnable onStartSuccess, @org.jetbrains.annotations.NotNull()
    java.lang.Runnable onStopCallback) {
    }
    
    public final void stopHiddenVideoRecording(@org.jetbrains.annotations.NotNull()
    java.lang.Runnable onStopCommand) {
    }
    
    public final void takeHiddenPhoto(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.Runnable onCaptureInitiated, @org.jetbrains.annotations.NotNull()
    java.lang.Runnable onCaptureCompleted) {
    }
}