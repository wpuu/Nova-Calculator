package org.solovyev.android.calculator;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.solovyev.android.calculator.analytics.NovaProductAnalytics;
import org.solovyev.android.calculator.preferences.PreferencesActivity;
import org.solovyev.android.calculator.wizard.CalculatorWizards;
import org.solovyev.android.wizard.Wizard;
import org.solovyev.android.wizard.Wizards;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static org.solovyev.android.calculator.release.ReleaseNotes.hasReleaseNotes;
import static org.solovyev.android.wizard.WizardUi.*;

@Singleton
public class StartupHelper {

    private static final String AUTOTAP_CHOICE_SHOWN_V1 = "nova.autotap.choice-shown-v1";

    @Named(AppModule.PREFS_UI)
    @Inject
    SharedPreferences uiPreferences;
    @Inject
    SharedPreferences preferences;
    @Inject
    Wizards wizards;
    @Inject
    NovaProductAnalytics productAnalytics;

    @Inject
    public StartupHelper() {
    }

    public void onMainActivityOpened(@NonNull AppCompatActivity activity) {
        final SharedPreferences.Editor editor = uiPreferences.edit();
        final Integer opened = UiPreferences.opened.getPreference(uiPreferences);
        UiPreferences.opened.putPreference(editor, opened == null ? 1 : opened + 1);
        handleOnMainActivityOpened(activity, editor, opened == null ? 0 : opened);
        UiPreferences.appVersion.putPreference(editor, App.getAppVersionCode(activity));
        editor.apply();
    }

    /**
     * Shows a one-time product choice only after the inherited first-time wizard is complete.
     * This never requests Accessibility permission: choosing AutoTap only opens its settings page,
     * where the separate prominent disclosure remains mandatory before any system permission UI.
     */
    public void maybeShowAutoTapChoice(@NonNull AppCompatActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (uiPreferences.getBoolean(AUTOTAP_CHOICE_SHOWN_V1, false)) return;

        final Wizard wizard = wizards.getWizard(CalculatorWizards.FIRST_TIME_WIZARD);
        if (!wizard.isFinished()) return;

        final Integer opened = UiPreferences.opened.getPreference(uiPreferences);
        // This is acquisition onboarding, not a popup for long-time upgraded users.
        if (opened == null || opened > 3) return;

        uiPreferences.edit().putBoolean(AUTOTAP_CHOICE_SHOWN_V1, true).apply();
        new AlertDialog.Builder(activity, App.getTheme().alertDialogTheme)
                .setTitle(R.string.autotap_onboarding_title)
                .setMessage(R.string.autotap_onboarding_message)
                .setPositiveButton(R.string.autotap_onboarding_open, (dialog, which) -> {
                    productAnalytics.autoTapSettingsOpened(NovaProductAnalytics.EntrySource.OTHER);
                    activity.startActivity(PreferencesActivity.makeIntent(
                            activity,
                            R.xml.preferences_auto_clicker,
                            R.string.pref_auto_clicker_category));
                })
                .setNegativeButton(R.string.autotap_onboarding_calculator, null)
                .show();
    }

    private void handleOnMainActivityOpened(@NonNull final AppCompatActivity activity, @NonNull SharedPreferences.Editor editor, int opened) {
        final int currentVersion = App.getAppVersionCode(activity);
        final Wizard wizard = wizards.getWizard(CalculatorWizards.FIRST_TIME_WIZARD);
        if (wizard.isStarted() && !wizard.isFinished()) {
            continueWizard(wizards, wizard.getName(), activity);
            return;
        }

        if (!UiPreferences.appVersion.isSet(uiPreferences)) {
            // new start
            startWizard(wizards, activity);
            return;
        }

        final Integer savedVersion = UiPreferences.appVersion.getPreference(uiPreferences);
        if (savedVersion < currentVersion) {
            if (Preferences.Gui.showReleaseNotes.getPreference(preferences) && hasReleaseNotes(activity, savedVersion + 1)) {
                final Bundle bundle = new Bundle();
                bundle.putInt(CalculatorWizards.RELEASE_NOTES_VERSION, savedVersion);
                activity.startActivity(createLaunchIntent(wizards, CalculatorWizards.RELEASE_NOTES, activity, bundle));
                return;
            }
        }

        if (shouldShowRateUsDialog(opened)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity, App.getTheme().alertDialogTheme);
            builder.setPositiveButton(R.string.cpp_rateus_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        final Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("https://market.android.com/details?id=org.solovyev.android.calculator"));
                        activity.startActivity(intent);
                    } catch (ActivityNotFoundException ignored) {
                    }
                }
            });
            builder.setNegativeButton(R.string.cpp_rateus_cancel, null);
            builder.setMessage(activity.getString(R.string.cpp_rateus_message, activity.getString(R.string.cpp_app_name)));
            builder.setTitle(activity.getString(R.string.cpp_rateus_title, activity.getString(R.string.cpp_app_name)));
            builder.create().show();
            UiPreferences.rateUsShown.putPreference(editor, true);
        }
    }

    private boolean shouldShowRateUsDialog(int opened) {
        // 二开：隐藏「给高级计算器评分」弹窗，避免跳转原作者的 Google Play 商店页
        // （org.solovyev.android.calculator）。若要恢复评分功能，把此返回值改为
        // 「opened > 30 && !UiPreferences.rateUsShown.getPreference(uiPreferences)」即可。
        return false;
    }
}
