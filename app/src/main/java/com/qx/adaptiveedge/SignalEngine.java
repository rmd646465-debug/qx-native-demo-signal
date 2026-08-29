package com.qx.adaptiveedge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Direction-neutral, market-regime adaptive analysis for closed candle points.
 *
 * <p>The engine deliberately mirrors every bullish rule with an equivalent
 * bearish rule. It chooses a trend, breakout or range-reversal profile from
 * the visible chart instead of forcing one strategy onto every asset. Scores
 * describe setup quality, never a probability of winning.</p>
 */
final class SignalEngine {
    static final int MIN_CANDLES = 20;
    static final int MAX_CANDLES = 60;
    static final int MAX_SCORE = 15;

    static final String TREND_PROFILE = "TREND + MOMENTUM";
    static final String BREAKOUT_PROFILE = "BREAKOUT + ROC";
    static final String RANGE_PROFILE = "RANGE REVERSAL";

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
            this.height = safeHeight;
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
        final int rsi;
        final int bullishPoints;
        final int bearishPoints;
        final int expiryMinutes;
        final String profile;
        final int waitMinutes;
        final int readiness;
        final String detail;

        Decision(String direction, int strength, int rsi, int bullishPoints,
                 int bearishPoints, int expiryMinutes, String profile,
                 int waitMinutes, int readiness, String detail) {
            this.direction = direction;
            this.strength = strength;
            this.rsi = rsi;
            this.bullishPoints = bullishPoints;
            this.bearishPoints = bearishPoints;
            this.expiryMinutes = expiryMinutes;
            this.profile = profile;
            this.waitMinutes = waitMinutes;
            this.readiness = readiness;
            this.detail = detail;
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
        float[] prices = new float[size];
        float averageRange = 0f;
        for (int i = 0; i < size; i++) {
            prices[i] = candles.get(i).close;
            averageRange += candles.get(i).height;
        }
        averageRange = Math.max(1f, averageRange / size);

        float shortSlope = regressionSlope(prices, Math.min(7, size)) / averageRange;
        float turnSlope = regressionSlope(prices, Math.min(4, size)) / averageRange;
        float mediumSlope = regressionSlope(prices, Math.min(14, size)) / averageRange;
        float longSlope = regressionSlope(prices, Math.min(26, size)) / averageRange;
        float horizonSlope = regressionSlope(prices, Math.min(42, size)) / averageRange;
        float emaGap = (ema(prices, 5) - ema(prices, 13)) / averageRange;
        float[] priorPrices = new float[Math.max(2, prices.length - 3)];
        System.arraycopy(prices, 0, priorPrices, 0, priorPrices.length);
        float priorEmaGap = (ema(priorPrices, 5) - ema(priorPrices, 13)) / averageRange;
        float macdImpulse = emaGap - priorEmaGap;
        float swing = (prices[size - 1] - prices[Math.max(0, size - 5)]) / averageRange;
        int rsi = Math.round(rsi(prices));

        float path = 0f;
        float maximumMove = 0f;
        int directionChanges = 0;
        int previousSign = 0;
        for (int i = 1; i < size; i++) {
            float delta = prices[i] - prices[i - 1];
            float move = Math.abs(delta);
            path += move;
            maximumMove = Math.max(maximumMove, move);
            int sign = delta > 0.0001f ? 1 : delta < -0.0001f ? -1 : 0;
            if (sign != 0 && previousSign != 0 && sign != previousSign) directionChanges++;
            if (sign != 0) previousSign = sign;
        }
        float averageMove = path / Math.max(1, size - 1);
        float trendEfficiency = path < 0.0001f
                ? 0f : Math.abs(prices[size - 1] - prices[0]) / path;
        float changeRatio = directionChanges / (float) Math.max(1, size - 2);

        int recentColorBalance = 0;
        for (int i = Math.max(0, size - 7); i < size; i++) {
            recentColorBalance += candles.get(i).up ? 1 : -1;
        }

        float latest = prices[size - 1];
        float priorMax = -Float.MAX_VALUE;
        float priorMin = Float.MAX_VALUE;
        for (int i = Math.max(0, size - 11); i < size - 1; i++) {
            priorMax = Math.max(priorMax, prices[i]);
            priorMin = Math.min(priorMin, prices[i]);
        }

        int rangeStart = Math.max(0, size - 20);
        float rangeMax = -Float.MAX_VALUE;
        float rangeMin = Float.MAX_VALUE;
        float rangeMean = 0f;
        for (int i = rangeStart; i < size; i++) {
            rangeMax = Math.max(rangeMax, prices[i]);
            rangeMin = Math.min(rangeMin, prices[i]);
            rangeMean += prices[i];
        }
        int rangeCount = size - rangeStart;
        rangeMean /= Math.max(1, rangeCount);
        float variance = 0f;
        for (int i = rangeStart; i < size; i++) {
            float difference = prices[i] - rangeMean;
            variance += difference * difference;
        }
        float standardDeviation = (float) Math.sqrt(variance / Math.max(1, rangeCount));
        float zScore = standardDeviation < 0.0001f ? 0f : (latest - rangeMean) / standardDeviation;
        float rangePosition = rangeMax - rangeMin < 0.0001f
                ? 0.5f : (latest - rangeMin) / (rangeMax - rangeMin);

        int recentMoveStart = Math.max(1, size - 6);
        float recentMove = 0f;
        int recentMoveCount = 0;
        for (int i = recentMoveStart; i < size; i++) {
            recentMove += Math.abs(prices[i] - prices[i - 1]);
            recentMoveCount++;
        }
        recentMove /= Math.max(1, recentMoveCount);
        float earlierMove = 0f;
        int earlierMoveCount = 0;
        for (int i = 1; i < recentMoveStart; i++) {
            earlierMove += Math.abs(prices[i] - prices[i - 1]);
            earlierMoveCount++;
        }
        earlierMove /= Math.max(1, earlierMoveCount);
        float volatilityRatio = earlierMove < 0.0001f ? 1f : recentMove / earlierMove;

        CandlePoint latestCandle = candles.get(size - 1);
        float body = Math.max(0.1f, Math.abs(latestCandle.close - latestCandle.open));
        float upperWick = Math.max(0f,
                latestCandle.high - Math.max(latestCandle.open, latestCandle.close));
        float lowerWick = Math.max(0f,
                Math.min(latestCandle.open, latestCandle.close) - latestCandle.low);
        float latestRangeRatio = latestCandle.height / averageRange;

        int bullish = 0;
        int bearish = 0;
        if (shortSlope > 0.08f) bullish += 2;
        else if (shortSlope < -0.08f) bearish += 2;
        if (mediumSlope > 0.055f) bullish += 2;
        else if (mediumSlope < -0.055f) bearish += 2;
        if (longSlope > 0.035f) bullish += 2;
        else if (longSlope < -0.035f) bearish += 2;
        if (emaGap > 0.055f) bullish += 2;
        else if (emaGap < -0.055f) bearish += 2;
        if (macdImpulse > 0.012f) bullish += 1;
        else if (macdImpulse < -0.012f) bearish += 1;
        if (swing > 0.16f) bullish += 1;
        else if (swing < -0.16f) bearish += 1;
        if (recentColorBalance >= 3) bullish += 1;
        else if (recentColorBalance <= -3) bearish += 1;
        if (latest > priorMax) bullish += 1;
        else if (latest < priorMin) bearish += 1;
        if (rsi >= 54 && rsi <= 70) bullish += 1;
        else if (rsi <= 46 && rsi >= 30) bearish += 1;
        float netMove = prices[size - 1] - prices[0];
        if (trendEfficiency >= 0.30f && netMove > 0f) bullish += 1;
        else if (trendEfficiency >= 0.30f && netMove < 0f) bearish += 1;
        if (rangePosition >= 0.56f && rangePosition <= 0.93f) bullish += 1;
        else if (rangePosition <= 0.44f && rangePosition >= 0.07f) bearish += 1;

        int net = bullish - bearish;
        int winningSide = Math.max(bullish, bearish);
        int directionalLead = Math.abs(net);
        boolean trendUp = shortSlope > 0.045f && mediumSlope > 0.035f
                && longSlope > 0.025f && emaGap > 0.035f;
        boolean trendDown = shortSlope < -0.045f && mediumSlope < -0.035f
                && longSlope < -0.025f && emaGap < -0.035f;
        boolean breakoutUp = latest > priorMax + averageRange * 0.025f
                && swing > 0.22f && volatilityRatio >= 0.92f;
        boolean breakoutDown = latest < priorMin - averageRange * 0.025f
                && swing < -0.22f && volatilityRatio >= 0.92f;
        boolean rangeLike = trendEfficiency <= 0.30f && Math.abs(longSlope) < 0.160f
                && changeRatio >= 0.24f;

        boolean recentTurnUp = size >= 4 && latestCandle.up && turnSlope > 0.08f;
        boolean recentTurnDown = size >= 4 && !latestCandle.up && turnSlope < -0.08f;
        int rangeUp = 0;
        int rangeDown = 0;
        if (rangePosition <= 0.36f) rangeUp += 2;
        else if (rangePosition >= 0.64f) rangeDown += 2;
        if (rsi <= 40) rangeUp += 2;
        else if (rsi >= 60) rangeDown += 2;
        if (zScore <= -1.0f) rangeUp += 2;
        else if (zScore >= 1.0f) rangeDown += 2;
        if (recentTurnUp) rangeUp += 2;
        else if (recentTurnDown) rangeDown += 2;
        if (lowerWick >= Math.max(body * 0.65f, averageRange * 0.12f)) rangeUp += 1;
        else if (upperWick >= Math.max(body * 0.65f, averageRange * 0.12f)) rangeDown += 1;
        if (rangeLike) {
            rangeUp += 1;
            rangeDown += 1;
        }

        boolean deadMarket = averageMove / averageRange < 0.045f;
        boolean volatilityShock = maximumMove / averageRange > 3.0f
                || volatilityRatio > 3.1f;
        boolean trendConflict = (shortSlope > 0.08f && longSlope < -0.04f)
                || (shortSlope < -0.08f && longSlope > 0.04f);
        boolean erratic = changeRatio > 0.74f && trendEfficiency < 0.24f;
        boolean oversizedFinalCandle = latestRangeRatio > 2.65f;

        String direction = "WAIT";
        String profile = chooseWaitProfile(rangeLike, longSlope, volatilityShock,
                deadMarket, erratic);
        if (breakoutUp && bullish >= 9 && net >= 4 && trendUp
                && trendEfficiency >= 0.34f) {
            direction = "UP";
            profile = BREAKOUT_PROFILE;
        } else if (breakoutDown && bearish >= 9 && net <= -4 && trendDown
                && trendEfficiency >= 0.34f) {
            direction = "DOWN";
            profile = BREAKOUT_PROFILE;
        } else if (bullish >= 9 && net >= 4 && trendUp && recentColorBalance >= 1
                && trendEfficiency >= 0.34f) {
            direction = "UP";
            profile = TREND_PROFILE;
        } else if (bearish >= 9 && net <= -4 && trendDown && recentColorBalance <= -1
                && trendEfficiency >= 0.34f) {
            direction = "DOWN";
            profile = TREND_PROFILE;
        } else if (rangeLike && rangeUp >= 7 && rangeUp - rangeDown >= 3
                && longSlope > -0.160f) {
            direction = "UP";
            profile = RANGE_PROFILE;
        } else if (rangeLike && rangeDown >= 7 && rangeDown - rangeUp >= 3
                && longSlope < 0.160f) {
            direction = "DOWN";
            profile = RANGE_PROFILE;
        }

        boolean trendOverextended = !RANGE_PROFILE.equals(profile)
                && (("UP".equals(direction) && rsi > 82)
                || ("DOWN".equals(direction) && rsi < 18));
        boolean lateTrendEntry = TREND_PROFILE.equals(profile)
                && (("UP".equals(direction) && (rangePosition > 0.965f || zScore > 2.65f))
                || ("DOWN".equals(direction) && (rangePosition < 0.035f || zScore < -2.65f)));
        String safetyReason = "";
        if (deadMarket) safetyReason = "low volatility";
        else if (volatilityShock) safetyReason = "volatility shock";
        else if (trendConflict) safetyReason = "trend conflict";
        else if (erratic) safetyReason = "erratic/choppy";
        else if (oversizedFinalCandle) safetyReason = "oversized final candle";
        else if (trendOverextended) safetyReason = "overextended momentum";
        else if (lateTrendEntry) safetyReason = "late trend entry";
        if (!safetyReason.isEmpty()) {
            direction = "WAIT";
            profile = "SAFETY WAIT";
        }

        int trendReadiness = Math.min(99, winningSide * 6 + directionalLead * 3
                + Math.round(trendEfficiency * 22f));
        int rangeReadiness = Math.min(99, Math.max(rangeUp, rangeDown) * 9
                + (rangeLike ? 8 : 0));
        int readiness = Math.max(trendReadiness, rangeReadiness);
        int waitMinutes = 0;
        if ("WAIT".equals(direction)) {
            if (!safetyReason.isEmpty()) waitMinutes = 2;
            else if (readiness >= 72) waitMinutes = 1;
            else if (readiness >= 54) waitMinutes = 2;
        }

        String metrics = "UP " + bullish + "/" + MAX_SCORE
                + " • DOWN " + bearish + "/" + MAX_SCORE
                + " • trend " + Math.round(trendEfficiency * 100f) + "%"
                + " • vol " + Math.round(volatilityRatio * 100f) + "%"
                + " • flow " + Math.round(changeRatio * 100f) + "%"
                + " • slope " + Math.round(longSlope * 1000f)
                + " • range " + rangeUp + "/" + rangeDown;
        if ("WAIT".equals(direction)) {
            String reason = !safetyReason.isEmpty() ? safetyReason
                    : rangeLike ? "range trigger incomplete"
                    : winningSide >= 7 ? "confluence developing"
                    : "no clean market regime";
            return new Decision("WAIT", 0, rsi, bullish, bearish, 0, profile,
                    waitMinutes, readiness, metrics + " • " + reason);
        }

        int profilePoints = RANGE_PROFILE.equals(profile)
                ? Math.max(rangeUp, rangeDown) : winningSide;
        int strength;
        if (RANGE_PROFILE.equals(profile)) {
            strength = Math.min(91, 52 + profilePoints * 4
                    + Math.round(Math.max(0f, 0.30f - trendEfficiency) * 20f));
        } else if (BREAKOUT_PROFILE.equals(profile)) {
            strength = Math.min(95, 55 + winningSide * 3
                    + Math.round(Math.min(8f, trendEfficiency * 12f)));
        } else {
            strength = Math.min(94, 51 + winningSide * 3
                    + Math.round(Math.min(9f, trendEfficiency * 13f)));
        }
        int expiryMinutes = chooseExpiry(profile, size, winningSide, directionalLead,
                trendEfficiency, Math.abs(longSlope), Math.abs(horizonSlope),
                volatilityRatio);
        return new Decision(direction, strength, rsi, bullish, bearish,
                expiryMinutes, profile, 0, 100,
                metrics + " • " + profile.toLowerCase(Locale.US));
    }

    private static String chooseWaitProfile(boolean rangeLike, float longSlope,
                                            boolean volatilityShock,
                                            boolean deadMarket, boolean erratic) {
        if (volatilityShock) return "VOLATILITY WAIT";
        if (deadMarket) return "LOW VOLATILITY WAIT";
        if (erratic) return "CHOPPY WAIT";
        if (rangeLike) return "RANGE WATCH";
        if (Math.abs(longSlope) >= 0.025f) return "TREND WATCH";
        return "MIXED / WAIT";
    }

    private static int chooseExpiry(String profile, int candleCount,
                                    int winningSide, int directionalLead,
                                    float trendEfficiency, float longSlope,
                                    float horizonSlope, float volatilityRatio) {
        if (RANGE_PROFILE.equals(profile)) return 2;
        boolean controlledVolatility = volatilityRatio >= 0.55f && volatilityRatio <= 1.90f;
        if (candleCount >= 38 && winningSide >= 11 && directionalLead >= 6
                && trendEfficiency >= 0.48f && longSlope >= 0.035f
                && horizonSlope >= 0.025f && controlledVolatility) {
            return 5;
        }
        if (candleCount >= 29 && winningSide >= 10 && directionalLead >= 5
                && trendEfficiency >= 0.41f && longSlope >= 0.030f
                && controlledVolatility) {
            return 3;
        }
        return 2;
    }

    private static float ema(float[] values, int period) {
        float alpha = 2f / (period + 1f);
        float value = values[0];
        for (int i = 1; i < values.length; i++) {
            value = values[i] * alpha + value * (1f - alpha);
        }
        return value;
    }

    private static float rsi(float[] values) {
        int start = Math.max(1, values.length - 14);
        float gains = 0f;
        float losses = 0f;
        int count = 0;
        for (int i = start; i < values.length; i++) {
            float delta = values[i] - values[i - 1];
            if (delta > 0) gains += delta;
            else losses -= delta;
            count++;
        }
        if (count == 0) return 50f;
        gains /= count;
        losses /= count;
        if (losses < 0.0001f) return 100f;
        float relativeStrength = gains / losses;
        return 100f - 100f / (1f + relativeStrength);
    }

    private static float regressionSlope(float[] values, int count) {
        int start = Math.max(0, values.length - count);
        int n = values.length - start;
        if (n < 2) return 0f;
        float sumX = 0f;
        float sumY = 0f;
        float sumXY = 0f;
        float sumXX = 0f;
        for (int i = 0; i < n; i++) {
            float y = values[start + i];
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumXX += i * i;
        }
        float denominator = n * sumXX - sumX * sumX;
        return Math.abs(denominator) < 0.0001f
                ? 0f : (n * sumXY - sumX * sumY) / denominator;
    }
}
