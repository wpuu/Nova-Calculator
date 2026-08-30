package org.solovyev.android.calculator.autoclicker;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class AutoClickerCompatibilityTest {

    @Test
    public void stationaryPathHasZeroLength() {
        Path path = AutoClickerGestureFactory.stationaryPath(320, 640);
        PathMeasure measure = new PathMeasure(path, false);

        assertEquals(0f, measure.getLength(), 0f);
    }

    @Test
    public void negativeTapCoordinatesAreClampedToScreenOrigin() {
        Path path = AutoClickerGestureFactory.stationaryPath(-10, -20);
        float[] position = new float[2];
        PathMeasure measure = new PathMeasure(path, false);
        measure.getPosTan(0f, position, null);

        assertEquals(0f, position[0], 0f);
        assertEquals(0f, position[1], 0f);
    }

    @Test
    public void displayBoundsChooseStrictlyLargerAreaToEscapeTransientHalfScreen() {
        Rect transientHalf = new Rect(0, 0, 1080, 1200);
        Rect fullLandscape = new Rect(0, 0, 2400, 1080);

        Rect selected = AutoClickerDisplayBounds.chooseBest(transientHalf, fullLandscape);

        assertEquals(2400, selected.width());
        assertEquals(1080, selected.height());
    }

    @Test
    public void equalAreaPrefersCurrentOrientation() {
        Rect currentLandscape = new Rect(0, 0, 2400, 1080);
        Rect maximumPortrait = new Rect(0, 0, 1080, 2400);

        Rect selected = AutoClickerDisplayBounds.chooseBest(currentLandscape, maximumPortrait);

        assertEquals(2400, selected.width());
        assertEquals(1080, selected.height());
    }
}
