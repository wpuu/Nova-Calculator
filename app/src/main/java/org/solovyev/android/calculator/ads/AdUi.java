package org.solovyev.android.calculator.ads;

import android.view.View;

import androidx.annotation.NonNull;

import org.solovyev.android.calculator.AdView;
import org.solovyev.android.calculator.R;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * No-op advertising coordinator for the Nova commercial branch.
 *
 * A Nova-owned advertising/consent implementation will replace this later. Keeping the
 * inherited view lifecycle API avoids touching unrelated calculator fragments while ensuring
 * that old Calculator++ billing and AdMob identities are not exercised during commercial tests.
 */
public class AdUi {

    @Nullable
    AdView adView;

    @Inject
    public AdUi() {
    }

    public void onCreate() {
    }

    public void onResume() {
        if (adView != null) {
            adView.hide();
        }
    }

    public void onCreateView(@NonNull View view) {
        adView = view.findViewById(R.id.cpp_ad);
        if (adView != null) {
            adView.hide();
        }
    }

    public void onPause() {
    }

    public void onDestroyView() {
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
    }

    public void onDestroy() {
    }
}
