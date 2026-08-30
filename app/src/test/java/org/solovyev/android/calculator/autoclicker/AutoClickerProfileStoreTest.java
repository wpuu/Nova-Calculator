package org.solovyev.android.calculator.autoclicker;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AutoClickerProfileStoreTest {

    private SharedPreferences preferences;
    private AutoClickerProfileStore store;

    @Before
    public void setUp() {
        preferences = PreferenceManager.getDefaultSharedPreferences(
                RuntimeEnvironment.getApplication());
        preferences.edit().clear().commit();
        store = new AutoClickerProfileStore(preferences);
    }

    @Test
    public void saveAndApplyRestoresTimingAndNormalizedPositions() throws Exception {
        preferences.edit()
                .putString(AutoClickerProfileStore.KEY_INTERVAL, "120")
                .putString(AutoClickerProfileStore.KEY_DURATION, "90")
                .putString(AutoClickerProfileStore.KEY_POSITIONS, "r:0.100000,0.200000;r:0.800000,0.700000")
                .putString(AutoClickerProfileStore.KEY_FLOATING_POSITION, "r:0.500000,0.100000")
                .commit();

        store.saveCurrent("Game setup", AutoClickerProfileStore.FREE_PROFILE_LIMIT);

        preferences.edit()
                .putString(AutoClickerProfileStore.KEY_INTERVAL, "40")
                .putString(AutoClickerProfileStore.KEY_DURATION, "10")
                .putString(AutoClickerProfileStore.KEY_POSITIONS, "")
                .putString(AutoClickerProfileStore.KEY_FLOATING_POSITION, "")
                .commit();

        assertTrue(store.apply("Game setup"));
        assertEquals("120", preferences.getString(AutoClickerProfileStore.KEY_INTERVAL, ""));
        assertEquals("90", preferences.getString(AutoClickerProfileStore.KEY_DURATION, ""));
        assertEquals("r:0.100000,0.200000;r:0.800000,0.700000",
                preferences.getString(AutoClickerProfileStore.KEY_POSITIONS, ""));
        assertEquals("r:0.500000,0.100000",
                preferences.getString(AutoClickerProfileStore.KEY_FLOATING_POSITION, ""));
    }

    @Test
    public void freeTierAllowsOneProfileButReplacementDoesNotConsumeAnotherSlot() throws Exception {
        store.saveCurrent("Main", AutoClickerProfileStore.FREE_PROFILE_LIMIT);

        preferences.edit().putString(AutoClickerProfileStore.KEY_INTERVAL, "250").commit();
        store.saveCurrent("main", AutoClickerProfileStore.FREE_PROFILE_LIMIT);

        List<AutoClickerProfileStore.Profile> profiles = store.list();
        assertEquals(1, profiles.size());
        assertEquals("250", profiles.get(0).interval);

        boolean rejected = false;
        try {
            store.saveCurrent("Second", AutoClickerProfileStore.FREE_PROFILE_LIMIT);
        } catch (AutoClickerProfileStore.ProfileLimitReachedException expected) {
            rejected = true;
            assertEquals(1, expected.limit);
        }
        assertTrue(rejected);
    }

    @Test
    public void deleteAndMalformedStorageFailClosed() throws Exception {
        store.saveCurrent("A", AutoClickerProfileStore.PRO_PROFILE_LIMIT);
        store.saveCurrent("B", AutoClickerProfileStore.PRO_PROFILE_LIMIT);
        assertTrue(store.delete("A"));
        assertFalse(store.delete("missing"));
        assertEquals(1, store.list().size());

        preferences.edit().putString(AutoClickerProfileStore.KEY_PROFILES, "not-json").commit();
        assertTrue(store.list().isEmpty());
        assertFalse(store.apply("B"));
    }
}
