package org.solovyev.android.calculator.entitlement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;

public class DefaultEntitlementManagerTest {

    @Test
    public void defaultsToFreeWithoutCommercialRights() {
        DefaultEntitlementManager manager = new DefaultEntitlementManager();

        assertFalse(manager.has(Entitlement.PRO_LIFETIME));
        assertFalse(manager.has(Entitlement.AI_PLUS));
    }

    @Test
    public void proLifetimeAndAiPlusCanCoexist() {
        EntitlementSource source = new EntitlementSource() {
            @Override
            public EntitlementSnapshot load() {
                return EntitlementSnapshot.of(
                        EnumSet.of(Entitlement.PRO_LIFETIME, Entitlement.AI_PLUS),
                        "test",
                        1L);
            }
        };
        DefaultEntitlementManager manager = new DefaultEntitlementManager(source);

        assertTrue(manager.has(Entitlement.PRO_LIFETIME));
        assertTrue(manager.has(Entitlement.AI_PLUS));
    }

    @Test
    public void nullSourceSnapshotFailsClosedToFree() {
        EntitlementSource source = new EntitlementSource() {
            @Override
            public EntitlementSnapshot load() {
                return null;
            }
        };
        DefaultEntitlementManager manager = new DefaultEntitlementManager(source);

        assertFalse(manager.has(Entitlement.PRO_LIFETIME));
        assertFalse(manager.has(Entitlement.AI_PLUS));
    }
}
