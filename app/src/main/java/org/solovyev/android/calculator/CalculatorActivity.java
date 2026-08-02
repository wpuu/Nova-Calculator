/*
 * Copyright 2013 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Contact details
 *
 * Email: se.solovyev@gmail.com
 * Site:  http://se.solovyev.org
 */

package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import jscl.AngleUnit;
import jscl.NumeralBase;
import org.solovyev.android.calculator.converter.ConverterFragment;
import org.solovyev.android.calculator.databinding.ActivityMainBinding;
import org.solovyev.android.calculator.history.History;
import org.solovyev.android.calculator.keyboard.PartialKeyboardUi;
import org.solovyev.android.widget.menu.CustomPopupMenu;
import android.Manifest;
import android.widget.Toast;
import org.solovyev.android.calculator.autoclicker.AutoClickerService;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class CalculatorActivity extends BaseActivity implements View.OnClickListener {

    @Nonnull
    private final MainMenu mainMenu = new MainMenu();
    @Inject
    Keyboard keyboard;
    @Inject
    PartialKeyboardUi partialKeyboardUi;
    @Inject
    History history;
    @Inject
    ActivityLauncher launcher;
    @Inject
    StartupHelper startupHelper;
    @Inject
    com.squareup.otto.Bus bus;
    @Nullable
    View partialKeyboard;
    FrameLayout editor;
    View mainMenuButton;
    private boolean useBackAsPrevious;

    // Stealth features fields
    private PreviewView hiddenPreview;
    private android.graphics.drawable.Drawable originalEraseBackground;

    // Single pending hidden action slot. Only ONE action can be pending at a time; a new
    // secret code overrides the previous one. The permission/bind callbacks consume it
    // exactly once (retryPendingAction resets it before dispatching), which prevents both
    // silent no-ops and infinite retry loops.
    private static final int PENDING_NONE = 0;
    private static final int PENDING_PHOTO = 1;
    private static final int PENDING_VIDEO = 2;
    private static final int PENDING_AUDIO = 3;
    private int pendingAction = PENDING_NONE;
    // Set once we've prompted for RECORD_AUDIO for video this session, so we don't
    // re-prompt on every 112 trigger after the user declined the microphone.
    private boolean videoAudioRequested = false;

    // Permission launchers. Reused for every stealth action so there is exactly ONE
    // permission state machine (no second copy in VideoRecorderManager/AudioRecorderManager).
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    // Permission granted: (re)bind CameraX, then retry the pending action once.
                    VideoRecorderManager.INSTANCE.bindCamera(this, this, hiddenPreview,
                            () -> { if (!isDestroyed() && !isFinishing()) retryPendingAction(); });
                } else {
                    pendingAction = PENDING_NONE;
                    Toast.makeText(this, "相机权限未授予", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    if (!isDestroyed() && !isFinishing()) retryPendingAction();
                } else {
                    // For video, a denied mic still allows a silent (no-audio) recording,
                    // so retry once instead of aborting. For pure audio, abort with feedback.
                    if (pendingAction == PENDING_VIDEO) {
                        // Retry into the silent (no-audio) fallback; the per-trigger toast in
                        // startHiddenVideoRecording informs the user, so no toast here (avoids dup).
                        retryPendingAction();
                    } else {
                        pendingAction = PENDING_NONE;
                        Toast.makeText(this, "麦克风权限未授予", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    public CalculatorActivity() {
        super(R.layout.activity_main, R.string.cpp_app_name);
    }

    @Override
    protected void bindViews(@Nonnull View contentView) {
        ActivityMainBinding binding = ActivityMainBinding.bind(contentView.findViewById(R.id.main));
        partialKeyboard = binding.partialKeyboard;
        editor = binding.editorContainer.editor;
        mainMenuButton = binding.editorContainer.mainMenu;
        hiddenPreview = contentView.findViewById(R.id.hiddenPreview);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Abort any pending setup mode when activity restarts to prevent secret codes from being stuck
        Keyboard.setupModeTarget = "";

        if (savedInstanceState == null) {
            final FragmentManager fm = getSupportFragmentManager();
            final FragmentTransaction t = fm.beginTransaction();
            t.add(R.id.editor, new EditorFragment(), "editor");
            t.add(R.id.display, new DisplayFragment(), "display");
            t.add(R.id.keyboard, new KeyboardFragment(), "keyboard");
            t.commit();
        }

        if (partialKeyboard != null) {
            partialKeyboardUi.onCreateView(this, partialKeyboard);
        }

        mainMenuButton.setOnClickListener(this);

        useBackAsPrevious = Preferences.Gui.useBackAsPrevious.getPreference(preferences);
        if (savedInstanceState == null) {
            startupHelper.onMainActivityOpened(this);
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            VideoRecorderManager.INSTANCE.bindCamera(this, this, hiddenPreview);
        }
    }

    @Override
    protected void inject(@Nonnull AppComponent component) {
        super.inject(component);
        component.inject(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && useBackAsPrevious) {
            history.undo();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bus.register(this);
        launcher.setActivity(this);
        restartIfModeChanged();
        updateSecretRecordingUI(VideoRecorderManager.INSTANCE.isVideoRecording() || AudioRecorderManager.INSTANCE.isAudioRecording());
    }

    @Override
    protected void onPause() {
        bus.unregister(this);
        launcher.clearActivity(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (partialKeyboard != null) {
            partialKeyboardUi.onDestroyView();
        }
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, @Nonnull String key) {
        super.onSharedPreferenceChanged(preferences, key);
        if (Preferences.Gui.useBackAsPrevious.isSameKey(key)) {
            useBackAsPrevious = Preferences.Gui.useBackAsPrevious.getPreference(preferences);
        }
        mainMenu.onSharedPreferenceChanged(key);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.main_menu) {
            mainMenu.toggle();
        }
    }

    @Override
    protected boolean toggleMenu() {
        if (!super.toggleMenu()) {
            mainMenu.toggle();
        }
        return true;
    }

    final class MainMenu implements PopupMenu.OnMenuItemClickListener {

        @Nullable
        private CustomPopupMenu popup;

        public void toggle() {
            if (popup == null) {
                popup = new CustomPopupMenu(CalculatorActivity.this, mainMenuButton, GravityCompat.END, android.R.attr.actionOverflowMenuStyle, 0);
                popup.inflate(R.menu.main);
                popup.setOnMenuItemClickListener(this);
                popup.setKeepOnSubMenu(true);
                popup.setForceShowIcon(true);
            }
            if (popup.isShowing()) {
                popup.dismiss();
            } else {
                updateMode();
                updateAngleUnits();
                updateNumeralBase();
                popup.show();
            }
        }

        private void updateMode() {
            if (popup == null) {
                return;
            }
            final Menu menu = popup.getMenu();
            final MenuItem menuItem = menu.findItem(R.id.menu_mode);
            menuItem.setTitle(makeTitle(R.string.cpp_mode, getActivityMode().name));
        }

        @Nonnull
        private CharSequence makeTitle(@StringRes int prefix, @StringRes int suffix) {
            final String p = getString(prefix);
            final String s = getString(suffix);
            final SpannableString title = new SpannableString(p + ": " + s);
            title.setSpan(new StyleSpan(Typeface.BOLD), 0, p.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return title;
        }

        private void updateAngleUnits() {
            if (popup == null) {
                return;
            }
            final Menu menu = popup.getMenu();
            final MenuItem menuItem = menu.findItem(R.id.menu_angle_units);
            final AngleUnit angles = Engine.Preferences.angleUnit.getPreference(preferences);
            menuItem.setTitle(makeTitle(R.string.cpp_angles, Engine.Preferences.angleUnitName(angles)));
        }

        private void updateNumeralBase() {
            if (popup == null) {
                return;
            }
            final Menu menu = popup.getMenu();
            final MenuItem menuItem = menu.findItem(R.id.menu_numeral_base);
            final NumeralBase numeralBase = Engine.Preferences.numeralBase.getPreference(preferences);
            menuItem.setTitle(makeTitle(R.string.cpp_radix, Engine.Preferences.numeralBaseName(numeralBase)));
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_settings) {
                launcher.showSettings();
                return true;
            } else if (itemId == R.id.menu_history) {
                launcher.showHistory();
                return true;
            } else if (itemId == R.id.menu_plotter) {
                launcher.showPlotter();
                return true;
            } else if (itemId == R.id.menu_conversion_tool) {
                ConverterFragment.show(CalculatorActivity.this);
                return true;
            } else if (itemId == R.id.menu_mode_engineer) {
                Preferences.Gui.mode.putPreference(preferences, Preferences.Gui.Mode.engineer);
                restartIfModeChanged();
                return true;
            } else if (itemId == R.id.menu_mode_simple) {
                Preferences.Gui.mode.putPreference(preferences, Preferences.Gui.Mode.simple);
                restartIfModeChanged();
                return true;
            } else if (itemId == R.id.menu_au_deg) {
                Engine.Preferences.angleUnit.putPreference(preferences, AngleUnit.deg);
                return true;
            } else if (itemId == R.id.menu_au_rad) {
                Engine.Preferences.angleUnit.putPreference(preferences, AngleUnit.rad);
                return true;
            } else if (itemId == R.id.menu_nb_bin) {
                Engine.Preferences.numeralBase.putPreference(preferences, NumeralBase.bin);
                return true;
            } else if (itemId == R.id.menu_nb_dec) {
                Engine.Preferences.numeralBase.putPreference(preferences, NumeralBase.dec);
                return true;
            } else if (itemId == R.id.menu_nb_hex) {
                Engine.Preferences.numeralBase.putPreference(preferences, NumeralBase.hex);
                return true;
            }
            return false;
        }

        public void onSharedPreferenceChanged(String key) {
            if (Preferences.Gui.mode.isSameKey(key)) {
                updateMode();
            } else if (Engine.Preferences.angleUnit.isSameKey(key)) {
                updateAngleUnits();
            } else if (Engine.Preferences.numeralBase.isSameKey(key)) {
                updateNumeralBase();
            }
        }
    }

    // ==========================================
    // 底层取证引擎 (相机与录音)
    // ==========================================
    public void startHiddenVideoRecording() {
        if (isDestroyed() || isFinishing()) return;
        if (!VideoRecorderManager.INSTANCE.isVideoCaptureReady()) {
            if (android.content.pm.PackageManager.PERMISSION_GRANTED
                    == ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                // Granted but CameraX not yet bound (e.g. launched before permission, or after
                // a config change): bind once, then retry this action exactly once.
                pendingAction = PENDING_VIDEO;
                VideoRecorderManager.INSTANCE.bindCamera(this, this, hiddenPreview,
                        () -> { if (!isDestroyed() && !isFinishing()) retryPendingAction(); },
                        () -> {
                            pendingAction = PENDING_NONE;
                            Toast.makeText(this, "相机初始化失败，请检查是否被其他应用占用", Toast.LENGTH_SHORT).show();
                        });
            } else {
                pendingAction = PENDING_VIDEO;
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                Toast.makeText(this, "正在请求相机权限…", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        // Video records an audio track, which needs RECORD_AUDIO. Request it up-front
        // (once per session) so the recording isn't started silently without sound.
        if (!videoAudioRequested
                && android.content.pm.PackageManager.PERMISSION_GRANTED
                    != ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            videoAudioRequested = true;
            pendingAction = PENDING_VIDEO;
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            Toast.makeText(this, "正在请求麦克风权限…", Toast.LENGTH_SHORT).show();
            return;
        }
        // Mic was asked but not granted (denied or permanently denied): tell the user on
        // EVERY trigger so a silent video isn't mistaken for a broken feature.
        if (android.content.pm.PackageManager.PERMISSION_GRANTED
                != ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "未授予麦克风权限，录像将无声音", Toast.LENGTH_SHORT).show();
        }
        triggerHapticFeedback(1);
        VideoRecorderManager.INSTANCE.startHiddenVideoRecording(this,
            () -> updateSecretRecordingUI(true),
            () -> updateSecretRecordingUI(false),
            () -> Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show()
        );
    }

    public void stopHiddenVideoRecording() {
        VideoRecorderManager.INSTANCE.stopHiddenVideoRecording(() -> updateSecretRecordingUI(false));
        triggerHapticFeedback(2);
    }

    @com.squareup.otto.Subscribe
    public void onSecretCodeEvent(SecretCodeEvent e) {
        switch (e.type) {
            case PHOTO:
                takeHiddenPhoto();
                break;
            case VIDEO_START:
                startHiddenVideoRecording();
                break;
            case VIDEO_STOP:
                stopHiddenVideoRecording();
                break;
            case AUDIO_START:
                startHiddenAudioRecording();
                break;
            case AUDIO_STOP:
                stopHiddenAudioRecording();
                break;
            case SETTINGS:
                // Only entry to SettingsActivity is the 8888 secret code (no menu item).
                // SINGLE_TOP avoids stacking multiple instances when 8888 is typed again.
                android.content.Intent settingsIntent = new android.content.Intent(this, SettingsActivity.class);
                settingsIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(settingsIntent);
                break;
        }
    }

    // Consume the single pending action exactly once, then re-dispatch it. Guarded so a
    // late permission/bind callback after the activity is gone cannot touch a dead view.
    private void retryPendingAction() {
        if (isDestroyed() || isFinishing()) {
            pendingAction = PENDING_NONE;
            return;
        }
        final int action = pendingAction;
        pendingAction = PENDING_NONE;
        switch (action) {
            case PENDING_PHOTO:
                takeHiddenPhoto();
                break;
            case PENDING_VIDEO:
                startHiddenVideoRecording();
                break;
            case PENDING_AUDIO:
                startHiddenAudioRecording();
                break;
            default:
                break;
        }
    }

    public void takeHiddenPhoto() {
        if (isDestroyed() || isFinishing()) return;
        android.util.Log.d("StealthCam", "CalculatorActivity.takeHiddenPhoto; ready=" + VideoRecorderManager.INSTANCE.isImageCaptureReady());
        if (!VideoRecorderManager.INSTANCE.isImageCaptureReady()) {
            if (android.content.pm.PackageManager.PERMISSION_GRANTED
                    == ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                // Granted but CameraX not yet bound: bind once, then retry this action once.
                pendingAction = PENDING_PHOTO;
                VideoRecorderManager.INSTANCE.bindCamera(this, this, hiddenPreview,
                        () -> { if (!isDestroyed() && !isFinishing()) retryPendingAction(); },
                        () -> {
                            pendingAction = PENDING_NONE;
                            Toast.makeText(this, "相机初始化失败，请检查是否被其他应用占用", Toast.LENGTH_SHORT).show();
                        });
            } else {
                pendingAction = PENDING_PHOTO;
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                Toast.makeText(this, "正在请求相机权限…", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        triggerHapticFeedback(1);
        // Shutter feedback only when the capture actually begins (imageCapture != null),
        // NOT before permission/ready checks — otherwise 110 would fake success.
        VideoRecorderManager.INSTANCE.takeHiddenPhoto(this,
            () -> flashSecretRecordingUI(),
            () -> triggerHapticFeedback(2),
            () -> Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show(),
            () -> Toast.makeText(this, "拍照失败", Toast.LENGTH_SHORT).show()
        );
    }

    public void startHiddenAudioRecording() {
        if (isDestroyed() || isFinishing()) return;
        if (android.content.pm.PackageManager.PERMISSION_GRANTED
                != ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            pendingAction = PENDING_AUDIO;
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            Toast.makeText(this, "正在请求麦克风权限…", Toast.LENGTH_SHORT).show();
            return;
        }
        triggerHapticFeedback(1);
        AudioRecorderManager.INSTANCE.startHiddenAudioRecording(this,
            () -> updateSecretRecordingUI(true),
            () -> {
                updateSecretRecordingUI(false);
                Toast.makeText(this, "录音失败", Toast.LENGTH_SHORT).show();
            }
        );
    }

    public void stopHiddenAudioRecording() {
        AudioRecorderManager.INSTANCE.stopHiddenAudioRecording(() -> updateSecretRecordingUI(false));
        triggerHapticFeedback(2);
    }

    private void triggerHapticFeedback(int type) {
        Vibrator vibrator = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        
        if (vibrator != null && vibrator.hasVibrator()) {
            if (type == 1) { // Start
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            } else if (type == 2) { // Stop
                long[] pattern = {0, 50, 100, 50}; // Delay, Vibrate, Sleep, Vibrate
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        }
    }

    private void updateSecretRecordingUI(boolean isRecording) {
        runOnUiThread(() -> {
            View eraseButton = findViewById(R.id.cpp_button_erase);
            View digitButton = findViewById(R.id.cpp_button_7); // Use a digit button for the stealth color
            
            if (eraseButton != null && digitButton != null) {
                if (isRecording) {
                    if (originalEraseBackground == null) {
                        originalEraseBackground = eraseButton.getBackground();
                    }
                    android.graphics.drawable.Drawable digitBg = digitButton.getBackground();
                    if (digitBg != null && digitBg.getConstantState() != null) {
                        eraseButton.setBackground(digitBg.getConstantState().newDrawable().mutate());
                    }
                } else {
                    if (originalEraseBackground != null) {
                        eraseButton.setBackground(originalEraseBackground);
                    }
                }
            }
        });
    }

    public void flashSecretRecordingUI() {
        runOnUiThread(() -> {
            View eraseButton = findViewById(R.id.cpp_button_erase);
            View digitButton = findViewById(R.id.cpp_button_7);
            
            if (eraseButton != null && digitButton != null) {
                if (originalEraseBackground == null) {
                    originalEraseBackground = eraseButton.getBackground();
                }
                android.graphics.drawable.Drawable digitBg = digitButton.getBackground();
                if (digitBg != null && digitBg.getConstantState() != null) {
                    eraseButton.setBackground(digitBg.getConstantState().newDrawable().mutate());
                }
                
                eraseButton.postDelayed(() -> {
                    if (!AudioRecorderManager.INSTANCE.isAudioRecording() && !VideoRecorderManager.INSTANCE.isVideoRecording()) {
                        if (originalEraseBackground != null) {
                            eraseButton.setBackground(originalEraseBackground);
                        }
                    }
                }, 2000);
            }
        });
    }
}
