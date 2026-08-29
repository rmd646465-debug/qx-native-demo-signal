package com.qx.adaptiveedge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Closed-candle price-structure engine.
 *
 * <p>This version intentionally contains no EMA, MACD, RSI, Bollinger or old
 * confluence rules. It reads candle geometry, detects contextual price-action
 * structures and compares the latest three-candle pulse with earlier local
 * analogues on the same visible currency chart. Scores are setup-quality
 * scores, never claimed win probabilities.</p>
 */
final class SignalEngine {
    static final int MIN_CANDLES = 16;
    static final int MAX_CANDLES = 64;

    static final String SWEEP_PROFILE = "LIQUIDITY SWEEP";
    static final String RETEST_PROFILE = "BREAK + RETEST";
    static final String PULLBACK_PROFILE = "PULLBACK PULSE";
    static final String FLOW_PROFILE = "STRUCTURE FLOW";
    static final String ANALOG_PROFILE = "LOCAL PATTERN MATCH";

    static final class CandlePoint {
        final float x;
        final float open;
        final float high;
        final float low;
        final float close;
        final float price;
        final float height;
        final boolean up;

        /** Convenience constructor used by deterministic non-Android tests. */
        CandlePoint(float x, float price, float height, boolean up) {
            float safeHeight = Math.max(1f, Math.abs(height));
            float body = Math.max(0.6f, safeHeight * 0.55f);
            this.x = x;
            this.close = price;
            this.open = up ? price - body : price + body;
            this.high = Math.max(this.open, this.close) + safeHeight * 0.20f;
            this.low = Math.min(this.open, this.close) - safeHeight * 0.20f;
            this.price = price;
            this.height = Math.max(1f, this.high - this.low);
            this.up = up;
        }

        CandlePoint(float x, float open, float high, float low, float close,
                    boolean up) {
            this.x = x;
            this.open = open;
            this.high = Math.max(high, Math.max(open, close));
            this.low = Math.min(low, Math.min(open, close));
            this.close = close;
            this.price = close;
            this.height = Math.max(1f, this.high - this.low);
            this.up = up;
        }
    }

    static final class Decision {
        final String direction;
        final int strength;
        final int flowBias;
        final int bullishPoints;
        final int bearishPoints;
        final int expiryMinutes;
        final String profile;
        final int readiness;
        final String detail;

        Decision(String direction, int strength, int flowBias, int bullishPoints,
                 int bearishPoints, int expiryMinutes, String profile,
                 int readiness, String detail) {
            this.direction = direction;
            this.strength = strength;
            this.flowBias = flowBias;
            this.bullishPoints = bullishPoints;
            this.bearishPoints = bearishPoints;
            this.expiryMinutes = expiryMinutes;
            this.profile = profile;
            this.readiness = readiness;
            this.detail = detail;
        }
    }

    private static final class AnalogCandidate {
        final float distance;
        final int outcome;

        AnalogCandidate(float distance, int outcome) {
            this.distance = distance;
            this.outcome = outcome;
        }
    }

    private static final class AnalogResult {
        final int direction;
        final int horizon;
        final float agreement;
        final float averageDistance;
        final int samples;

        AnalogResult(int direction, int horizon, float agreement,
                     float averageDistance, int samples) {
            this.direction = direction;
            this.horizon = horizon;
            this.agreement = agreement;
            this.averageDistance = averageDistance;
            this.samples = samples;
        }
    }

    private SignalEngine() {}

    static Decision analyze(List<CandlePoint> input) {
        if (input.size() < MIN_CANDLES) {
            throw new IllegalArgumentException("need at least " + MIN_CANDLES + " candles");
        }
        List<CandlePoint> candles = input;
        if (candles.size() > MAX_CANDLES) {
            candles = new ArrayList<>(candles.subList(candles.size() - MAX_CANDLES,
                    candles.size()));
        }
        int size = candles.size();
        float[] closes = new float[size];
        List<Float> ranges = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            closes[i] = candles.get(i).close;
            ranges.add(candles.get(i).height);
        }
        float unit = Math.max(1f, median(ranges));

        CandlePoint last = candles.get(size - 1);
        CandlePoint breaker = candles.get(size - 2);
        float lastBody = Math.abs(last.close - last.open);
        float lastBodyRatio = lastBody / Math.max(1f, last.height);
        float lastCloseLocation = closeLocation(last);
        float lastUpperWick = last.high - Math.max(last.open, last.close);
        float lastLowerWick = Math.min(last.open, last.close) - last.low;
        float lastRangeRatio = last.height / unit;

        float move3 = move(closes, 3) / unit;
        float move6 = move(closes, 6) / unit;
        float move12 = move(closes, 12) / unit;
        float efficiency6 = efficiency(closes, 6);
        float efficiency12 = efficiency(closes, 12);
        float changeRate8 = changeRate(closes, 8);
        int colourBalance6 = colourBalance(candles, 6);

        float recentMedianRange = medianRange(candles, 6);
        float baselineMedianRange = medianRange(candles,
                Math.max(8, Math.min(size, 24)));
        float volatilityExpansion = recentMedianRange
                / Math.max(1f, baselineMedianRange);
        float averageCloseMove = averageCloseMove(closes, Math.min(size, 16));

        float priorHigh1 = highestHigh(candles, size - 9, size - 1);
        float priorLow1 = lowestLow(candles, size - 9, size - 1);
        float priorHigh2 = highestHigh(candles, size - 11, size - 2);
        float priorLow2 = lowestLow(candles, size - 11, size - 2);

        boolean bullishSweep = last.low < priorLow1 - unit * 0.03f
                && last.close > priorLow1 + unit * 0.03f
                && last.close > last.open
                && lastLowerWick >= Math.max(lastBody * 0.80f, unit * 0.18f)
                && lastCloseLocation >= 0.63f;
        boolean bearishSweep = last.high > priorHigh1 + unit * 0.03f
                && last.close < priorHigh1 - unit * 0.03f
                && last.close < last.open
                && lastUpperWick >= Math.max(lastBody * 0.80f, unit * 0.18f)
                && lastCloseLocation <= 0.37f;

        boolean bullishRetest = breaker.close > priorHigh2 + unit * 0.04f
                && last.low <= priorHigh2 + unit * 0.30f
                && last.close > priorHigh2 + unit * 0.02f
                && lastCloseLocation >= 0.55f
                && last.close >= last.open - unit * 0.08f;
        boolean bearishRetest = breaker.close < priorLow2 - unit * 0.04f
                && last.high >= priorLow2 - unit * 0.30f
                && last.close < priorLow2 - unit * 0.02f
                && lastCloseLocation <= 0.45f
                && last.close <= last.open + unit * 0.08f;

        int priorTrendStart = Math.max(0, size - 9);
        int priorTrendEnd = size - 3;
        float priorTrendMove = (closes[priorTrendEnd] - closes[priorTrendStart]) / unit;
        float priorTrendEfficiency = efficiencyBetween(closes, priorTrendStart,
                priorTrendEnd);
        float pullbackBody = breaker.close - breaker.open;
        boolean bullishPullback = priorTrendMove > 0.52f
                && priorTrendEfficiency >= 0.39f
                && pullbackBody < -unit * 0.08f
                && last.close > last.open
                && last.close > (breaker.open + breaker.close) * 0.5f
                && move3 > 0.12f && lastCloseLocation >= 0.62f;
        boolean bearishPullback = priorTrendMove < -0.52f
                && priorTrendEfficiency >= 0.39f
                && pullbackBody > unit * 0.08f
                && last.close < last.open
                && last.close < (breaker.open + breaker.close) * 0.5f
                && move3 < -0.12f && lastCloseLocation <= 0.38f;

        boolean bullishFlow = move6 > 0.48f && efficiency6 >= 0.42f
                && colourBalance6 >= 2 && last.close > last.open
                && lastBodyRatio >= 0.32f && lastCloseLocation >= 0.60f;
        boolean bearishFlow = move6 < -0.48f && efficiency6 >= 0.42f
                && colourBalance6 <= -2 && last.close < last.open
                && lastBodyRatio >= 0.32f && lastCloseLocation <= 0.40f;

        AnalogResult analog = bestAnalog(candles, unit);
        int bullish = 0;
        int bearish = 0;
        if (move3 > 0.18f) bullish += 2;
        else if (move3 < -0.18f) bearish += 2;
        if (move6 > 0.42f) bullish += 2;
        else if (move6 < -0.42f) bearish += 2;
        if (efficiency6 >= 0.38f) {
            if (move6 > 0f) bullish += 2;
            else if (move6 < 0f) bearish += 2;
        }
        if (colourBalance6 >= 2) bullish += 1;
        else if (colourBalance6 <= -2) bearish += 1;
        if (last.close > last.open && lastBodyRatio >= 0.30f
                && lastCloseLocation >= 0.58f) bullish += 2;
        else if (last.close < last.open && lastBodyRatio >= 0.30f
                && lastCloseLocation <= 0.42f) bearish += 2;
        int structure = structureDirection(candles, unit);
        if (structure > 0) bullish += 2;
        else if (structure < 0) bearish += 2;
        if (lastLowerWick > Math.max(lastUpperWick * 1.35f, unit * 0.16f)
                && lastCloseLocation >= 0.55f) bullish += 1;
        else if (lastUpperWick > Math.max(lastLowerWick * 1.35f, unit * 0.16f)
                && lastCloseLocation <= 0.45f) bearish += 1;

        if (bullishSweep) bullish += 4;
        if (bearishSweep) bearish += 4;
        if (bullishRetest) bullish += 4;
        if (bearishRetest) bearish += 4;
        if (bullishPullback) bullish += 3;
        if (bearishPullback) bearish += 3;
        if (bullishFlow) bullish += 2;
        if (bearishFlow) bearish += 2;
        if (analog.direction > 0 && analog.agreement >= 0.42f) {
            bullish += analog.agreement >= 0.66f ? 3 : 2;
        } else if (analog.direction < 0 && analog.agreement >= 0.42f) {
            bearish += analog.agreement >= 0.66f ? 3 : 2;
        }

        int flowBias = calculateFlowBias(move3, move6, move12, efficiency6,
                colourBalance6);
        boolean deadMarket = averageCloseMove / unit < 0.040f;
        boolean volatilityShock = lastRangeRatio > 3.15f
                || volatilityExpansion > 3.00f;
        boolean randomWhipsaw = changeRate8 > 0.82f && efficiency6 < 0.20f;
        boolean strongAnalogConflict = analog.agreement >= 0.72f
                && ((analog.direction > 0 && bearish - bullish >= 3)
                || (analog.direction < 0 && bullish - bearish >= 3));

        int lead = Math.abs(bullish - bearish);
        int winning = Math.max(bullish, bearish);
        int directionSign = bullish > bearish ? 1 : bearish > bullish ? -1 : 0;
        String profile = "NO STRUCTURAL EDGE";
        boolean contextualSetup = false;
        if ((directionSign > 0 && bullishSweep) || (directionSign < 0 && bearishSweep)) {
            profile = SWEEP_PROFILE;
            contextualSetup = true;
        } else if ((directionSign > 0 && bullishRetest)
                || (directionSign < 0 && bearishRetest)) {
            profile = RETEST_PROFILE;
            contextualSetup = true;
        } else if ((directionSign > 0 && bullishPullback)
                || (directionSign < 0 && bearishPullback)) {
            profile = PULLBACK_PROFILE;
            contextualSetup = true;
        } else if ((directionSign > 0 && bullishFlow)
                || (directionSign < 0 && bearishFlow)) {
            profile = FLOW_PROFILE;
            contextualSetup = true;
        } else if (analog.direction == directionSign && analog.agreement >= 0.68f) {
            profile = ANALOG_PROFILE;
        }

        boolean eligible = directionSign != 0 && winning >= 7 && lead >= 3
                && (contextualSetup || (ANALOG_PROFILE.equals(profile)
                && winning >= 8 && lead >= 4));
        String safetyReason = "";
        if (deadMarket) safetyReason = "flat candle movement";
        else if (volatilityShock) safetyReason = "abnormal range expansion";
        else if (randomWhipsaw) safetyReason = "random colour whipsaw";
        else if (strongAnalogConflict && !contextualSetup) {
            safetyReason = "local pattern conflict";
        }
        if (!safetyReason.isEmpty()) eligible = false;

        String analogLabel = analog.direction == 0 ? "neutral"
                : (analog.direction > 0 ? "UP" : "DOWN") + " "
                + analog.horizon + "m/" + Math.round(analog.agreement * 100f) + "%";
        String metrics = "UP " + bullish + " • DOWN " + bearish
                + " • flow " + signed(flowBias)
                + " • efficiency " + Math.round(efficiency6 * 100f) + "%"
                + " • analog " + analogLabel
                + " • range " + Math.round(volatilityExpansion * 100f) + "%";

        int readiness = Math.min(99, 30 + winning * 5 + lead * 4
                + (contextualSetup ? 8 : 0));
        if (!eligible) {
            String reason = !safetyReason.isEmpty() ? safetyReason
                    : !contextualSetup && !ANALOG_PROFILE.equals(profile)
                    ? "no complete price-action setup"
                    : "directional edge too small";
            return new Decision("WAIT", 0, flowBias, bullish, bearish, 0,
                    safetyReason.isEmpty() ? profile : "SAFETY FILTER",
                    readiness, metrics + " • " + reason);
        }

        int expiry = chooseExpiry(profile, size, directionSign, move6, move12,
                efficiency6, efficiency12, analog);
        int strength = Math.min(94, 49 + winning * 4 + lead * 2
                + (contextualSetup ? 4 : 0));
        String direction = directionSign > 0 ? "UP" : "DOWN";
        return new Decision(direction, strength, flowBias, bullish, bearish,
                expiry, profile, 100,
                metrics + " • " + profile.toLowerCase(Locale.US));
    }

    private static int chooseExpiry(String profile, int size, int direction,
                                    float move6, float move12,
                                    float efficiency6, float efficiency12,
                                    AnalogResult analog) {
        if (SWEEP_PROFILE.equals(profile)) return 2;
        if (ANALOG_PROFILE.equals(profile)) return analog.horizon;
        boolean localSupport = analog.direction == direction && analog.agreement >= 0.45f;
        if (size >= 34 && Math.abs(move12) >= 1.45f && efficiency12 >= 0.62f
                && localSupport) return 5;
        if (RETEST_PROFILE.equals(profile)) return 3;
        if (Math.abs(move6) >= 0.78f && efficiency6 >= 0.52f) return 3;
        return 2;
    }

    private static AnalogResult bestAnalog(List<CandlePoint> candles, float unit) {
        AnalogResult best = new AnalogResult(0, 2, 0f, Float.MAX_VALUE, 0);
        for (int horizon : new int[]{2, 3, 5}) {
            AnalogResult result = analogForHorizon(candles, unit, horizon);
            float quality = result.agreement / (1f + result.averageDistance * 0.18f);
            float bestQuality = best.agreement / (1f + best.averageDistance * 0.18f);
            if (result.samples >= 4 && quality > bestQuality) best = result;
        }
        return best;
    }

    private static AnalogResult analogForHorizon(List<CandlePoint> candles,
                                                  float unit, int horizon) {
        int size = candles.size();
        int currentEnd = size - 1;
        List<AnalogCandidate> candidates = new ArrayList<>();
        for (int end = 3; end + horizon < currentEnd; end++) {
            float distance = patternDistance(candles, end, currentEnd, unit);
            float outcomeMove = candles.get(end + horizon).close
                    - candles.get(end).close;
            int outcome = outcomeMove > unit * 0.04f ? 1
                    : outcomeMove < -unit * 0.04f ? -1 : 0;
            candidates.add(new AnalogCandidate(distance, outcome));
        }
        Collections.sort(candidates, Comparator.comparingDouble(c -> c.distance));
        int count = Math.min(6, candidates.size());
        float weightedVote = 0f;
        float totalWeight = 0f;
        float distanceSum = 0f;
        int nonFlat = 0;
        for (int i = 0; i < count; i++) {
            AnalogCandidate candidate = candidates.get(i);
            float weight = 1f / (0.35f + candidate.distance);
            weightedVote += weight * candidate.outcome;
            totalWeight += weight;
            distanceSum += candidate.distance;
            if (candidate.outcome != 0) nonFlat++;
        }
        if (count < 4 || totalWeight <= 0f || nonFlat < 3) {
            return new AnalogResult(0, horizon, 0f, Float.MAX_VALUE, count);
        }
        float agreement = Math.abs(weightedVote) / totalWeight;
        int direction = weightedVote > 0f ? 1 : weightedVote < 0f ? -1 : 0;
        return new AnalogResult(direction, horizon, agreement,
                distanceSum / count, count);
    }

    private static float patternDistance(List<CandlePoint> candles, int pastEnd,
                                         int currentEnd, float unit) {
        float distance = 0f;
        for (int offset = 0; offset < 3; offset++) {
            int pastIndex = pastEnd - 2 + offset;
            int currentIndex = currentEnd - 2 + offset;
            CandlePoint past = candles.get(pastIndex);
            CandlePoint current = candles.get(currentIndex);
            float pastRange = Math.max(1f, past.height);
            float currentRange = Math.max(1f, current.height);
            float pastBody = (past.close - past.open) / pastRange;
            float currentBody = (current.close - current.open) / currentRange;
            distance += Math.abs(pastBody - currentBody) * 1.20f;
            distance += Math.abs(closeLocation(past) - closeLocation(current)) * 0.80f;
            distance += Math.abs(clamp(past.height / unit, 0f, 2.5f)
                    - clamp(current.height / unit, 0f, 2.5f)) * 0.45f;
            if (pastIndex > 0 && currentIndex > 0) {
                float pastDelta = clamp((past.close - candles.get(pastIndex - 1).close)
                        / unit, -2f, 2f);
                float currentDelta = clamp((current.close
                        - candles.get(currentIndex - 1).close) / unit, -2f, 2f);
                distance += Math.abs(pastDelta - currentDelta) * 0.65f;
            }
        }
        float pastContext = (candles.get(pastEnd).close
                - candles.get(Math.max(0, pastEnd - 4)).close) / unit;
        float currentContext = (candles.get(currentEnd).close
                - candles.get(Math.max(0, currentEnd - 4)).close) / unit;
        distance += Math.abs(clamp(pastContext, -3f, 3f)
                - clamp(currentContext, -3f, 3f)) * 0.55f;
        return distance;
    }

    private static int structureDirection(List<CandlePoint> candles, float unit) {
        int size = candles.size();
        if (size < 8) return 0;
        float oldHigh = highestHigh(candles, size - 8, size - 4);
        float oldLow = lowestLow(candles, size - 8, size - 4);
        float newHigh = highestHigh(candles, size - 4, size);
        float newLow = lowestLow(candles, size - 4, size);
        if (newHigh > oldHigh + unit * 0.05f
                && newLow > oldLow + unit * 0.05f) return 1;
        if (newHigh < oldHigh - unit * 0.05f
                && newLow < oldLow - unit * 0.05f) return -1;
        return 0;
    }

    private static int calculateFlowBias(float move3, float move6, float move12,
                                         float efficiency6, int colourBalance6) {
        float raw = clamp(move3 / 0.9f, -1f, 1f) * 0.38f
                + clamp(move6 / 1.4f, -1f, 1f) * 0.34f
                + clamp(move12 / 2.2f, -1f, 1f) * 0.16f
                + clamp(colourBalance6 / 6f, -1f, 1f) * 0.12f;
        raw *= 0.58f + efficiency6 * 0.42f;
        return Math.round(clamp(raw, -1f, 1f) * 100f);
    }

    private static float move(float[] closes, int count) {
        int start = Math.max(0, closes.length - count);
        return closes[closes.length - 1] - closes[start];
    }

    private static float efficiency(float[] closes, int count) {
        int start = Math.max(0, closes.length - count);
        return efficiencyBetween(closes, start, closes.length - 1);
    }

    private static float efficiencyBetween(float[] closes, int start, int end) {
        if (end <= start) return 0f;
        float path = 0f;
        for (int i = start + 1; i <= end; i++) {
            path += Math.abs(closes[i] - closes[i - 1]);
        }
        return path < 0.0001f ? 0f
                : Math.abs(closes[end] - closes[start]) / path;
    }

    private static float changeRate(float[] closes, int count) {
        int start = Math.max(1, closes.length - count);
        int changes = 0;
        int comparisons = 0;
        int previous = 0;
        for (int i = start; i < closes.length; i++) {
            float delta = closes[i] - closes[i - 1];
            int sign = delta > 0.0001f ? 1 : delta < -0.0001f ? -1 : 0;
            if (sign != 0 && previous != 0) {
                comparisons++;
                if (sign != previous) changes++;
            }
            if (sign != 0) previous = sign;
        }
        return comparisons == 0 ? 0f : changes / (float) comparisons;
    }

    private static int colourBalance(List<CandlePoint> candles, int count) {
        int start = Math.max(0, candles.size() - count);
        int balance = 0;
        for (int i = start; i < candles.size(); i++) {
            balance += candles.get(i).close > candles.get(i).open ? 1 : -1;
        }
        return balance;
    }

    private static float averageCloseMove(float[] closes, int count) {
        int start = Math.max(1, closes.length - count);
        float sum = 0f;
        int samples = 0;
        for (int i = start; i < closes.length; i++) {
            sum += Math.abs(closes[i] - closes[i - 1]);
            samples++;
        }
        return samples == 0 ? 0f : sum / samples;
    }

    private static float medianRange(List<CandlePoint> candles, int count) {
        int start = Math.max(0, candles.size() - count);
        List<Float> values = new ArrayList<>();
        for (int i = start; i < candles.size(); i++) values.add(candles.get(i).height);
        return Math.max(1f, median(values));
    }

    private static float highestHigh(List<CandlePoint> candles, int from, int to) {
        int start = Math.max(0, from);
        int end = Math.min(candles.size(), to);
        float result = -Float.MAX_VALUE;
        for (int i = start; i < end; i++) result = Math.max(result, candles.get(i).high);
        return result == -Float.MAX_VALUE ? candles.get(0).high : result;
    }

    private static float lowestLow(List<CandlePoint> candles, int from, int to) {
        int start = Math.max(0, from);
        int end = Math.min(candles.size(), to);
        float result = Float.MAX_VALUE;
        for (int i = start; i < end; i++) result = Math.min(result, candles.get(i).low);
        return result == Float.MAX_VALUE ? candles.get(0).low : result;
    }

    private static float closeLocation(CandlePoint candle) {
        return clamp((candle.close - candle.low) / Math.max(1f, candle.height), 0f, 1f);
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) return 0f;
        List<Float> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int middle = copy.size() / 2;
        if ((copy.size() & 1) == 1) return copy.get(middle);
        return (copy.get(middle - 1) + copy.get(middle)) * 0.5f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
