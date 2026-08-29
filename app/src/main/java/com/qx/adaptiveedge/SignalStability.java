package com.qx.adaptiveedge;

import java.util.List;

/** Pure-Java agreement gate for repeated chart reads. */
final class SignalStability {
    static final int REQUIRED_READS = 3;
    static final int MAX_STRENGTH_SPREAD = 5;
    static final int MAX_FLOW_SPREAD = 8;

    static final class Read {
        final int candles;
        final String direction;
        final String profile;
        final int expiryMinutes;
        final int strength;
        final int flowBias;

        Read(int candles, String direction, String profile, int expiryMinutes,
             int strength, int flowBias) {
            this.candles = candles;
            this.direction = direction;
            this.profile = profile;
            this.expiryMinutes = expiryMinutes;
            this.strength = strength;
            this.flowBias = flowBias;
        }
    }

    private SignalStability() {}

    static boolean agrees(List<Read> reads) {
        if (reads.size() != REQUIRED_READS) return false;
        Read first = reads.get(0);
        int minimumStrength = first.strength;
        int maximumStrength = first.strength;
        int minimumFlow = first.flowBias;
        int maximumFlow = first.flowBias;
        for (int i = 1; i < reads.size(); i++) {
            Read next = reads.get(i);
            if (first.candles != next.candles
                    || !first.direction.equals(next.direction)
                    || !first.profile.equals(next.profile)
                    || first.expiryMinutes != next.expiryMinutes) return false;
            minimumStrength = Math.min(minimumStrength, next.strength);
            maximumStrength = Math.max(maximumStrength, next.strength);
            minimumFlow = Math.min(minimumFlow, next.flowBias);
            maximumFlow = Math.max(maximumFlow, next.flowBias);
        }
        return maximumStrength - minimumStrength <= MAX_STRENGTH_SPREAD
                && maximumFlow - minimumFlow <= MAX_FLOW_SPREAD;
    }
}
