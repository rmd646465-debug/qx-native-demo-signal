package com.qx.adaptiveedge;

import java.util.ArrayList;
import java.util.List;

/** Run with plain javac/java; no Android SDK is required. */
public final class SignalEngineSelfTest {
    private static SignalEngine.CandlePoint candle(int index, float open,
                                                    float close, boolean up) {
        float high = up ? close + 0.20f : close + 0.80f;
        float low = up ? close - 0.80f : close - 0.20f;
        return new SignalEngine.CandlePoint(index * 12f, open, high, low, close, up);
    }

    private static List<SignalEngine.CandlePoint> sweepUp() {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float previous = 100f;
        for (int i = 0; i < 22; i++) {
            float close = 100f + (i % 4 == 0 ? 0.18f : i % 4 == 2 ? -0.16f : 0.04f);
            boolean up = close >= previous;
            candles.add(candle(i, up ? close - 0.22f : close + 0.22f, close, up));
            previous = close;
        }
        for (int i = 22; i < 30; i++) {
            float close = 100f - (i - 21) * 0.18f;
            candles.add(candle(i, close + 0.22f, close, false));
        }
        candles.add(new SignalEngine.CandlePoint(30f * 12f,
                99.20f, 100.30f, 97.40f, 100.00f, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> retestUp() {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float previous = 100f;
        for (int i = 0; i < 36; i++) {
            float close = 100f + (i % 4 == 0 ? 0.16f : i % 4 == 2 ? -0.12f : 0.02f);
            boolean up = close >= previous;
            candles.add(candle(i, up ? close - 0.20f : close + 0.20f, close, up));
            previous = close;
        }
        candles.add(new SignalEngine.CandlePoint(36f * 12f,
                100.40f, 102.20f, 100.20f, 102.00f, true));
        candles.add(new SignalEngine.CandlePoint(37f * 12f,
                100.90f, 101.60f, 100.60f, 101.40f, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> pullbackUp(int count, float step) {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        int baseline = count - 10;
        for (int i = 0; i < baseline; i++) {
            float delta = i % 2 == 0 ? 0.08f : -0.08f;
            float open = price;
            price += delta;
            candles.add(candle(i, open, price, delta > 0f));
        }
        for (int i = baseline; i < count - 2; i++) {
            float open = price;
            price += step;
            candles.add(candle(i, open, price, true));
        }
        float trendClose = price;
        float pullbackClose = trendClose - 0.18f;
        candles.add(candle(count - 2, trendClose + 0.12f,
                pullbackClose, false));
        float resumeClose = trendClose + 0.20f;
        candles.add(candle(count - 1, pullbackClose, resumeClose, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> extendedFlow() {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < 34; i++) {
            float delta = i % 2 == 0 ? 0.08f : -0.06f;
            float open = price;
            price += delta;
            candles.add(candle(i, open, price, delta > 0f));
        }
        float[] tail = {0.48f, 0.48f, 0.48f, 0.48f, 0.10f, 0.65f};
        for (int i = 0; i < tail.length; i++) {
            float open = price;
            price += tail[i];
            candles.add(candle(34 + i, open, price, true));
        }
        return candles;
    }

    private static List<SignalEngine.CandlePoint> flat() {
        List<SignalEngine.CandlePoint> candles = new ArrayList<>();
        float price = 100f;
        for (int i = 0; i < 40; i++) {
            float delta = i % 2 == 0 ? 0.02f : -0.02f;
            float open = price;
            price += delta;
            candles.add(candle(i, open, price, delta > 0f));
        }
        return candles;
    }

    private static List<SignalEngine.CandlePoint> shock() {
        List<SignalEngine.CandlePoint> candles = retestUp();
        candles.set(candles.size() - 1, new SignalEngine.CandlePoint(37f * 12f,
                100.90f, 106.00f, 96.00f, 101.40f, true));
        return candles;
    }

    private static List<SignalEngine.CandlePoint> mirror(
            List<SignalEngine.CandlePoint> source) {
        List<SignalEngine.CandlePoint> result = new ArrayList<>();
        for (SignalEngine.CandlePoint item : source) {
            float open = 200f - item.open;
            float high = 200f - item.low;
            float low = 200f - item.high;
            float close = 200f - item.close;
            result.add(new SignalEngine.CandlePoint(item.x, open, high, low, close,
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

    private static void stabilityAndLockChecks() {
        List<SignalStability.Read> stable = List.of(
                new SignalStability.Read(31, "UP", SignalEngine.SWEEP_PROFILE,
                        2, 82, 30),
                new SignalStability.Read(31, "UP", SignalEngine.SWEEP_PROFILE,
                        2, 85, 38),
                new SignalStability.Read(31, "UP", SignalEngine.SWEEP_PROFILE,
                        2, 84, 34));
        require(SignalStability.agrees(stable), "three matching reads must pass");
        require(!SignalStability.agrees(List.of(
                        stable.get(0),
                        new SignalStability.Read(29, "UP", SignalEngine.SWEEP_PROFILE,
                                2, 82, 30), stable.get(2))),
                "28/29/31-style candle-count drift must fail");
        require(!SignalStability.agrees(List.of(
                        stable.get(0),
                        new SignalStability.Read(31, "DOWN", SignalEngine.SWEEP_PROFILE,
                                2, 82, -30), stable.get(2))),
                "UP/DOWN disagreement must fail");
        require(!SignalStability.agrees(List.of(
                        stable.get(0),
                        new SignalStability.Read(31, "UP", SignalEngine.SWEEP_PROFILE,
                                3, 82, 30), stable.get(2))),
                "2m/3m disagreement must fail");

        SignalLockBook<String> locks = new SignalLockBook<>();
        long now = 1_000_000L;
        SignalLockBook.Entry<String> first = locks.issueOrKeep(
                "USD/COP", "USD/COP", "UP-2M", now, now + 120_000L);
        SignalLockBook.Entry<String> reversal = locks.issueOrKeep(
                "USD/COP", "USD/COP", "DOWN-3M", now + 20_000L,
                now + 200_000L);
        require(first == reversal && "UP-2M".equals(reversal.payload),
                "active signal must refuse direction/expiry replacement");
        SignalLockBook.Entry<String> other = locks.issueOrKeep(
                "EUR/USD", "EUR/USD", "DOWN-3M", now + 20_000L,
                now + 200_000L);
        require(other != first && "DOWN-3M".equals(other.payload),
                "different currencies need independent locks");
        require(locks.active("USD/COP", now + 119_999L) == first,
                "signal must remain active until expiry");
        require(locks.active("USD/COP", now + 120_000L) == null,
                "signal must unlock at expiry");
        SignalLockBook.Entry<String> afterExpiry = locks.issueOrKeep(
                "USD/COP", "USD/COP", "DOWN-3M", now + 120_000L,
                now + 300_000L);
        require(afterExpiry != first && "DOWN-3M".equals(afterExpiry.payload),
                "new signal may be issued only after old expiry");
    }

    private static void dailyTargetChecks() {
        DailyTargetSession target = new DailyTargetSession();
        target.record(true);
        target.record(true);
        target.record(true);
        require(!target.isStopped() && target.profitCents() == 492,
                "three clean wins must remain below the $5 target");
        target.record(true);
        require(target.isStopped()
                        && target.stopReason()
                        == DailyTargetSession.StopReason.TARGET_REACHED,
                "four clean wins must protect the $5-$7 target range");
        require(target.profitCents() == 656 && target.trades() == 4,
                "four $2 wins at 82% must equal +$6.56");

        DailyTargetSession lossStop = new DailyTargetSession();
        lossStop.record(false);
        require(!lossStop.isStopped(), "one loss must not stop the session");
        lossStop.record(false);
        require(lossStop.isStopped()
                        && lossStop.stopReason()
                        == DailyTargetSession.StopReason.LOSS_LIMIT,
                "two consecutive losses must trigger the -$4 stop");
        require(lossStop.profitCents() == -400,
                "two $2 losses must equal -$4.00");

        DailyTargetSession tradeLimit = new DailyTargetSession();
        for (int i = 0; i < 5; i++) {
            tradeLimit.record(true);
            tradeLimit.record(false);
        }
        require(tradeLimit.isStopped()
                        && tradeLimit.stopReason()
                        == DailyTargetSession.StopReason.TRADE_LIMIT,
                "alternating results must stop at ten trades");
        require(tradeLimit.profitCents() == -180,
                "five wins and five losses must equal -$1.80 at 82%");

        boolean rejectedAfterStop = false;
        try {
            target.record(true);
        } catch (IllegalStateException expected) {
            rejectedAfterStop = true;
        }
        require(rejectedAfterStop, "a stopped daily session must reject more results");
        require("+$6.56".equals(DailyTargetSession.formatMoney(656))
                        && "-$4.00".equals(DailyTargetSession.formatMoney(-400)),
                "money formatting must be exact");
    }

    public static void main(String[] args) {
        List<SignalEngine.CandlePoint> sweep = sweepUp();
        List<SignalEngine.CandlePoint> retest = retestUp();
        List<SignalEngine.CandlePoint> shortPullback = pullbackUp(34, 0.12f);
        List<SignalEngine.CandlePoint> mediumPullback = pullbackUp(36, 0.24f);
        List<SignalEngine.CandlePoint> longPullback = pullbackUp(44, 0.24f);

        SignalEngine.Decision sweepDecision = SignalEngine.analyze(sweep);
        SignalEngine.Decision sweepMirror = SignalEngine.analyze(mirror(sweep));
        SignalEngine.Decision retestDecision = SignalEngine.analyze(retest);
        SignalEngine.Decision retestMirror = SignalEngine.analyze(mirror(retest));
        SignalEngine.Decision shortDecision = SignalEngine.analyze(shortPullback);
        SignalEngine.Decision shortMirror = SignalEngine.analyze(mirror(shortPullback));
        SignalEngine.Decision mediumDecision = SignalEngine.analyze(mediumPullback);
        SignalEngine.Decision mediumMirror = SignalEngine.analyze(mirror(mediumPullback));
        SignalEngine.Decision longDecision = SignalEngine.analyze(longPullback);
        SignalEngine.Decision longMirror = SignalEngine.analyze(mirror(longPullback));
        SignalEngine.Decision flowWait = SignalEngine.analyze(extendedFlow());
        SignalEngine.Decision flatWait = SignalEngine.analyze(flat());
        SignalEngine.Decision shockWait = SignalEngine.analyze(shock());

        mirrored(sweepDecision, sweepMirror, "liquidity sweep");
        mirrored(retestDecision, retestMirror, "break and retest");
        mirrored(shortDecision, shortMirror, "short pullback");
        mirrored(mediumDecision, mediumMirror, "medium pullback");
        mirrored(longDecision, longMirror, "long pullback");

        require(SignalEngine.SWEEP_PROFILE.equals(sweepDecision.profile),
                "sweep profile required");
        require(sweepDecision.expiryMinutes == 2, "sweep must use 2m");
        require(SignalEngine.RETEST_PROFILE.equals(retestDecision.profile),
                "retest profile required: " + retestDecision.detail);
        require(retestDecision.expiryMinutes == 3, "retest must use 3m");
        require(SignalEngine.PULLBACK_PROFILE.equals(shortDecision.profile),
                "pullback profile required");
        require(shortDecision.expiryMinutes == 2,
                "controlled pullback must use 2m: " + shortDecision.detail);
        require(mediumDecision.expiryMinutes == 3,
                "strong pullback must use 3m: " + mediumDecision.detail);
        require(longDecision.expiryMinutes == 5,
                "persistent pullback must use 5m: " + longDecision.detail);

        require("WAIT".equals(flowWait.direction),
                "extended flow alone must WAIT: " + flowWait.detail);
        require(flowWait.detail.contains("late entry"),
                "extended flow must trip chase filter: " + flowWait.detail);
        require("WAIT".equals(flatWait.direction),
                "flat chart must WAIT: " + flatWait.detail);
        require("WAIT".equals(shockWait.direction),
                "abnormal range must WAIT: " + shockWait.detail);

        boolean rejectedShortHistory = false;
        try {
            SignalEngine.analyze(flat().subList(0, SignalEngine.MIN_CANDLES - 1));
        } catch (IllegalArgumentException expected) {
            rejectedShortHistory = true;
        }
        require(rejectedShortHistory, "fewer than 30 closed candles must be rejected");
        stabilityAndLockChecks();
        dailyTargetChecks();

        System.out.println("PASS: engine, stability, expiry lock, daily target and safety");
        System.out.println("SWEEP " + sweepDecision.detail);
        System.out.println("RETEST " + retestDecision.detail);
        System.out.println("PULLBACK 2/3/5 " + shortDecision.expiryMinutes + "/"
                + mediumDecision.expiryMinutes + "/" + longDecision.expiryMinutes);
        System.out.println("FLOW WAIT " + flowWait.detail);
    }
}
