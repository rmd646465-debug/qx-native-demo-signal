package com.qx.adaptiveedge;

import java.util.Locale;

/**
 * Pure-Java demo-session ledger for the fixed $2 / 82% target experiment.
 * It is deliberately a stop controller, not a prediction or staking system.
 */
final class DailyTargetSession {
    static final int STAKE_CENTS = 200;
    static final int WIN_PROFIT_CENTS = 164;
    static final int TARGET_CENTS = 500;
    static final int LOSS_LIMIT_CENTS = -400;
    static final int MAX_TRADES = 10;
    static final int MAX_CONSECUTIVE_LOSSES = 2;

    enum StopReason {
        NONE,
        TARGET_REACHED,
        LOSS_LIMIT,
        TRADE_LIMIT
    }

    private int wins;
    private int losses;
    private int consecutiveLosses;

    DailyTargetSession() {
        this(0, 0, 0);
    }

    DailyTargetSession(int wins, int losses, int consecutiveLosses) {
        this.wins = Math.max(0, wins);
        this.losses = Math.max(0, losses);
        this.consecutiveLosses = Math.max(0,
                Math.min(this.losses, consecutiveLosses));
    }

    void record(boolean win) {
        if (isStopped()) {
            throw new IllegalStateException("daily demo session is already stopped");
        }
        if (win) {
            wins++;
            consecutiveLosses = 0;
        } else {
            losses++;
            consecutiveLosses++;
        }
    }

    int wins() {
        return wins;
    }

    int losses() {
        return losses;
    }

    int consecutiveLosses() {
        return consecutiveLosses;
    }

    int trades() {
        return wins + losses;
    }

    int profitCents() {
        return wins * WIN_PROFIT_CENTS - losses * STAKE_CENTS;
    }

    StopReason stopReason() {
        if (profitCents() >= TARGET_CENTS) return StopReason.TARGET_REACHED;
        if (profitCents() <= LOSS_LIMIT_CENTS
                || consecutiveLosses >= MAX_CONSECUTIVE_LOSSES) {
            return StopReason.LOSS_LIMIT;
        }
        if (trades() >= MAX_TRADES) return StopReason.TRADE_LIMIT;
        return StopReason.NONE;
    }

    boolean isStopped() {
        return stopReason() != StopReason.NONE;
    }

    String stopDetail() {
        switch (stopReason()) {
            case TARGET_REACHED:
                return "Demo target protected at " + formatMoney(profitCents());
            case LOSS_LIMIT:
                return "-$4 / two-loss safety limit reached at "
                        + formatMoney(profitCents());
            case TRADE_LIMIT:
                return "10-trade daily limit reached at "
                        + formatMoney(profitCents());
            default:
                return "Daily demo session is active";
        }
    }

    static String formatMoney(int cents) {
        String sign = cents > 0 ? "+" : cents < 0 ? "-" : "";
        int absolute = Math.abs(cents);
        return String.format(Locale.US, "%s$%d.%02d", sign,
                absolute / 100, absolute % 100);
    }
}
