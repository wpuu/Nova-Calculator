package org.solovyev.android.calculator.release;

import android.content.Context;
import android.util.SparseArray;

import org.solovyev.android.calculator.App;
import org.solovyev.android.calculator.R;
import org.solovyev.common.text.Strings;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/** Nova-owned release history for the commercial application. */
public final class ReleaseNotes {

    private static final SparseArray<ReleaseNote> map = new SparseArray<>();

    static {
        // Calculator++ 2.x release history intentionally does not belong to the Nova product.
        map.put(20001, ReleaseNote.make("0.2.0-alpha01", R.string.nova_release_notes_20001));
    }

    private ReleaseNotes() {
        throw new AssertionError();
    }

    @Nonnull
    public static String getReleaseNotes(@Nonnull Context context) {
        return getReleaseNotesString(context, 0);
    }

    @Nonnull
    public static String getReleaseNoteVersion(int version) {
        final ReleaseNote releaseNote = map.get(version);
        return releaseNote == null ? String.valueOf(version) : releaseNote.versionName;
    }

    @Nonnull
    public static String getReleaseNoteDescription(@Nonnull Context context, int version) {
        final ReleaseNote releaseNote = map.get(version);
        return releaseNote == null ? "" : getDescription(context, releaseNote.description);
    }

    @Nonnull
    public static String getReleaseNotesString(@Nonnull Context context, int minVersion) {
        final StringBuilder result = new StringBuilder();
        final String releaseNotesForTitle = context.getString(R.string.c_release_notes_for_title);
        final int currentVersionCode = App.getAppVersionCode(context);
        boolean first = true;

        for (int index = map.size() - 1; index >= 0; index--) {
            final int versionCode = map.keyAt(index);
            if (versionCode > currentVersionCode || versionCode < minVersion) {
                continue;
            }
            final ReleaseNote releaseNote = map.valueAt(index);
            final String descriptionHtml = getDescription(context, releaseNote.description);
            if (Strings.isEmpty(descriptionHtml)) {
                continue;
            }
            if (!first) {
                result.append("<br/><br/>");
            } else {
                first = false;
            }
            result.append("<b>")
                    .append(releaseNotesForTitle)
                    .append(releaseNote.versionName)
                    .append("</b><br/><br/>");
            result.append(descriptionHtml);
        }
        return result.toString();
    }

    @Nonnull
    private static String getDescription(@Nonnull Context context, int description) {
        return context.getResources().getString(description).replace("\n", "<br/>");
    }

    @Nonnull
    public static List<Integer> getReleaseNotesVersions(@Nonnull Context context, int minVersion) {
        final List<Integer> releaseNotes = new ArrayList<>();
        final int currentVersionCode = App.getAppVersionCode(context);
        for (int index = map.size() - 1; index >= 0; index--) {
            final int versionCode = map.keyAt(index);
            if (versionCode > currentVersionCode || versionCode < minVersion) {
                continue;
            }
            final ReleaseNote releaseNote = map.valueAt(index);
            if (!Strings.isEmpty(context.getString(releaseNote.description))) {
                releaseNotes.add(versionCode);
            }
        }
        return releaseNotes;
    }

    public static boolean hasReleaseNotes(@Nonnull Context context, int minVersion) {
        return !getReleaseNotesVersions(context, minVersion).isEmpty();
    }
}
