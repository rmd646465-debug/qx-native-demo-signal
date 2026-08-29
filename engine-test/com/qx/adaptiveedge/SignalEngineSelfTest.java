package com.qx.adaptiveedge;

import java.util.ArrayList;
import java.util.List;

/** Run with plain javac/java; no Android SDK is required. */
public final class SignalEngineSelfTest {
    private static List<SignalEngine.CandlePoint> trend(int direction, int count) {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < count; i++) {
            float delta = (i % 3 == 2 ? -0.66f : 1f) * direction;
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
        List<SignalEngine.CandlePoint> candles = trend(1, 42);
        SignalEngine.CandlePoint previous = candles.get(candles.size() - 2);
        candles.set(candles.size() - 1,
                new SignalEngine.CandlePoint(41f * 12f, previous.close + 20f, 2f, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> rangeReversal(int direction) {
        float[] values = {
                0, 1, 2, 1, 0, -1, -2, -1,
                0, 1, 2, 1, 0, -1, -2, -1,
                0, 1, 2, 1, 0, -1, -2, -1,
                1, 0, -1, -2, -3, -4, -7, -6, -5, -4
        };
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float previous = 100f + values[0] * direction;
        candles.add(new SignalEngine.CandlePoint(0f, previous, 2f, direction > 0));
        for (int i = 1; i < values.length; i++) {
            float price = 100f + values[i] * direction;
            float delta = price - previous;
            candles.add(new SignalEngine.CandlePoint(i * 12f, price, 2f, delta > 0));
            previous = price;
        }
        return candles;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SignalEngine.Decision shortUp = SignalEngine.analyze(trend(1, 24));
        SignalEngine.Decision shortDown = SignalEngine.analyze(trend(-1, 24));
        SignalEngine.Decision mediumUp = SignalEngine.analyze(trend(1, 32));
        SignalEngine.Decision longUp = SignalEngine.analyze(trend(1, 48));
        SignalEngine.Decision longDown = SignalEngine.analyze(trend(-1, 48));
        SignalEngine.Decision rangeUp = SignalEngine.analyze(rangeReversal(1));
        SignalEngine.Decision rangeDown = SignalEngine.analyze(rangeReversal(-1));
        SignalEngine.Decision wait = SignalEngine.analyze(flat());
        SignalEngine.Decision shockWait = SignalEngine.analyze(shock());

        require("UP".equals(shortUp.direction), "rising mirror must produce UP: " + shortUp.detail);
        require("DOWN".equals(shortDown.direction), "falling mirror must produce DOWN: " + shortDown.detail);
        require(shortUp.bullishPoints == shortDown.bearishPoints,
                "mirrored bullish/bearish points must match");
        require(shortUp.bearishPoints == shortDown.bullishPoints,
                "mirrored opposing points must match");
        require(shortUp.strength == shortDown.strength,
                "mirrored trend setup strength must match");
        require(shortUp.expiryMinutes == 2, "24-candle trend must use 2m horizon");
        require(mediumUp.expiryMinutes == 3,
                "32-candle persistent trend must use 3m horizon: " + mediumUp.detail);
        require(longUp.expiryMinutes == 5,
                "48-candle persistent trend must use 5m horizon: " + longUp.detail);
        require(longUp.expiryMinutes == longDown.expiryMinutes,
                "mirrored long horizons must match");

        require("UP".equals(rangeUp.direction),
                "lower range rejection must produce UP: " + rangeUp.detail);
        require("DOWN".equals(rangeDown.direction),
                "upper range rejection must produce DOWN: " + rangeDown.detail);
        require(SignalEngine.RANGE_PROFILE.equals(rangeUp.profile),
                "range chart must select range profile");
        require(rangeUp.strength == rangeDown.strength,
                "mirrored range setup strength must match");
        require(rangeUp.expiryMinutes == 2 && rangeDown.expiryMinutes == 2,
                "range reversals must stay on 2m horizon");

        require("WAIT".equals(wait.direction), "flat market must WAIT: " + wait.detail);
        require("WAIT".equals(shockWait.direction),
                "one-candle volatility shock must WAIT: " + shockWait.detail);

        System.out.println("PASS: mirror symmetry, adaptive profiles, WAIT safety and 2/3/5m horizons");
        System.out.println("TREND 2m " + shortUp.detail);
        System.out.println("TREND 3m " + mediumUp.detail);
        System.out.println("TREND 5m " + longUp.detail);
        System.out.println("RANGE 2m " + rangeUp.detail);
        System.out.println("WAIT " + wait.profile + " • " + wait.detail);
        System.out.println("SHOCK " + shockWait.profile + " • " + shockWait.detail);
    }
}
