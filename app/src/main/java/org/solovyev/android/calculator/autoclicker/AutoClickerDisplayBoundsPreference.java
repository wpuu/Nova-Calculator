package org.solovyev.android.calculator.autoclicker;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.solovyev.android.calculator.R;

/** Shows the most recent display bounds actually selected by the AutoTap coordinate helper. */
public final class AutoClickerDisplayBoundsPreference extends Preference {

    public AutoClickerDisplayBoundsPreference(@NonNull Context context) {
        super(context);
        init();
    }

    public AutoClickerDisplayBoundsPreference(@NonNull Context context,
                                              @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AutoClickerDisplayBoundsPreference(@NonNull Context context,
                                              @Nullable AttributeSet attrs,
                                              int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setPersistent(false);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        refreshSummary();
    }

    @Override
    protected void onClick() {
        super.onClick();
        refreshSummary();
    }

    private void refreshSummary() {
        final AutoClickerDisplayBounds.Bounds bounds = AutoClickerDisplayBounds.getLastRead();
        if (bounds == null) {
            setSummary(R.string.autotap_display_bounds_unknown);
            return;
        }
        setSummary(getContext().getString(
                R.string.autotap_display_bounds_summary, bounds.width, bounds.height));
    }
}
