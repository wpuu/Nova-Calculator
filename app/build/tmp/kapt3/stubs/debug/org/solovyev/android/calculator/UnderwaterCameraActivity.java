package org.solovyev.android.calculator;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000~\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010!\u001a\u00020\"2\b\u0010#\u001a\u0004\u0018\u00010$H\u0014J\u001a\u0010%\u001a\u00020\u00112\u0006\u0010&\u001a\u00020\'2\b\u0010(\u001a\u0004\u0018\u00010)H\u0016J\u001a\u0010*\u001a\u00020\u00112\u0006\u0010&\u001a\u00020\'2\b\u0010(\u001a\u0004\u0018\u00010)H\u0016J\b\u0010+\u001a\u00020\"H\u0014J\b\u0010,\u001a\u00020\"H\u0014J\u0010\u0010-\u001a\u00020\"2\u0006\u0010.\u001a\u00020\u0011H\u0016J\b\u0010/\u001a\u00020\"H\u0002J\b\u00100\u001a\u00020\"H\u0002J\b\u00101\u001a\u00020\"H\u0002J\b\u00102\u001a\u00020\"H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0012\u001a\u0004\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0018\u001a\u0004\u0018\u00010\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001b\u001a\u0004\u0018\u00010\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u001c\u001a\n\u0012\u0004\u0012\u00020\u001e\u0018\u00010\u001dX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001f\u001a\u00020 X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u00063"}, d2 = {"Lorg/solovyev/android/calculator/UnderwaterCameraActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "btnExitContainer", "Landroid/view/View;", "burstRunnable", "Ljava/lang/Runnable;", "currentUIRotation", "", "exitAnimator", "Landroid/animation/ValueAnimator;", "flashOverlay", "handler", "Landroid/os/Handler;", "imageCapture", "Landroidx/camera/core/ImageCapture;", "isBursting", "", "ivPreview", "Landroid/widget/ImageView;", "orientationEventListener", "Landroid/view/OrientationEventListener;", "recording", "Landroidx/camera/video/Recording;", "tvPhoto", "Landroid/widget/TextView;", "tvScreenOff", "tvVideo", "videoCapture", "Landroidx/camera/video/VideoCapture;", "Landroidx/camera/video/Recorder;", "viewFinder", "Landroidx/camera/view/PreviewView;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "onKeyDown", "keyCode", "", "event", "Landroid/view/KeyEvent;", "onKeyUp", "onPause", "onResume", "onWindowFocusChanged", "hasFocus", "setupOrientationListener", "startCamera", "takePhoto", "toggleRecording", "app_debug"})
public final class UnderwaterCameraActivity extends androidx.appcompat.app.AppCompatActivity {
    private androidx.camera.view.PreviewView viewFinder;
    @org.jetbrains.annotations.Nullable()
    private androidx.camera.core.ImageCapture imageCapture;
    @org.jetbrains.annotations.Nullable()
    private androidx.camera.video.VideoCapture<androidx.camera.video.Recorder> videoCapture;
    @org.jetbrains.annotations.Nullable()
    private androidx.camera.video.Recording recording;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvVideo;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvPhoto;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView tvScreenOff;
    @org.jetbrains.annotations.Nullable()
    private android.view.View btnExitContainer;
    @org.jetbrains.annotations.Nullable()
    private android.widget.ImageView ivPreview;
    @org.jetbrains.annotations.Nullable()
    private android.view.View flashOverlay;
    @org.jetbrains.annotations.Nullable()
    private android.animation.ValueAnimator exitAnimator;
    @org.jetbrains.annotations.Nullable()
    private android.view.OrientationEventListener orientationEventListener;
    private float currentUIRotation = 0.0F;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    private boolean isBursting = false;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable burstRunnable = null;
    
    public UnderwaterCameraActivity() {
        super();
    }
    
    @java.lang.Override()
    public void onWindowFocusChanged(boolean hasFocus) {
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void setupOrientationListener() {
    }
    
    @java.lang.Override()
    protected void onResume() {
    }
    
    @java.lang.Override()
    protected void onPause() {
    }
    
    private final void startCamera() {
    }
    
    @java.lang.Override()
    public boolean onKeyDown(int keyCode, @org.jetbrains.annotations.Nullable()
    android.view.KeyEvent event) {
        return false;
    }
    
    @java.lang.Override()
    public boolean onKeyUp(int keyCode, @org.jetbrains.annotations.Nullable()
    android.view.KeyEvent event) {
        return false;
    }
    
    private final void takePhoto() {
    }
    
    private final void toggleRecording() {
    }
}