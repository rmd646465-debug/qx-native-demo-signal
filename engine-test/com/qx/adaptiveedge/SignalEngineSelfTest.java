package com.qx.adaptiveedge;

import java.util.ArrayList;
import java.util.List;

/** Run with plain javac/java; no Android SDK is required. */
public final class SignalEngineSelfTest {
    private static List<SignalEngine.CandlePoint> flow(int count, float scale) {
        float[] pattern = {0.90f, 0.72f, -0.28f, 0.82f};
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < count; i++) {
            float delta = pattern[i % pattern.length] * scale;
            price += delta;
            candles.add(new SignalEngine.CandlePoint(i * 12f, price, 2f, delta > 0f));
        }
        return candles;
    }

    private static List<SignalEngine.CandlePoint> sweepUp() {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float[] values = {0f, 0.7f, -0.2f, 0.8f, -0.5f, 0.5f, -0.8f, 0.4f,
                -0.6f, 0.6f, -0.9f, 0.3f, -0.7f, 0.5f, -0.8f, 0.2f,
                -0.5f, 0.4f, -0.9f, 0.1f, -0.6f, 0.2f, -0.7f};
        float previous = 100f;
        for (int i = 0; i < values.length; i++) {
            float price = 100f + values[i];
            candles.add(new SignalEngine.CandlePoint(i * 12f, price, 2f,
                    price >= previous));
            previous = price;
        }
        candles.add(new SignalEngine.CandlePoint(23f * 12f,
                98.2f, 100.5f, 96.5f, 100.1f, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> flat() {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < 40; i++) {
            float delta = i % 2 == 0 ? 0.25f : -0.25f;
            price += delta;
            candles.add(new SignalEngine.CandlePoint(i * 12f, price, 2f, delta > 0f));
        }
        return candles;
    }

    private static List<SignalEngine.CandlePoint> shock() {
        List<SignalEngine.CandlePoint> candles = flow(36, 0.55f);
        float previous = candles.get(candles.size() - 2).close;
        candles.set(candles.size() - 1, new SignalEngine.CandlePoint(35f * 12f,
                previous, previous + 9f, previous - 1f, previous + 7f, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> mirror(
            List<SignalEngine.CandlePoint> source) {
        List<SignalEngine.CandlePoint> result = new ArrayList<>();
        for (SignalEngine.CandlePoint candle : source) {
            float open = 200f - candle.open;
            float high = 200f - candle.low;
            float low = 200f - candle.high;
            float close = 200f - candle.close;
            result.add(new SignalEngine.CandlePoint(candle.x, open, high, low, close,
                    close > open));
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void mirrored(SignalEngine.Decision up,
                                 SignalEngine.Decision down, String label) {
        require("UP".equals(up.direction), label + " must produce UP: " + up.detail);
        require("DOWN".equals(down.direction), label + " mirror must produce DOWN: "
                + down.detail);
        require(up.bullishPoints == down.bearishPoints,
                label + " mirrored primary points must match");
        require(up.bearishPoints == down.bullishPoints,
                label + " mirrored opposing points must match");
        require(up.strength == down.strength,
                label + " mirrored strength must match");
        require(up.expiryMinutes == down.expiryMinutes,
                label + " mirrored expiry must match");
        require(up.flowBias == -down.flowBias,
                label + " mirrored flow must be opposite");
    }

    public static void main(String[] args) {
        List<SignalEngine.CandlePoint> shortFlow = flow(24, 0.40f);
        List<SignalEngine.CandlePoint> mediumFlow = flow(32, 0.90f);
        List<SignalEngine.CandlePoint> longFlow = flow(48, 1.10f);
        List<SignalEngine.CandlePoint> sweep = sweepUp();

        SignalEngine.Decision shortUp = SignalEngine.analyze(shortFlow);
        SignalEngine.Decision shortDown = SignalEngine.analyze(mirror(shortFlow));
        SignalEngine.Decision mediumUp = SignalEngine.analyze(mediumFlow);
        SignalEngine.Decision mediumDown = SignalEngine.analyze(mirror(mediumFlow));
        SignalEngine.Decision longUp = SignalEngine.analyze(longFlow);
        SignalEngine.Decision longDown = SignalEngine.analyze(mirror(longFlow));
        SignalEngine.Decision sweepUp = SignalEngine.analyze(sweep);
        SignalEngine.Decision sweepDown = SignalEngine.analyze(mirror(sweep));
        SignalEngine.Decision flatWait = SignalEngine.analyze(flat());
        SignalEngine.Decision shockWait = SignalEngine.analyze(shock());

        mirrored(shortUp, shortDown, "short flow");
        mirrored(mediumUp, mediumDown, "medium flow");
        mirrored(longUp, longDown, "long flow");
        mirrored(sweepUp, sweepDown, "liquidity sweep");

        require(shortUp.expiryMinutes == 2,
                "controlled short flow must use 2m: " + shortUp.detail);
        require(mediumUp.expiryMinutes == 3,
                "strong medium flow must use 3m: " + mediumUp.detail);
        require(longUp.expiryMinutes == 5,
                "persistent long flow must use 5m: " + longUp.detail);
        require(SignalEngine.SWEEP_PROFILE.equals(sweepUp.profile),
                "sweep must select liquidity profile: " + sweepUp.detail);
        require(sweepUp.expiryMinutes == 2,
                "liquidity sweep must use 2m");
        require("WAIT".equals(flatWait.direction),
                "alternating flat chart must WAIT: " + flatWait.detail);
        require("WAIT".equals(shockWait.direction),
                "abnormal final range must WAIT: " + shockWait.detail);

        System.out.println("PASS: new price-structure engine, mirror symmetry, safety, 2/3/5m");
        System.out.println("FLOW 2m " + shortUp.detail);
        System.out.println("FLOW 3m " + mediumUp.detail);
        System.out.println("FLOW 5m " + longUp.detail);
        System.out.println("SWEEP 2m " + sweepUp.detail);
        System.out.println("WAIT " + flatWait.detail);
        System.out.println("SHOCK " + shockWait.detail);
    }
}
