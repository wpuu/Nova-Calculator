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
 */

package org.solovyev.android.calculator;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.solovyev.android.calculator.ai.AiGatewayFeatureConfig;
import org.solovyev.android.calculator.ai.AiGatewayRequest;
import org.solovyev.android.calculator.ai.AiGatewayResponse;
import org.solovyev.android.calculator.ai.AiNaturalLanguageCoordinator;
import org.solovyev.android.calculator.converter.ConverterFragment;
import org.solovyev.android.calculator.databinding.ActivityMainBinding;
import org.solovyev.android.calculator.history.History;
import org.solovyev.android.calculator.keyboard.PartialKeyboardUi;
import org.solovyev.android.widget.menu.CustomPopupMenu;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import jscl.AngleUnit;
import jscl.NumeralBase;

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
    Editor calculatorEditor;
    @Inject
    AiGatewayFeatureConfig aiGatewayFeatureConfig;
    @Inject
    AiNaturalLanguageCoordinator aiNaturalLanguageCoordinator;
    @Nullable
    View partialKeyboard;
    FrameLayout editor;
    View mainMenuButton;
    @Nullable
    private AlertDialog aiNaturalLanguageDialog;
    private boolean useBackAsPrevious;

    public CalculatorActivity() {
        super(R.layout.activity_main, R.string.cpp_app_name);
    }

    @Override
    protected void bindViews(@Nonnull View contentView) {
        ActivityMainBinding binding = ActivityMainBinding.bind(contentView.findViewById(R.id.main));
        partialKeyboard = binding.partialKeyboard;
        editor = binding.editorContainer.editor;
        mainMenuButton = binding.editorContainer.mainMenu;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        launcher.setActivity(this);
        restartIfModeChanged();
    }

    @Override
    protected void onPause() {
        launcher.clearActivity(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        aiNaturalLanguageCoordinator.cancelCurrent();
        dismissNaturalLanguageDialog();
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

    private void showNaturalLanguageInput() {
        if (!aiGatewayFeatureConfig.isEnabled()) {
            Toast.makeText(this, R.string.nova_ai_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setHint(R.string.nova_ai_natural_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});

        new AlertDialog.Builder(this, App.getTheme().alertDialogTheme)
                .setTitle(R.string.nova_ai_natural_title)
                .setMessage(R.string.nova_ai_natural_description)
                .setView(input)
                .setNegativeButton(R.string.cpp_cancel, null)
                .setPositiveButton(R.string.nova_ai_natural_submit, (dialog, which) -> {
                    final String query = input.getText() == null ? "" : input.getText().toString().trim();
                    if (query.isEmpty()) {
                        Toast.makeText(this, R.string.nova_ai_natural_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    parseNaturalLanguage(query);
                })
                .show();
    }

    private void parseNaturalLanguage(@Nonnull String query) {
        aiNaturalLanguageCoordinator.cancelCurrent();
        dismissNaturalLanguageDialog();

        final AlertDialog dialog = new AlertDialog.Builder(this, App.getTheme().alertDialogTheme)
                .setTitle(R.string.nova_ai_natural_title)
                .setMessage(R.string.nova_ai_natural_loading)
                .setNegativeButton(R.string.cpp_cancel,
                        (d, which) -> aiNaturalLanguageCoordinator.cancelCurrent())
                .create();
        aiNaturalLanguageDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (aiNaturalLanguageDialog == dialog) {
                aiNaturalLanguageDialog = null;
                aiNaturalLanguageCoordinator.cancelCurrent();
            }
        });
        dialog.show();

        aiNaturalLanguageCoordinator.parse(
                query,
                Locale.getDefault().toLanguageTag(),
                new AiNaturalLanguageCoordinator.Listener() {
                    @Override
                    public void onStarted(AiGatewayRequest request) {
                    }

                    @Override
                    public void onFinished(AiGatewayResponse response) {
                        if (isFinishing() || isDestroyed()
                                || aiNaturalLanguageDialog != dialog
                                || !dialog.isShowing()) {
                            return;
                        }
                        if (response != null && response.isSuccess()) {
                            final String candidate = response.getCandidateExpression();
                            if (candidate != null && !candidate.trim().isEmpty()) {
                                calculatorEditor.setText(candidate.trim());
                                dialog.dismiss();
                                Toast.makeText(
                                        CalculatorActivity.this,
                                        R.string.nova_ai_natural_ready,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        dialog.setMessage(messageForNaturalLanguage(response));
                    }
                });
    }

    @Nonnull
    private CharSequence messageForNaturalLanguage(@Nullable AiGatewayResponse response) {
        if (response == null) return getString(R.string.nova_ai_unavailable);
        switch (response.getStatus()) {
            case AUTH_REQUIRED:
                return getString(R.string.nova_ai_auth_required);
            case QUOTA_EXHAUSTED:
                return getString(R.string.nova_ai_quota_exhausted);
            case RATE_LIMITED:
                return getString(R.string.nova_ai_rate_limited);
            case INVALID_REQUEST:
                return getString(R.string.nova_ai_natural_invalid);
            case TEMPORARILY_UNAVAILABLE:
            case SUCCESS:
            default:
                return getString(R.string.nova_ai_unavailable);
        }
    }

    private void dismissNaturalLanguageDialog() {
        final AlertDialog dialog = aiNaturalLanguageDialog;
        aiNaturalLanguageDialog = null;
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            if (dialog.isShowing()) dialog.dismiss();
        }
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
                updateAiActions();
                popup.show();
            }
        }

        private void updateMode() {
            if (popup == null) return;
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
            if (popup == null) return;
            final Menu menu = popup.getMenu();
            final MenuItem menuItem = menu.findItem(R.id.menu_angle_units);
            final AngleUnit angles = Engine.Preferences.angleUnit.getPreference(preferences);
            menuItem.setTitle(makeTitle(R.string.cpp_angles, Engine.Preferences.angleUnitName(angles)));
        }

        private void updateNumeralBase() {
            if (popup == null) return;
            final Menu menu = popup.getMenu();
            final MenuItem menuItem = menu.findItem(R.id.menu_numeral_base);
            final NumeralBase numeralBase = Engine.Preferences.numeralBase.getPreference(preferences);
            menuItem.setTitle(makeTitle(R.string.cpp_radix, Engine.Preferences.numeralBaseName(numeralBase)));
        }

        private void updateAiActions() {
            if (popup == null) return;
            final MenuItem item = popup.getMenu().findItem(R.id.menu_ai_natural_language);
            if (item != null) item.setVisible(aiGatewayFeatureConfig.isEnabled());
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_ai_natural_language) {
                showNaturalLanguageInput();
                return true;
            } else if (itemId == R.id.menu_settings) {
                launcher.showSettings();
                return true;
            } else if (itemId == R.id.menu_tools) {
                startActivity(new android.content.Intent(CalculatorActivity.this, SettingsActivity.class));
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
}
