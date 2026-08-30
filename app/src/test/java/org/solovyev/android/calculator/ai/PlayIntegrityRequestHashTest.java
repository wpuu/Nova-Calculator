package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PlayIntegrityRequestHashTest {

    @Test
    public void requestHashMatchesServerContractAndHidesInstallationId() {
        String first = PlayIntegrityRequestHash.forAnonymousSession("install-123");
        assertEquals("RxFjGZXMWfInp3uG5HTnyzBmXyDbLwJFuLKXwo__NAY", first);
        assertEquals(43, first.length());
        assertNotEquals(first, PlayIntegrityRequestHash.forAnonymousSession("install-456"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankInstallationIdIsRejected() {
        PlayIntegrityRequestHash.forAnonymousSession("   ");
    }
}
