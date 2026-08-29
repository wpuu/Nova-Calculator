package org.solovyev.android.calculator.preferences;

import static org.solovyev.android.calculator.App.cast;
import static org.solovyev.android.calculator.Engine.Preferences.angleUnitName;
import static org.solovyev.android.calculator.Engine.Preferences.numeralBaseName;
import static org.solovyev.android.calculator.wizard.CalculatorWizards.DEFAULT_WIZARD_FLOW;
import static org.solovyev.android.wizard.WizardUi.startWizard;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.solovyev.android.calculator.Engine;
import org.solovyev.android.calculator.Preferences;
import org.solovyev.android.calculator.Preferences.Gui.Theme;
import org.solovyev.android.calculator.R;
import org.solovyev.android.calculator.autoclicker.AutoClickerService;
import org.solovyev.android.calculator.language.Language;
import org.solovyev.android.calculator.language.Languages;
import org.solovyev.android.prefs.StringPreference;
import org.solovyev.android.wizard.Wizards;
import org.solovyev.common.text.CharacterMapper;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import jscl.AngleUnit;
import jscl.JsclMathEngine;
import jscl.NumeralBase;

/**
 * Calculator settings for the Nova commercial line.
 *
 * The inherited Calculator++ ad-free purchase check is intentionally removed. Commercial
 * entitlements will be supplied by Nova's own entitlement layer; until then this screen has
 * no billing side effects and makes no inherited purchase or advertising requests.
 */
public class PreferencesFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Nonnull
    private static final String ARG_PREFERENCES = "preferences";
    private static final long RECONCILE_DEBOUNCE_MS = 800;

    @Inject
    SharedPreferences preferences;
    @Inject
    Languages languages;
    @Inject
    Wizards wizards;
    @Inject
    JsclMathEngine engine;
    @Inject
    Bus bus;

    private int lastDiagCode = 0;
    private long lastReconcileRequestMs = 0;

    @Nonnull
    public static PreferencesFragment create(int preferences) {
        final PreferencesFragment fragment = new PreferencesFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_PREFERENCES, preferences);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cast(this).getComponent().inject(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        bus.register(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Bundle args = getArguments();
        final int preferencesResId = args == null ? R.xml.preferences :
                args.getInt(ARG_PREFERENCES, R.xml.preferences);
        addPreferencesFromResource(preferencesResId);
    }

    private int currentPreferencesResource() {
        final Bundle args = getArguments();
        return args == null ? R.xml.preferences : args.getInt(ARG_PREFERENCES, R.xml.preferences);
    }

    private void setPreferenceIntent(int xml, @Nonnull PreferencesActivity.PrefDef def) {
        final Preference preference = findPreference(def.id);
        if (preference == null) return;
        final FragmentActivity context = getActivity();
        if (context == null) return;
        final Intent intent = new Intent(context, PreferencesActivity.getClass(context));
        intent.putExtra(PreferencesActivity.EXTRA_PREFERENCE, xml);
        intent.putExtra(PreferencesActivity.EXTRA_PREFERENCE_TITLE, def.title);
        preference.setIntent(intent);
    }

    /** @noinspection deprecation */
    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        final String fragmentTag = "fragment:" + preference.getKey();
        if (getParentFragmentManager().findFragmentByTag(fragmentTag) != null) return;
        if (preference instanceof PrecisionPreference) {
            final PreferenceDialogFragmentCompat dialog = new PrecisionPreference.Dialog();
            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), fragmentTag);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final int preference = currentPreferencesResource();
        if (preference == R.xml.preferences) {
            prepareScreens();
            prepareIntroduction();
            prepareMode();
            prepareAngles();
            prepareRadix();
        } else if (preference == R.xml.preferences_number_format) {
            prepareListPreference(Engine.Preferences.Output.notation, Engine.Notation.class);
            preparePrecisionPreference();
            prepareSeparatorPreference();
            prepareNumberFormatExamplesPreference();
        } else if (preference == R.xml.preferences_onscreen) {
            updateFloatingCalculatorPreferences();
        } else if (preference == R.xml.preferences_auto_clicker) {
            prepareAutoClicker();
        }

        prepareLanguagePreference(preference);
        prepareThemePreference(preference);
    }

    private void prepareScreens() {
        final SparseArray<PreferencesActivity.PrefDef> defs = PreferencesActivity.getPreferenceDefs();
        for (int i = 0; i < defs.size(); i++) {
            setPreferenceIntent(defs.keyAt(i), defs.valueAt(i));
        }
    }

    private void prepareIntroduction() {
        final Preference introduction = findPreference("prefs.introduction");
        if (introduction == null) return;
        introduction.setOnPreferenceClickListener(preference -> {
            if (getActivity() != null) {
                startWizard(wizards, DEFAULT_WIZARD_FLOW, getActivity());
            }
            return true;
        });
    }

    private void prepareNumberFormatExamplesPreference() {
        final Preference p = findPreference("numberFormat.examples");
        if (p instanceof NumberFormatExamplesPreference) {
            ((NumberFormatExamplesPreference) p).update(engine);
        }
    }

    private void prepareSeparatorPreference() {
        final Preference p = findPreference(Engine.Preferences.Output.separator.getKey());
        if (!(p instanceof ListPreference)) return;
        final ListPreference preference = (ListPreference) p;
        preference.setSummary(separatorName(Engine.Preferences.Output.separator.getPreference(preferences)));
        preference.setOnPreferenceChangeListener((ignored, newValue) -> {
            preference.setSummary(separatorName(CharacterMapper.INSTANCE.parseValue(String.valueOf(newValue))));
            return true;
        });
    }

    private int separatorName(char separator) {
        switch (separator) {
            case '\'':
                return R.string.cpp_thousands_separator_apostrophe;
            case ' ':
                return R.string.cpp_thousands_separator_space;
            case 0:
            default:
                return R.string.cpp_thousands_separator_no;
        }
    }

    private void preparePrecisionPreference() {
        final Preference p = findPreference(Engine.Preferences.Output.precision.getKey());
        if (!(p instanceof PrecisionPreference)) return;
        final PrecisionPreference preference = (PrecisionPreference) p;
        preference.setSummary(String.valueOf(Engine.Preferences.Output.precision.getPreference(preferences)));
        preference.setOnPreferenceChangeListener((ignored, newValue) -> {
            preference.setSummary(String.valueOf(newValue));
            return true;
        });
    }

    private <E extends Enum<E> & PreferenceEntry> void prepareListPreference(
            @Nonnull final StringPreference<E> setting, @Nonnull Class<E> type) {
        final Preference p = findPreference(setting.getKey());
        if (!(p instanceof ListPreference)) return;
        final ListPreference preference = (ListPreference) p;
        final E[] entries = type.getEnumConstants();
        final FragmentActivity activity = getActivity();
        if (entries == null || activity == null) return;
        populate(preference, entries);
        preference.setSummary(setting.getPreference(preferences).getName(activity));
        preference.setOnPreferenceChangeListener((ignored, newValue) -> {
            for (E entry : entries) {
                if (entry.getId().equals(newValue)) {
                    preference.setSummary(entry.getName(activity));
                    break;
                }
            }
            return true;
        });
    }

    private void prepareMode() {
        final Preference p = findPreference(Preferences.Gui.mode.getKey());
        if (!(p instanceof ListPreference)) return;
        final ListPreference mode = (ListPreference) p;
        mode.setSummary(Preferences.Gui.getMode(preferences).name);
        mode.setOnPreferenceChangeListener((ignored, newValue) -> {
            mode.setSummary(Preferences.Gui.Mode.valueOf((String) newValue).name);
            return true;
        });
    }

    private void prepareAngles() {
        final Preference p = findPreference(Engine.Preferences.angleUnit.getKey());
        if (!(p instanceof ListPreference)) return;
        final ListPreference angles = (ListPreference) p;
        angles.setSummary(angleUnitName(Engine.Preferences.angleUnit.getPreference(preferences)));
        angles.setOnPreferenceChangeListener((ignored, newValue) -> {
            angles.setSummary(angleUnitName(AngleUnit.valueOf((String) newValue)));
            return true;
        });
    }

    private void prepareRadix() {
        final Preference p = findPreference(Engine.Preferences.numeralBase.getKey());
        if (!(p instanceof ListPreference)) return;
        final ListPreference radix = (ListPreference) p;
        radix.setSummary(numeralBaseName(Engine.Preferences.numeralBase.getPreference(preferences)));
        radix.setOnPreferenceChangeListener((ignored, newValue) -> {
            radix.setSummary(numeralBaseName(NumeralBase.valueOf((String) newValue)));
            return true;
        });
    }

    private void prepareThemePreference(int preferenceResource) {
        if (preferenceResource != R.xml.preferences_appearance) return;
        final Preference p = findPreference(Preferences.Gui.theme.getKey());
        if (!(p instanceof ListPreference)) return;
        final ListPreference theme = (ListPreference) p;
        final FragmentActivity context = getActivity();
        if (context == null) return;
        populate(theme,
                Theme.material_theme,
                Theme.material_black_theme,
                Theme.material_light_theme,
                Theme.metro_blue_theme,
                Theme.metro_green_theme,
                Theme.metro_purple_theme,
                Theme.premium_theme,
                Theme.premium_neumorphism_theme,
                Theme.premium_glass_theme,
                Theme.ios_theme);
        theme.setSummary(Preferences.Gui.getTheme(preferences).getName(context));
        theme.setOnPreferenceChangeListener((ignored, newValue) -> {
            final Theme newTheme = Theme.valueOf((String) newValue);
            theme.setSummary(newTheme.getName(context));
            return true;
        });
    }

    private static void populate(@Nonnull ListPreference preference, @Nonnull PreferenceEntry... entries) {
        populate(preference, Arrays.asList(entries));
    }

    private static void populate(@Nonnull ListPreference preference,
                                 @Nonnull List<? extends PreferenceEntry> entries) {
        final int size = entries.size();
        final CharSequence[] labels = new CharSequence[size];
        final CharSequence[] values = new CharSequence[size];
        final Context context = preference.getContext();
        for (int i = 0; i < size; i++) {
            final PreferenceEntry entry = entries.get(i);
            labels[i] = entry.getName(context);
            values[i] = entry.getId();
        }
        preference.setEntries(labels);
        preference.setEntryValues(values);
    }

    private void prepareLanguagePreference(int preferenceResource) {
        if (preferenceResource != R.xml.preferences_appearance) return;
        final Preference p = findPreference(Preferences.Gui.language.getKey());
        if (!(p instanceof ListPreference)) return;
        final ListPreference language = (ListPreference) p;
        final FragmentActivity activity = getActivity();
        if (activity == null) return;
        populate(language, languages.getList());
        language.setSummary(languages.getCurrent().getName(activity));
        language.setOnPreferenceChangeListener((ignored, newValue) -> {
            final Language value = languages.get((String) newValue);
            language.setSummary(value.getName(activity));
            return true;
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Preferences.Onscreen.showAppIcon.isSameKey(key)) {
            updateFloatingCalculatorPreferences();
        } else if (Preferences.AutoClicker.intent.getKey().equals(key)
                || Preferences.AutoClicker.enabled.getKey().equals(key)
                || Preferences.AutoClicker.lastFailure.getKey().equals(key)) {
            final Preference p = findPreference(Preferences.AutoClicker.intent.getKey());
            if (p instanceof SwitchPreferenceCompat) {
                updateAutoClickerEnabledSummary((SwitchPreferenceCompat) p);
            }
            if (Preferences.AutoClicker.enabled.getKey().equals(key)
                    && Preferences.AutoClicker.enabled.getPreference(preferences)) {
                final android.app.Activity activity = getActivity();
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    activity.finish();
                }
            }
        }
    }

    private void updateFloatingCalculatorPreferences() {
        final Preference theme = findPreference(Preferences.Onscreen.theme.getKey());
        if (theme != null) {
            theme.setEnabled(Preferences.Onscreen.showAppIcon.getPreference(preferences));
        }
    }

    private void prepareAutoClicker() {
        final Preference p = findPreference(Preferences.AutoClicker.intent.getKey());
        if (p instanceof SwitchPreferenceCompat) {
            final SwitchPreferenceCompat enabledPref = (SwitchPreferenceCompat) p;
            enabledPref.setPersistent(false);
            updateAutoClickerEnabledSummary(enabledPref);
            enabledPref.setOnPreferenceChangeListener((ignored, newValue) -> {
                final boolean enable = (Boolean) newValue;
                preferences.edit().putBoolean(Preferences.AutoClicker.intent.getKey(), enable).apply();
                if (enable && getActivity() != null
                        && !AutoClickerService.isAccessibilityEnabled(getActivity())) {
                    openAccessibilitySettingsSafely();
                }
                requestAutoClickerReconcile();
                updateAutoClickerEnabledSummary(enabledPref);
                return false;
            });
        }

        final Preference diagPref = findPreference("auto_clicker_diagnostics");
        if (diagPref != null) {
            diagPref.setOnPreferenceClickListener(ignored -> {
                handleAutoClickerDiagnostic(lastDiagCode);
                return true;
            });
        }
        final Preference retryPref = findPreference("auto_clicker_retry_now");
        if (retryPref != null) {
            retryPref.setOnPreferenceClickListener(ignored -> {
                requestAutoClickerReconcile();
                return true;
            });
        }
        final Preference openA11yPref = findPreference("auto_clicker_open_accessibility");
        if (openA11yPref != null) {
            openA11yPref.setOnPreferenceClickListener(ignored -> {
                openAccessibilitySettingsSafely();
                return true;
            });
        }
        final Preference openOverlayPref = findPreference("auto_clicker_open_overlay");
        if (openOverlayPref != null) {
            openOverlayPref.setOnPreferenceClickListener(ignored -> {
                openOverlaySettingsSafely();
                return true;
            });
        }
        final Preference copyDiagPref = findPreference("auto_clicker_copy_diagnostics");
        if (copyDiagPref != null) {
            copyDiagPref.setOnPreferenceClickListener(ignored -> {
                copyDiagnosticToClipboard(lastDiagCode);
                return true;
            });
        }

        final Preference intervalPref = findPreference(Preferences.AutoClicker.interval.getKey());
        final Preference durationPref = findPreference(Preferences.AutoClicker.duration.getKey());
        updateAutoClickerSummary(intervalPref, durationPref);

        final Preference.OnPreferenceChangeListener valueListener = (preference, newValue) -> {
            if (preference == intervalPref && newValue instanceof String) {
                String value = ((String) newValue).trim();
                if (value.isEmpty()) value = Preferences.AutoClicker.interval.getPreference(preferences);
                final long safe = clamp(value, 40, 5000, 40);
                preferences.edit().putString(Preferences.AutoClicker.interval.getKey(), String.valueOf(safe)).apply();
                if (intervalPref != null) intervalPref.setSummary("当前间隔：" + safe + " 毫秒");
                return false;
            }
            if (preference == durationPref && newValue instanceof String) {
                String value = ((String) newValue).trim();
                if (value.isEmpty()) value = Preferences.AutoClicker.duration.getPreference(preferences);
                final long safe = clamp(value, 5, 3600, 60);
                preferences.edit().putString(Preferences.AutoClicker.duration.getKey(), String.valueOf(safe)).apply();
                if (durationPref != null) durationPref.setSummary("当前时长：" + safe + " 秒");
                return false;
            }
            return true;
        };
        if (intervalPref != null) intervalPref.setOnPreferenceChangeListener(valueListener);
        if (durationPref != null) durationPref.setOnPreferenceChangeListener(valueListener);
    }

    private static long clamp(String raw, long min, long max, long fallback) {
        try {
            final long value = Long.parseLong(raw);
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void updateAutoClickerSummary(Preference intervalPref, Preference durationPref) {
        if (intervalPref != null) {
            final long value = clamp(Preferences.AutoClicker.interval.getPreference(preferences), 40, 5000, 40);
            intervalPref.setSummary("当前间隔：" + value + " 毫秒");
        }
        if (durationPref != null) {
            final long value = clamp(Preferences.AutoClicker.duration.getPreference(preferences), 5, 3600, 60);
            durationPref.setSummary("当前时长：" + value + " 秒");
        }
    }

    private void updateAutoClickerEnabledSummary(SwitchPreferenceCompat pref) {
        if (pref == null || getActivity() == null) return;
        final boolean effective = Preferences.AutoClicker.enabled.getPreference(preferences);
        final boolean intent = Preferences.AutoClicker.intent.getPreference(preferences);
        final boolean granted = AutoClickerService.isAccessibilityEnabled(getActivity());
        final String failure = Preferences.AutoClicker.lastFailure.getPreference(preferences);
        int code = 0;
        try {
            code = Integer.parseInt(failure.trim());
        } catch (Exception ignored) {
        }

        pref.setChecked(intent && effective);
        int diagCode = 0;
        if (effective) {
            pref.setSummary("已开启 · 双圆圈显示中");
        } else if (!intent) {
            pref.setSummary("未开启 · 需要时再授予无障碍权限");
        } else if (code >= 1 && code <= 7) {
            pref.setSummary("出错（错误码 " + code + "）· 解决办法见下方");
            diagCode = code;
        } else if (!granted) {
            pref.setSummary("错误码 5 · 解决办法见下方");
            diagCode = 5;
        } else {
            pref.setSummary("无障碍已授予，正在显示圆圈…");
        }
        updateAutoClickerDiagnostics(diagCode);
    }

    private void updateAutoClickerDiagnostics(int code) {
        final Preference diag = findPreference("auto_clicker_diagnostics");
        final Preference retry = findPreference("auto_clicker_retry_now");
        final Preference openA11y = findPreference("auto_clicker_open_accessibility");
        final Preference openOverlay = findPreference("auto_clicker_open_overlay");
        final Preference copyDiag = findPreference("auto_clicker_copy_diagnostics");
        final boolean show = code >= 1 && code <= 7;

        if (diag != null) {
            diag.setVisible(show);
            lastDiagCode = show ? code : 0;
            if (show) {
                final Context context = diag.getContext();
                diag.setTitle(context.getString(R.string.auto_clicker_diagnostic_title, code));
                diag.setSummary(AutoClickerService.getFailureMessage(context, code));
            }
        }
        if (retry != null) retry.setVisible(show && (code == 2 || code == 3 || code == 4 || code == 6 || code == 7));
        if (openA11y != null) openA11y.setVisible(show && (code == 2 || code == 3 || code == 5 || code == 6 || code == 7));
        if (openOverlay != null) openOverlay.setVisible(show && code == 1);
        if (copyDiag != null) copyDiag.setVisible(show && code == 4);
    }

    private void copyDiagnosticToClipboard(int code) {
        if (getActivity() == null) return;
        try {
            final String version = getActivity().getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0).versionName;
            final String details = AutoClickerService.getDiagnosticSnapshot(getActivity(), code);
            final String text = "Nova Calculator 连点辅助 错误码 " + code + "\n"
                    + AutoClickerService.getFailureMessage(getActivity(), code)
                    + "\n版本: " + version + "\n" + details;
            final ClipboardManager clipboard = (ClipboardManager) getActivity()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("autoclicker_diag", text));
                Toast.makeText(getActivity(), R.string.auto_clicker_diagnostic_copied,
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleAutoClickerDiagnostic(int code) {
        switch (code) {
            case 1:
                openOverlaySettingsSafely();
                break;
            case 2:
            case 3:
            case 6:
            case 7:
                requestAutoClickerReconcile();
                openAccessibilitySettingsSafely();
                break;
            case 4:
                requestAutoClickerReconcile();
                showGeneralRecoveryInstructions();
                break;
            case 5:
                openAccessibilitySettingsSafely();
                break;
            default:
                break;
        }
        updateAutoClickerDiagnostics(lastDiagCode);
    }

    private void requestAutoClickerReconcile() {
        if (getActivity() == null) return;
        final long now = System.currentTimeMillis();
        if (now - lastReconcileRequestMs < RECONCILE_DEBOUNCE_MS) return;
        lastReconcileRequestMs = now;
        try {
            final Intent reconcile = new Intent(getActivity(), AutoClickerService.class);
            reconcile.setAction(AutoClickerService.ACTION_RECONCILE);
            reconcile.setPackage(getActivity().getPackageName());
            getActivity().sendBroadcast(reconcile);
            Toast.makeText(getActivity(), R.string.auto_clicker_retry_requested,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void openAccessibilitySettingsSafely() {
        if (getActivity() == null) return;
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void openOverlaySettingsSafely() {
        if (getActivity() == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getActivity().getPackageName())));
            } else {
                openAccessibilitySettingsSafely();
            }
        } catch (Exception ignored) {
            openAccessibilitySettingsSafely();
        }
    }

    private void showGeneralRecoveryInstructions() {
        if (getActivity() == null) return;
        final int code = lastDiagCode;
        final String message = getString(R.string.auto_clicker_reopen_accessibility_hint) + "\n"
                + getString(R.string.auto_clicker_restart_app_hint) + "\n"
                + getString(R.string.auto_clicker_restart_device_hint) + "\n"
                + getString(R.string.auto_clicker_clear_cache_hint);
        new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.auto_clicker_diagnostic_title, code))
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.auto_clicker_copy_diagnostics,
                        (DialogInterface dialog, int which) -> copyDiagnosticToClipboard(code))
                .show();
    }

    @Subscribe
    public void onEngineChanged(Engine.ChangedEvent event) {
        if (currentPreferencesResource() == R.xml.preferences_number_format) {
            prepareNumberFormatExamplesPreference();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reconcileAutoClickerState();
    }

    private void reconcileAutoClickerState() {
        final Preference p = findPreference(Preferences.AutoClicker.intent.getKey());
        if (!(p instanceof SwitchPreferenceCompat)) return;
        final SwitchPreferenceCompat enabledPref = (SwitchPreferenceCompat) p;
        final boolean intent = Preferences.AutoClicker.intent.getPreference(preferences);
        updateAutoClickerEnabledSummary(enabledPref);
        if (intent) requestAutoClickerReconcile();
    }

    @Override
    public void onDestroy() {
        bus.unregister(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
