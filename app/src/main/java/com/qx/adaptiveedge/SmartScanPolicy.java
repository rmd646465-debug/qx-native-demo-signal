package com.qx.adaptiveedge;

/** Pure-Java timing and eligibility rules for continuous demo chart scanning. */
final class SmartScanPolicy {
    static final long MINUTE_MS = 60_000L;
    static final long CLOSE_WINDOW_START_MS = 900L;
    static final long CLOSE_WINDOW_END_MS = 15_000L;
    static final long FRESH_ENTRY_END_MS = 18_000L;
    static final int MIN_PAYOUT_PERCENT = 82;

    private SmartScanPolicy() { }

    static boolean isCloseWindow(long now) {
        long position = Math.floorMod(now, MINUTE_MS);
        return position >= CLOSE_WINDOW_START_MS
                && position <= CLOSE_WINDOW_END_MS;
    }

    static long delayToCloseWindow(long now) {
        long position = Math.floorMod(now, MINUTE_MS);
        if (position < CLOSE_WINDOW_START_MS) {
            return CLOSE_WINDOW_START_MS - position;
        }
        return MINUTE_MS - position + CLOSE_WINDOW_START_MS;
    }

    static long delayToNextClose(long now) {
        long position = Math.floorMod(now, MINUTE_MS);
        return MINUTE_MS - position + CLOSE_WINDOW_START_MS;
    }

    static boolean eligiblePayout(int payoutPercent) {
        return payoutPercent == 0 || payoutPercent >= MIN_PAYOUT_PERCENT;
    }
}
