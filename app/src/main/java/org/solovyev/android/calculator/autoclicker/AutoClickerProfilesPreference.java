package org.solovyev.android.calculator.autoclicker;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.solovyev.android.calculator.CalculatorApplication;
import org.solovyev.android.calculator.R;
import org.solovyev.android.calculator.entitlement.EntitlementSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Lightweight settings UI for saved deterministic AutoTap setups. */
public final class AutoClickerProfilesPreference extends Preference {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AutoClickerProfilesPreference(@NonNull Context context) {
        super(context);
    }

    public AutoClickerProfilesPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoClickerProfilesPreference(@NonNull Context context,
                                         @Nullable AttributeSet attrs,
                                         int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        super.onClick();
        showProfilesMenu();
    }

    private void showProfilesMenu() {
        final AutoClickerProfileStore store = store();
        final List<AutoClickerProfileStore.Profile> profiles = store.list();
        final List<String> labels = new ArrayList<>();
        labels.add(getContext().getString(R.string.auto_clicker_profile_save_current));
        for (AutoClickerProfileStore.Profile profile : profiles) {
            labels.add(profile.name);
        }

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.auto_clicker_profiles_title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        promptSave(store, profiles.size());
                    } else {
                        showProfileActions(store, profiles.get(which - 1));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptSave(@NonNull AutoClickerProfileStore store, int currentCount) {
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(getContext().getString(
                R.string.auto_clicker_profile_default_name, currentCount + 1));

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.auto_clicker_profile_save_current)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final String name = input.getText() == null
                            ? "" : input.getText().toString();
                    saveCurrent(store, name, currentCount + 1);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveCurrent(@NonNull AutoClickerProfileStore store,
                             @NonNull String requestedName,
                             int fallbackIndex) {
        final String name = requestedName.trim().isEmpty()
                ? getContext().getString(R.string.auto_clicker_profile_default_name, fallbackIndex)
                : requestedName;
        try {
            store.saveCurrent(name, profileLimit());
            Toast.makeText(getContext(), R.string.auto_clicker_profile_saved, Toast.LENGTH_SHORT).show();
            notifyChanged();
        } catch (AutoClickerProfileStore.ProfileLimitReachedException e) {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.auto_clicker_profile_limit_title)
                    .setMessage(isPro()
                            ? R.string.auto_clicker_profile_limit_pro
                            : R.string.auto_clicker_profile_limit_free)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void showProfileActions(@NonNull AutoClickerProfileStore store,
                                    @NonNull AutoClickerProfileStore.Profile profile) {
        final String[] actions = {
                getContext().getString(R.string.auto_clicker_profile_load),
                getContext().getString(R.string.auto_clicker_profile_overwrite),
                getContext().getString(R.string.auto_clicker_profile_delete)
        };
        new AlertDialog.Builder(getContext())
                .setTitle(profile.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (store.apply(profile.name)) {
                            restartOverlayIfEnabled();
                            Toast.makeText(getContext(), R.string.auto_clicker_profile_loaded,
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 1) {
                        try {
                            store.saveCurrent(profile.name, profileLimit());
                            Toast.makeText(getContext(), R.string.auto_clicker_profile_saved,
                                    Toast.LENGTH_SHORT).show();
                        } catch (AutoClickerProfileStore.ProfileLimitReachedException ignored) {
                            // Replacing an existing profile never consumes an additional slot.
                        }
                    } else if (which == 2) {
                        confirmDelete(store, profile);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(@NonNull AutoClickerProfileStore store,
                               @NonNull AutoClickerProfileStore.Profile profile) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.auto_clicker_profile_delete)
                .setMessage(getContext().getString(R.string.auto_clicker_profile_delete_confirm,
                        profile.name))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    store.delete(profile.name);
                    notifyChanged();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void restartOverlayIfEnabled() {
        final SharedPreferences preferences = preferences();
        if (!preferences.getBoolean("auto_clicker_intent", false)) return;
        // The service recreates overlays when the user intent transitions off -> on. This makes
        // newly loaded normalized positions visible immediately and also safely stops any active
        // gesture before changing targets.
        preferences.edit().putBoolean("auto_clicker_intent", false).apply();
        mainHandler.postDelayed(() ->
                preferences.edit().putBoolean("auto_clicker_intent", true).apply(), 120L);
    }

    private int profileLimit() {
        return isPro() ? AutoClickerProfileStore.PRO_PROFILE_LIMIT
                : AutoClickerProfileStore.FREE_PROFILE_LIMIT;
    }

    private boolean isPro() {
        try {
            final Context applicationContext = getContext().getApplicationContext();
            if (!(applicationContext instanceof CalculatorApplication)) return false;
            final EntitlementSnapshot snapshot = ((CalculatorApplication) applicationContext)
                    .getComponent()
                    .billingCoordinator()
                    .getEntitlementSnapshot();
            return snapshot != null && (snapshot.hasProLifetime() || snapshot.hasAiPlus());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @NonNull
    private AutoClickerProfileStore store() {
        return new AutoClickerProfileStore(preferences());
    }

    @NonNull
    private SharedPreferences preferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }
}
