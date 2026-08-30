package org.solovyev.android.calculator.billing;

import com.android.billingclient.api.Purchase;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class NovaPurchaseSnapshotAccumulatorTest {

    @Test
    public void emitsOnlyAfterBothProductTypesComplete() {
        NovaPurchaseSnapshotAccumulator accumulator = new NovaPurchaseSnapshotAccumulator();
        Purchase pro = mock(Purchase.class);
        Purchase aiPlus = mock(Purchase.class);
        long generation = accumulator.beginRefresh();

        assertNull(accumulator.accept(generation, false, Collections.singletonList(aiPlus)));
        List<Purchase> complete = accumulator.accept(
                generation, true, Collections.singletonList(pro));

        assertEquals(2, complete.size());
        assertSame(pro, complete.get(0));
        assertSame(aiPlus, complete.get(1));
        assertNull(accumulator.accept(generation, true, Collections.singletonList(pro)));
    }

    @Test
    public void emptySuccessfulHalvesProduceAuthoritativeEmptySnapshot() {
        NovaPurchaseSnapshotAccumulator accumulator = new NovaPurchaseSnapshotAccumulator();
        long generation = accumulator.beginRefresh();

        assertNull(accumulator.accept(generation, true, Collections.emptyList()));
        List<Purchase> complete = accumulator.accept(
                generation, false, Collections.emptyList());

        assertEquals(0, complete.size());
    }

    @Test
    public void failedGenerationNeverEmitsFromLateOtherHalf() {
        NovaPurchaseSnapshotAccumulator accumulator = new NovaPurchaseSnapshotAccumulator();
        Purchase purchase = mock(Purchase.class);
        long generation = accumulator.beginRefresh();

        assertNull(accumulator.accept(generation, true, Collections.singletonList(purchase)));
        accumulator.fail(generation);
        assertNull(accumulator.accept(
                generation, false, Collections.singletonList(purchase)));
    }

    @Test
    public void newerRefreshSupersedesOlderCallbacks() {
        NovaPurchaseSnapshotAccumulator accumulator = new NovaPurchaseSnapshotAccumulator();
        Purchase oldPurchase = mock(Purchase.class);
        Purchase newPro = mock(Purchase.class);
        Purchase newAiPlus = mock(Purchase.class);
        long oldGeneration = accumulator.beginRefresh();
        long newGeneration = accumulator.beginRefresh();

        assertNull(accumulator.accept(
                oldGeneration, true, Arrays.asList(oldPurchase)));
        assertNull(accumulator.accept(
                newGeneration, true, Collections.singletonList(newPro)));
        List<Purchase> complete = accumulator.accept(
                newGeneration, false, Collections.singletonList(newAiPlus));

        assertEquals(2, complete.size());
        assertSame(newPro, complete.get(0));
        assertSame(newAiPlus, complete.get(1));
    }
}
