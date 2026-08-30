package org.solovyev.android.calculator.ai;

/** Supplies Nova's app-local random installation identifier; never a hardware identifier. */
public interface InstallationIdProvider {
    String getInstallationId();
}
