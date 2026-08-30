package org.solovyev.android.calculator.autoclicker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoClickerPlatformTest {

    @Test
    public void gestureAutotapStartsAtAndroidSeven() {
        assertFalse(AutoClickerPlatform.isSupportedSdk(23));
        assertTrue(AutoClickerPlatform.isSupportedSdk(24));
        assertTrue(AutoClickerPlatform.isSupportedSdk(36));
    }
}
