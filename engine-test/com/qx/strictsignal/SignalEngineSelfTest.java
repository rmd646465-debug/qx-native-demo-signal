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

    private static List<SignalEngine.CandlePoint> shock() {
        List<SignalEngine.CandlePoint> candles = trend(1);
        SignalEngine.CandlePoint previous = candles.get(candles.size() - 2);
        candles.set(candles.size() - 1,
                new SignalEngine.CandlePoint(41f * 12f, previous.price + 20f, 2f, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> persistentTrend(int direction) {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < 42; i++) {
            float delta;
            if (i < 28) delta = (i % 2 == 0 ? 4f : -0.5f) * direction;
            else delta = (i % 2 == 1 ? 1f : -0.5f) * direction;
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
        SignalEngine.Decision shockWait = SignalEngine.analyze(shock());
        SignalEngine.Decision longUp = SignalEngine.analyze(persistentTrend(1));
        SignalEngine.Decision longDown = SignalEngine.analyze(persistentTrend(-1));

        require("UP".equals(up.direction), "rising mirror must produce UP: " + up.detail);
        require("DOWN".equals(down.direction), "falling mirror must produce DOWN: " + down.detail);
        require("WAIT".equals(wait.direction), "flat market must produce WAIT: " + wait.detail);
        require("WAIT".equals(shockWait.direction),
                "one-candle volatility shock must produce WAIT: " + shockWait.detail);
        require(up.bullishPoints == down.bearishPoints,
                "mirrored bullish/bearish points must be equal");
        require(up.bearishPoints == down.bullishPoints,
                "mirrored opposing points must be equal");
        require(up.strength == down.strength, "mirrored setup strength must be equal");
        require(longUp.expiryMinutes == longDown.expiryMinutes,
                "mirrored expiry horizons must be equal");
        require(longUp.expiryMinutes >= 3,
                "persistent closed-candle trend must unlock a longer horizon: " + longUp.detail);

        System.out.println("PASS: UP/DOWN mirror symmetry, choppy WAIT and shock WAIT filters");
        System.out.println("UP   " + up.detail + " • strength " + up.strength);
        System.out.println("DOWN " + down.detail + " • strength " + down.strength);
        System.out.println("WAIT " + wait.detail);
        System.out.println("SHOCK " + shockWait.detail);
        System.out.println("HORIZON " + longUp.expiryMinutes + "m • " + longUp.detail);
    }
}
