package org.solovyev.android.calculator.entitlement;

/**
 * Durable commercial rights owned by a user.
 *
 * PRO_LIFETIME and AI_PLUS are intentionally independent: a user may own either one,
 * both, or neither. Free access is the baseline and is represented by an empty set.
 */
public enum Entitlement {
    PRO_LIFETIME,
    AI_PLUS
}
