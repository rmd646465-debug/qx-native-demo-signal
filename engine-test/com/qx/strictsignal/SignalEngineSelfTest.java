package com.qx.strictsignal;

import java.util.ArrayList;
import java.util.List;

/** Run with plain javac/java; no Android SDK is required. */
public final class SignalEngineSelfTest {
    private static List<SignalEngine.CandlePoint> trend(int direction) {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < 42; i++) {
            int phase = i % 3;
            float delta = (phase == 2 ? -1f : 1f) * direction;
            price += delta;
            candles.add(new SignalEngine.CandlePoint(i * 12f, price, 2f, delta > 0));
        }
        return candles;
    }

    private static List<SignalEngine.CandlePoint> flat() {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < 42; i++) {
            float delta = i % 2 == 0 ? 1f : -1f;
            price += delta;
            candles.add(new SignalEngine.CandlePoint(i * 12f, price, 2f, delta > 0));
        }
        return candles;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SignalEngine.Decision up = SignalEngine.analyze(trend(1));
        SignalEngine.Decision down = SignalEngine.analyze(trend(-1));
        SignalEngine.Decision wait = SignalEngine.analyze(flat());

        require("UP".equals(up.direction), "rising mirror must produce UP: " + up.detail);
        require("DOWN".equals(down.direction), "falling mirror must produce DOWN: " + down.detail);
        require("WAIT".equals(wait.direction), "flat market must produce WAIT: " + wait.detail);
        require(up.bullishPoints == down.bearishPoints,
                "mirrored bullish/bearish points must be equal");
        require(up.bearishPoints == down.bullishPoints,
                "mirrored opposing points must be equal");
        require(up.strength == down.strength, "mirrored setup strength must be equal");

        System.out.println("PASS: UP/DOWN mirror symmetry and WAIT filter");
        System.out.println("UP   " + up.detail + " • strength " + up.strength);
        System.out.println("DOWN " + down.detail + " • strength " + down.strength);
        System.out.println("WAIT " + wait.detail);
    }
}
