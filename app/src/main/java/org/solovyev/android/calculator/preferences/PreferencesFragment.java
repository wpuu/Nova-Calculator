package org.solovyev.android.calculator.preferences;

import static org.solovyev.android.calculator.App.cast;
import static org.solovyev.android.calculator.Engine.Preferences.angleUnitName;
import static org.solovyev.android.calculator.Engine.Preferences.numeralBaseName;
import static org.solovyev.android.calculator.wizard.CalculatorWizards.DEFAULT_WIZARD_FLOW;
import static org.solovyev.android.wizard.WizardUi.startWizard;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.app.AlertDialog;
import android.widget.Toast;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import jscl.AngleUnit;
import jscl.JsclMathEngine;
import jscl.NumeralBase;
import org.solovyev.android.calculator.ActivityLauncher;
import org.solovyev.android.calculator.AdView;
import org.solovyev.android.calculator.Engine;
import org.solovyev.android.calculator.Preferences;
import org.solovyev.android.calculator.autoclicker.AutoClickerService;
import org.solovyev.android.calculator.Preferences.Gui.Theme;
import org.solovyev.android.calculator.R;
import org.solovyev.android.calculator.feedback.FeedbackReporter;
import org.solovyev.android.calculator.language.Language;
import org.solovyev.android.calculator.language.Languages;
import org.solovyev.android.checkout.BillingRequests;
import org.solovyev.android.checkout.Checkout;
import org.solovyev.android.checkout.ProductTypes;
import org.solovyev.android.checkout.RequestListener;
import org.solovyev.android.prefs.StringPreference;
import org.solovyev.android.wizard.Wizards;
import org.solovyev.common.text.CharacterMapper;


public class PreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Nonnull
    private static String ARG_PREFERENCES = "preferences";

    @Nullable
    private AdView adView;
    @Inject
    SharedPreferences preferences;

    // Latest error code shown in the diagnostics panel, so its click handler knows
    // which one-tap fix (or clipboard copy) to perform.
    private int lastDiagCode = 0;
    private long lastReconcileRequestMs = 0;
    private static final long RECONCILE_DEBOUNCE_MS = 800;
    @Inject
    Languages languages;
    @Inject
    Wizards wizards;
    @Inject
    JsclMathEngine engine;
    @Inject
    FeedbackReporter feedbackReporter;
    @Inject
    ActivityLauncher launcher;
    @Inject
    Bus bus;

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
    public void onCreatePreferences(@androidx.annotation.Nullable Bundle savedInstanceState, @androidx.annotation.Nullable String rootKey) {
        int preferencesResId = getArguments().getInt(ARG_PREFERENCES);
        addPreferencesFromResource(preferencesResId);
    }

    private void setPreferenceIntent(int xml, @Nonnull PreferencesActivity.PrefDef def) {
        final Preference preference = findPreference(def.id);
        if (preference != null) {
            final FragmentActivity context = getActivity();
            final Intent intent = new Intent(context, PreferencesActivity.getClass(context));
            intent.putExtra(PreferencesActivity.EXTRA_PREFERENCE, xml);
            intent.putExtra(PreferencesActivity.EXTRA_PREFERENCE_TITLE, def.title);
            preference.setIntent(intent);
        }
    }

    /** @noinspection deprecation*/
    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        String fragmentTag = "fragment:" + preference.getKey();
        if (getParentFragmentManager().findFragmentByTag(fragmentTag) != null) return;

        if (preference instanceof PrecisionPreference) {
            final PreferenceDialogFragmentCompat f = new PrecisionPreference.Dialog();
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(), fragmentTag);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final int preference = getArguments().getInt(ARG_PREFERENCES);
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

        getCheckout().whenReady(new Checkout.EmptyListener() {
            @Override
            public void onReady(@Nonnull BillingRequests requests) {
                requests.isPurchased(ProductTypes.IN_APP, "ad_free", new RequestListener<Boolean>() {
                    @Override
                    public void onSuccess(@Nonnull Boolean purchased) {
                        final Preference supportProject = findPreference("prefs.supportProject");
                        if (supportProject != null) {
                            supportProject.setEnabled(!purchased);
                            supportProject.setSelectable(!purchased);
                        }
                        onShowAd(!purchased);
                    }

                    @Override
                    public void onError(int i, @Nonnull Exception e) {
                        onShowAd(false);
                    }
                });
            }
        });
    }

    private void prepareScreens() {
        final SparseArray<PreferencesActivity.PrefDef> preferences = PreferencesActivity.getPreferenceDefs();
        for (int i = 0; i < preferences.size(); i++) {
            setPreferenceIntent(preferences.keyAt(i), preferences.valueAt(i));
        }
    }

    private void prepareIntroduction() {
        final Preference introduction = findPreference("prefs.introduction");
        introduction.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startWizard(wizards, DEFAULT_WIZARD_FLOW, getActivity());
                return true;
            }
        });
    }

    private void prepareNumberFormatExamplesPreference() {
        final NumberFormatExamplesPreference preference = (NumberFormatExamplesPreference) getPreferenceManager().findPreference("numberFormat.examples");
        if (preference == null) {
            return;
        }
        preference.update(engine);
    }

    private void prepareSeparatorPreference() {
        final ListPreference preference = (ListPreference) getPreferenceManager().findPreference(Engine.Preferences.Output.separator.getKey());
        preference.setSummary(separatorName(Engine.Preferences.Output.separator.getPreference(preferences)));
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference p, Object newValue) {
                preference.setSummary(separatorName(CharacterMapper.INSTANCE.parseValue(String.valueOf(newValue))));
                return true;
            }
        });
    }

    private int separatorName(char separator) {
        switch (separator) {
            case '\'':
                return R.string.cpp_thousands_separator_apostrophe;
            case ' ':
                return R.string.cpp_thousands_separator_space;
            case 0:
                return R.string.cpp_thousands_separator_no;
        }
        return R.string.cpp_thousands_separator_no;
    }

    private void preparePrecisionPreference() {
        final PrecisionPreference preference = (PrecisionPreference) getPreferenceManager().findPreference(Engine.Preferences.Output.precision.getKey());
        preference.setSummary(String.valueOf(Engine.Preferences.Output.precision.getPreference(preferences)));
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference p, Object newValue) {
                preference.setSummary(String.valueOf(newValue));
                return true;
            }
        });
    }

    private <E extends Enum<E> & PreferenceEntry> void prepareListPreference(@Nonnull final StringPreference<E> p, @Nonnull Class<E> type) {
        final ListPreference preference = (ListPreference) getPreferenceManager().findPreference(p.getKey());
        if (preference == null) {
            return;
        }
        final E[] entries = type.getEnumConstants();
        final FragmentActivity activity = getActivity();
        populate(preference, entries);
        preference.setSummary(p.getPreference(preferences).getName(activity));
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference p, Object newValue) {
                for (E entry : entries) {
                    if (entry.getId().equals(newValue)) {
                        preference.setSummary(entry.getName(activity));
                        break;
                    }
                }
                return true;
            }
        });
    }

    private void prepareMode() {
        final ListPreference mode = (ListPreference) getPreferenceManager().findPreference(Preferences.Gui.mode.getKey());
        mode.setSummary(Preferences.Gui.getMode(preferences).name);
        mode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                mode.setSummary(Preferences.Gui.Mode.valueOf((String) newValue).name);
                return true;
            }
        });
    }

    private void prepareAngles() {
        final ListPreference angles = (ListPreference) getPreferenceManager().findPreference(Engine.Preferences.angleUnit.getKey());
        angles.setSummary(angleUnitName(Engine.Preferences.angleUnit.getPreference(preferences)));
        angles.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                angles.setSummary(angleUnitName(AngleUnit.valueOf((String) newValue)));
                return true;
            }
        });
    }

    private void prepareRadix() {
        final ListPreference radix = (ListPreference) getPreferenceManager().findPreference(Engine.Preferences.numeralBase.getKey());
        radix.setSummary(numeralBaseName(Engine.Preferences.numeralBase.getPreference(preferences)));
        radix.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                radix.setSummary(numeralBaseName(NumeralBase.valueOf((String) newValue)));
                return true;
            }
        });
    }

    private void prepareThemePreference(int preference) {
        if (preference != R.xml.preferences_appearance) {
            return;
        }
        final ListPreference theme = (ListPreference) getPreferenceManager().findPreference(Preferences.Gui.theme.getKey());
        final FragmentActivity context = getActivity();
        populate(theme,
                Theme.material_theme,
                Theme.material_black_theme,
                Theme.material_light_theme,
                Theme.metro_blue_theme,
                Theme.metro_green_theme,
                Theme.metro_purple_theme);
        theme.setSummary(Preferences.Gui.getTheme(preferences).getName(context));
        theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final Theme newTheme = Theme.valueOf((String) newValue);
                theme.setSummary(newTheme.getName(context));
                return true;
            }
        });
    }

    private static void populate(@Nonnull ListPreference preference, @Nonnull PreferenceEntry... entries) {
        populate(preference, Arrays.asList(entries));
    }

    private static void populate(@Nonnull ListPreference preference, @Nonnull List<? extends PreferenceEntry> entries) {
        final int size = entries.size();
        final CharSequence[] e = new CharSequence[size];
        final CharSequence[] v = new CharSequence[size];
        final Context context = preference.getContext();
        for (int i = 0; i < size; i++) {
            final PreferenceEntry entry = entries.get(i);
            e[i] = entry.getName(context);
            v[i] = entry.getId();
        }
        preference.setEntries(e);
        preference.setEntryValues(v);
    }

    private void prepareLanguagePreference(int preference) {
        if (preference != R.xml.preferences_appearance) {
            return;
        }

        final ListPreference language = (ListPreference) getPreferenceManager().findPreference(Preferences.Gui.language.getKey());
        populate(language, languages.getList());
        language.setSummary(languages.getCurrent().getName(getActivity()));
        language.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final Language l = languages.get((String) newValue);
                language.setSummary(l.getName(getActivity()));
                return true;
            }
        });
    }

    @Nonnull
    private Checkout getCheckout() {
        return ((PreferencesActivity) getActivity()).getCheckout();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (Preferences.Onscreen.showAppIcon.isSameKey(key)) {
            updateFloatingCalculatorPreferences();
        } else if (Preferences.AutoClicker.intent.getKey().equals(key)) {
            // The user's wish changed (toggle, screen-off, or service re-grant). Re-sync the
            // checkbox to the REAL state — checked only when both circles are actually
            // displayed (effective). The checkbox never tracks the wish alone.
            final SwitchPreferenceCompat ep =
                    (SwitchPreferenceCompat) findPreference(Preferences.AutoClicker.intent.getKey());
            // The checkbox reflects the real state; updateAutoClickerEnabledSummary
            // decides the checked state (checked only when truly on / pending-with-no-error).
            updateAutoClickerEnabledSummary(ep);
        } else if (Preferences.AutoClicker.enabled.getKey().equals(key)) {
            // The service changed the actual effective state (circles appeared / gone).
            // Re-sync the checkbox so it reflects the truth.
            final SwitchPreferenceCompat ep =
                    (SwitchPreferenceCompat) findPreference(Preferences.AutoClicker.intent.getKey());
            updateAutoClickerEnabledSummary(ep);
            // When the two circles actually appear (enabled -> true), drop back to the
            // calculator number page so the user lands where the click targets are,
            // instead of staying on the Settings page. This fires on the REAL event
            // (circles shown) rather than a fixed delay, so it always lands correctly
            // — whether accessibility was already granted or granted just now.
            if (preferences.getBoolean(Preferences.AutoClicker.enabled.getKey(), false)) {
                final android.app.Activity act = getActivity();
                if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                    act.finish();
                }
            }
        } else if (Preferences.AutoClicker.lastFailure.getKey().equals(key)) {
            // The service recorded a failure code for why the circles aren't showing. Reflect
            // it immediately so the user sees the concrete error + fix on this page.
            final SwitchPreferenceCompat ep =
                    (SwitchPreferenceCompat) findPreference(Preferences.AutoClicker.intent.getKey());
            updateAutoClickerEnabledSummary(ep);
        }
    }

    private void updateFloatingCalculatorPreferences() {
        final Preference theme = findPreference(Preferences.Onscreen.theme.getKey());
        if (theme != null) {
            theme.setEnabled(Preferences.Onscreen.showAppIcon.getPreference(preferences));
        }
    }

    private void prepareAutoClicker() {
        final SwitchPreferenceCompat enabledPref =
                (SwitchPreferenceCompat) findPreference(Preferences.AutoClicker.intent.getKey());
        if (enabledPref != null) {
            // The switch is a LIVE VIEW of the real state, not a raw write of the
            // user's wish. Disable the framework auto-persist so the programmatic
            // setChecked() we use to UNCHECK on failure can never clobber the wish
            // (auto_clicker_intent) — we persist the wish ourselves below instead.
            enabledPref.setPersistent(false);
            // Initial visual is driven by the REAL state (intent && effective), never by
            // the wish alone — so a previously-failed enable shows OFF, not stuck ON.
            updateAutoClickerEnabledSummary(enabledPref);
            enabledPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean enable = (Boolean) newValue;
                    // Persist the user's wish ourselves (persistent=false above) so the
                    // service can react; the framework would otherwise also write it.
                    preferences.edit().putBoolean(Preferences.AutoClicker.intent.getKey(), enable).apply();
                    // If we are enabling but accessibility is not yet granted, send the user to
                    // grant it; the service shows the circles automatically once bound.
                    if (enable && !AutoClickerService.isAccessibilityEnabled(getActivity())) {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    }
                    // Nudge the service to re-evaluate immediately on every toggle so the
                    // circles appear (or the failure code updates) without waiting for a resume.
                    try {
                        Intent reconcile = new Intent(getActivity(), AutoClickerService.class);
                        reconcile.setAction(AutoClickerService.ACTION_RECONCILE);
                        reconcile.setPackage(getActivity().getPackageName());
                        getActivity().sendBroadcast(reconcile);
                    } catch (Exception ignored) {
                    }
                    // Jump-to-calculator is now driven by the real `enabled=true` event in
                    // onSharedPreferenceChanged (circles actually appeared), not a fixed delay.
                    // IMPORTANT: do NOT let the framework auto-check the switch to the user's
                    // wish. The checkbox must reflect the REAL state (both circles actually
                    // displayed = `enabled`), so a failed enable stays visibly OFF. We drive
                    // the visual ourselves via updateAutoClickerEnabledSummary, which sets
                    // checked = (intent && effective). Returning false prevents the system
                    // from clobbering that with the raw toggle value.
                    updateAutoClickerEnabledSummary(enabledPref);
                    return false;
                }
            });

            // Diagnostics panel: tap to self-serve. For self-serviceable errors
            // (1 = overlay permission, 5 = accessibility not truly on) jump straight to the
            // relevant system setting; for internal errors copy a diagnostic snippet so the
            // user can report without retyping.
            // Diagnotics panel: tapping the description runs the primary self-help
            // action for the current error code. Dedicated buttons below give explicit
            // "立即重试 / 去无障碍设置 / 去悬浮窗设置 / 复制诊断" entries so the
            // user never has to copy a logcat to a developer.
            final Preference diagPref = findPreference("auto_clicker_diagnostics");
            if (diagPref != null) {
                diagPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        handleAutoClickerDiagnostic(lastDiagCode);
                        return true;
                    }
                });
            }
            final Preference retryPref = findPreference("auto_clicker_retry_now");
            if (retryPref != null) {
                retryPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        requestAutoClickerReconcile();
                        return true;
                    }
                });
            }
            final Preference openA11yPref = findPreference("auto_clicker_open_accessibility");
            if (openA11yPref != null) {
                openA11yPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        openAccessibilitySettingsSafely();
                        return true;
                    }
                });
            }
            final Preference openOverlayPref = findPreference("auto_clicker_open_overlay");
            if (openOverlayPref != null) {
                openOverlayPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        openOverlaySettingsSafely();
                        return true;
                    }
                });
            }
            final Preference copyDiagPref = findPreference("auto_clicker_copy_diagnostics");
            if (copyDiagPref != null) {
                copyDiagPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        copyDiagnosticToClipboard(lastDiagCode);
                        return true;
                    }
                });
            }
        }

        final Preference intervalPref = findPreference(Preferences.AutoClicker.interval.getKey());
        final Preference durationPref = findPreference(Preferences.AutoClicker.duration.getKey());
        updateAutoClickerSummary(intervalPref, durationPref);

        Preference.OnPreferenceChangeListener valueListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference == intervalPref && newValue instanceof String) {
                    String v = ((String) newValue).trim();
                    if (v.isEmpty()) v = Preferences.AutoClicker.interval.getPreference(preferences);
                    intervalPref.setSummary("当前间隔：" + v + " 毫秒");
                } else if (preference == durationPref && newValue instanceof String) {
                    String v = ((String) newValue).trim();
                    if (v.isEmpty()) v = Preferences.AutoClicker.duration.getPreference(preferences);
                    durationPref.setSummary("当前时长：" + v + " 秒");
                }
                return true;
            }
        };
        if (intervalPref != null) intervalPref.setOnPreferenceChangeListener(valueListener);
        if (durationPref != null) durationPref.setOnPreferenceChangeListener(valueListener);
    }

    private void updateAutoClickerSummary(Preference intervalPref, Preference durationPref) {
        if (intervalPref != null) {
            String v = Preferences.AutoClicker.interval.getPreference(preferences);
            if (v == null) v = "";
            intervalPref.setSummary("当前间隔：" + v.trim() + " 毫秒");
        }
        if (durationPref != null) {
            String v = Preferences.AutoClicker.duration.getPreference(preferences);
            if (v == null) v = "";
            durationPref.setSummary("当前时长：" + v.trim() + " 秒");
        }
    }

    // Keeps BOTH the checkbox and the summary honest about the real state:
    //   - The box is only shown as CHECKED when the user wants it on AND the circles
    //     are actually up (effective). If the user asked for it but it errored out
    //     (service crashed -> code 6, not bound -> code 5, overlay failed -> 1-4/7),
    //     the box must NOT look checked — a checked box that actually failed is
    //     contradictory and meaningless, as the user pointed out.
    //   - The box is ONLY checked when the user wants it on AND BOTH circles are actually
    //     on screen. "Both circles out" is exactly what `effective` (auto_clicker_enabled)
    //     means — the service writes it true ONLY after addOverlayAtomically() succeeded
    //     (overlayReady == both circles mounted), and false on every teardown. So the
    //     checkbox is a faithful mirror of the real visible state, never of the system's
    //     "accessibility enabled" string. No circles -> never checked. (User requirement.)
    // The switch itself only shows a SHORT pointer ("解决办法见下方"); the full fix
    // lives in the dedicated diagnostics panel below so the user can self-serve.
    private void updateAutoClickerEnabledSummary(SwitchPreferenceCompat pref) {
        if (pref == null) return;
        boolean effective = Preferences.AutoClicker.enabled.getPreference(preferences);
        boolean intent = Preferences.AutoClicker.intent.getPreference(preferences);
        boolean granted = AutoClickerService.isAccessibilityEnabled(getActivity());
        String failure = Preferences.AutoClicker.lastFailure.getPreference(preferences);
        int code = 0;
        try {
            code = Integer.parseInt(failure.trim());
        } catch (Exception ignored) {
        }
        // Checked ONLY when wished on AND both circles are truly displayed (effective).
        // The optimistic "granted && no-error-yet" branch was removed: it let the box show
        // checked while accessibility was merely *listed* in settings but the circles had
        // not actually mounted (or the service was dead) — a meaningless fake-success.
        pref.setChecked(intent && effective);

        int diagCode = 0;
        if (effective) {
            pref.setSummary("已开启 · 双圆圈显示中");
        } else if (!intent) {
            pref.setSummary("未开启 · 需在系统设置中授予无障碍权限");
        } else if (code >= 1 && code <= 7) {
            pref.setSummary("出错（错误码 " + code + "）· 解决办法见下方");
            diagCode = code;
        } else if (!granted) {
            pref.setSummary("错误码 5 · 解决办法见下方");
            diagCode = 5;
        } else {
            pref.setSummary("无障碍已授予，正在显示圆圈…（圆圈出现后开关会自动打开；若迟迟不出现，请返回本页查看错误码或重开无障碍服务）");
        }
        updateAutoClickerDiagnostics(diagCode);
    }

    // Shows/hides the diagnostics panel (auto_clicker_diagnostics) below the switch. It is
    // only visible when there is a concrete failure, then it carries the full "错误码 + 原因
    // + 解决办法" text. The panel is also tappable: for self-serviceable errors it jumps
    // straight to the relevant system setting; for internal errors it copies a diagnostic
    // snippet so the user can report without retyping. This lets the user resolve issues
    // on their own instead of coming back to ask every time.
    private void updateAutoClickerDiagnostics(int code) {
        Preference diag = findPreference("auto_clicker_diagnostics");
        Preference retry = findPreference("auto_clicker_retry_now");
        Preference openA11y = findPreference("auto_clicker_open_accessibility");
        Preference openOverlay = findPreference("auto_clicker_open_overlay");
        Preference copyDiag = findPreference("auto_clicker_copy_diagnostics");
        boolean show = code >= 1 && code <= 7;
        if (diag != null) {
            diag.setVisible(show);
            lastDiagCode = show ? code : 0;
            if (show) {
                Context ctx = diag.getContext();
                diag.setTitle(ctx.getString(R.string.auto_clicker_diagnostic_title, code));
                diag.setSummary(AutoClickerService.getFailureMessage(ctx, code));
            }
        }
        // Action buttons are shown only for the codes they actually help, so the
        // user always sees a concrete next step (never "contact developer").
        if (retry != null) retry.setVisible(show && (code == 2 || code == 3 || code == 4 || code == 6 || code == 7));
        if (openA11y != null) openA11y.setVisible(show && (code == 2 || code == 3 || code == 5 || code == 6 || code == 7));
        if (openOverlay != null) openOverlay.setVisible(show && code == 1);
        if (copyDiag != null) copyDiag.setVisible(show && code == 4);
    }

    private void copyDiagnosticToClipboard(int code) {
        if (getActivity() == null) return;
        try {
            String version = getActivity().getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0).versionName;
            String details = AutoClickerService.getDiagnosticSnapshot(getActivity(), code);
            String text = "Nova Calculator 连点辅助 错误码 " + code + "\n"
                    + AutoClickerService.getFailureMessage(getActivity(), code)
                    + "\n版本: " + version + "\n" + details;
            ClipboardManager cm = (ClipboardManager) getActivity()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("autoclicker_diag", text));
                Toast.makeText(getActivity(),
                        R.string.auto_clicker_diagnostic_copied, Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {
        }
    }

    // Routes the current error code to its concrete, in-app self-help action.
    // Every branch gives the user a real next step — none say "contact developer".
    private void handleAutoClickerDiagnostic(int code) {
        switch (code) {
            case 1: // FAILURE_OVERLAY_PERMISSION
                openOverlaySettingsSafely();
                break;
            case 2: // FAILURE_BAD_TOKEN
            case 3: // FAILURE_INVALID_DISPLAY
            case 6: // FAILURE_SERVICE_CONNECT_FAILED
            case 7: // FAILURE_TIMEOUT_NO_CIRCLES
                requestAutoClickerReconcile();
                openAccessibilitySettingsSafely();
                break;
            case 4: // FAILURE_UNKNOWN
                requestAutoClickerReconcile();
                showGeneralRecoveryInstructions();
                break;
            case 5: // FAILURE_A11Y_OFF
                openAccessibilitySettingsSafely();
                break;
        }
        updateAutoClickerDiagnostics(lastDiagCode);
    }

    // Sends a package-scoped ACTION_RECONCILE broadcast so the service re-evaluates
    // and re-mounts the circles. Debounced so a frantic tapper can't flood
    // the service with repeated broadcasts.
    private void requestAutoClickerReconcile() {
        if (getActivity() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastReconcileRequestMs < RECONCILE_DEBOUNCE_MS) return;
        lastReconcileRequestMs = now;
        try {
            Intent reconcile = new Intent(getActivity(), AutoClickerService.class);
            reconcile.setAction(AutoClickerService.ACTION_RECONCILE);
            reconcile.setPackage(getActivity().getPackageName());
            getActivity().sendBroadcast(reconcile);
            Toast.makeText(getActivity(), R.string.auto_clicker_retry_requested, Toast.LENGTH_SHORT).show();
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
            // Some OEMs lack the overlay-settings activity; fall back to accessibility.
            openAccessibilitySettingsSafely();
        }
    }

    // Code 4 (unknown error): show the concrete, app-side recovery steps, with
    // "copy diagnostic" as a last resort — never the only option.
    private void showGeneralRecoveryInstructions() {
        if (getActivity() == null) return;
        final int code = lastDiagCode;
        String msg = getString(R.string.auto_clicker_reopen_accessibility_hint) + "\n"
                + getString(R.string.auto_clicker_restart_app_hint) + "\n"
                + getString(R.string.auto_clicker_restart_device_hint) + "\n"
                + getString(R.string.auto_clicker_clear_cache_hint);
        new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.auto_clicker_diagnostic_title, code))
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.auto_clicker_copy_diagnostics,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                copyDiagnosticToClipboard(code);
                            }
                        })
                .show();
    }

    @Subscribe
    public void onEngineChanged(Engine.ChangedEvent e) {
        prepareNumberFormatExamplesPreference();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adView != null) {
            adView.resume();
        }
        reconcileAutoClickerState();
    }

    // Sync the switch + summary to the persisted state when the settings page resumes.
    // The checkbox tracks the user's intent; the summary reflects the real effective state
    // the service last reported.
    private void reconcileAutoClickerState() {
        final SwitchPreferenceCompat ep =
                (SwitchPreferenceCompat) findPreference(Preferences.AutoClicker.intent.getKey());
        if (ep == null) return;
        boolean intent = Preferences.AutoClicker.intent.getPreference(preferences);
        // The checkbox is a live view of the real state (see updateAutoClickerEnabledSummary),
        // so we don't force-set it here; it is reconciled by that method.
        updateAutoClickerEnabledSummary(ep);
        // Nudge the service to re-evaluate now that we are back from the accessibility
        // settings screen. This is the timing safety net for "I toggled the switch and
        // granted accessibility but the circles never appeared" on some ROMs.
        if (intent) {
            try {
                Intent reconcile = new Intent(getActivity(), AutoClickerService.class);
                reconcile.setAction(AutoClickerService.ACTION_RECONCILE);
                reconcile.setPackage(getActivity().getPackageName());
                getActivity().sendBroadcast(reconcile);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onPause() {
        if (adView != null) {
            adView.pause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (adView != null) {
            adView.destroy();
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        bus.unregister(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }


    protected void onShowAd(boolean show) {
        if (getView() == null) {
            return;
        }

        final View root = getView();
        if (!(root instanceof ViewGroup)) return;

        final ViewGroup container = (ViewGroup) root;
        if (show) {
            if (adView != null) return;
            adView = new AdView(getActivity());
            adView.show();
            container.addView(adView);
        } else {
            if (adView == null) return;
            container.removeView(adView);
            adView.hide();
            adView = null;
        }
    }

}
