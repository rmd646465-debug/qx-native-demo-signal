package com.qx.strictsignal;

import java.util.ArrayList;
import java.util.List;

/**
 * Direction-neutral signal scoring for candle points extracted from the chart.
 *
 * <p>The engine deliberately keeps bullish and bearish rules as exact mirrors.
 * A score is setup strength, not a claimed probability of winning.</p>
 */
final class SignalEngine {
    static final int MIN_CANDLES = 20;
    static final int MAX_CANDLES = 45;
    static final int MAX_SCORE = 15;

    static final class CandlePoint {
        final float x;
        final float price;
        final float height;
        final boolean up;

        CandlePoint(float x, float price, float height, boolean up) {
            this.x = x;
            this.price = price;
            this.height = height;
            this.up = up;
        }
    }

    static final class Decision {
        final String direction;
        final int strength;
        final int rsi;
        final int bullishPoints;
        final int bearishPoints;
        final String detail;

        Decision(String direction, int strength, int rsi, int bullishPoints,
                 int bearishPoints, String detail) {
            this.direction = direction;
            this.strength = strength;
            this.rsi = rsi;
            this.bullishPoints = bullishPoints;
            this.bearishPoints = bearishPoints;
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
            candles = new ArrayList<>(candles.subList(candles.size() - MAX_CANDLES, candles.size()));
        }

        float[] prices = new float[candles.size()];
        float averageHeight = 0f;
        for (int i = 0; i < candles.size(); i++) {
            prices[i] = candles.get(i).price;
            averageHeight += candles.get(i).height;
        }
        averageHeight = Math.max(2f, averageHeight / candles.size());

        float shortSlope = regressionSlope(prices, Math.min(8, prices.length)) / averageHeight;
        float mediumSlope = regressionSlope(prices, Math.min(14, prices.length)) / averageHeight;
        float longSlope = regressionSlope(prices, Math.min(24, prices.length)) / averageHeight;
        float emaGap = (ema(prices, 5) - ema(prices, 13)) / averageHeight;
        float[] priorPrices = new float[Math.max(2, prices.length - 3)];
        System.arraycopy(prices, 0, priorPrices, 0, priorPrices.length);
        float priorEmaGap = (ema(priorPrices, 5) - ema(priorPrices, 13)) / averageHeight;
        float macdImpulse = emaGap - priorEmaGap;
        float swing = (prices[prices.length - 1] - prices[Math.max(0, prices.length - 4)])
                / averageHeight;
        int rsi = Math.round(rsi(prices));

        float path = 0f;
        float averageMove = 0f;
        float maximumMove = 0f;
        for (int i = 1; i < prices.length; i++) {
            float move = Math.abs(prices[i] - prices[i - 1]);
            path += move;
            maximumMove = Math.max(maximumMove, move);
        }
        averageMove = path / Math.max(1, prices.length - 1);
        float trendEfficiency = path < 0.0001f
                ? 0f : Math.abs(prices[prices.length - 1] - prices[0]) / path;

        int recentColorBalance = 0;
        for (int i = Math.max(0, candles.size() - 7); i < candles.size(); i++) {
            recentColorBalance += candles.get(i).up ? 1 : -1;
        }

        float latest = prices[prices.length - 1];
        float priorMax = -Float.MAX_VALUE;
        float priorMin = Float.MAX_VALUE;
        for (int i = Math.max(0, prices.length - 9); i < prices.length - 1; i++) {
            priorMax = Math.max(priorMax, prices[i]);
            priorMin = Math.min(priorMin, prices[i]);
        }

        float rangeMax = -Float.MAX_VALUE;
        float rangeMin = Float.MAX_VALUE;
        for (int i = Math.max(0, prices.length - 14); i < prices.length; i++) {
            rangeMax = Math.max(rangeMax, prices[i]);
            rangeMin = Math.min(rangeMin, prices[i]);
        }
        float rangePosition = rangeMax - rangeMin < 0.0001f
                ? 0.5f : (latest - rangeMin) / (rangeMax - rangeMin);

        int bullish = 0;
        int bearish = 0;

        if (shortSlope > 0.08f) bullish += 2;
        else if (shortSlope < -0.08f) bearish += 2;

        if (mediumSlope > 0.055f) bullish += 2;
        else if (mediumSlope < -0.055f) bearish += 2;

        if (longSlope > 0.04f) bullish += 2;
        else if (longSlope < -0.04f) bearish += 2;

        if (emaGap > 0.055f) bullish += 2;
        else if (emaGap < -0.055f) bearish += 2;

        if (macdImpulse > 0.015f) bullish += 1;
        else if (macdImpulse < -0.015f) bearish += 1;

        if (swing > 0.16f) bullish += 1;
        else if (swing < -0.16f) bearish += 1;

        if (recentColorBalance >= 3) bullish += 1;
        else if (recentColorBalance <= -3) bearish += 1;

        if (latest > priorMax) bullish += 1;
        else if (latest < priorMin) bearish += 1;

        if (rsi >= 56 && rsi <= 68) bullish += 1;
        else if (rsi <= 44 && rsi >= 32) bearish += 1;

        float netMove = prices[prices.length - 1] - prices[0];
        if (trendEfficiency >= 0.30f && netMove > 0f) bullish += 1;
        else if (trendEfficiency >= 0.30f && netMove < 0f) bearish += 1;

        if (rangePosition >= 0.58f && rangePosition <= 0.88f) bullish += 1;
        else if (rangePosition <= 0.42f && rangePosition >= 0.12f) bearish += 1;

        int net = bullish - bearish;
        String direction = "WAIT";
        if (bullish >= 9 && net >= 4 && shortSlope > 0.04f
                && mediumSlope > 0.035f && longSlope > 0.025f
                && recentColorBalance >= 1 && trendEfficiency >= 0.31f) {
            direction = "UP";
        } else if (bearish >= 9 && net <= -4 && shortSlope < -0.04f
                && mediumSlope < -0.035f && longSlope < -0.025f
                && recentColorBalance <= -1 && trendEfficiency >= 0.31f) {
            direction = "DOWN";
        }

        boolean overextended = ("UP".equals(direction) && rsi > 70)
                || ("DOWN".equals(direction) && rsi < 30);
        boolean choppy = trendEfficiency < 0.30f || Math.abs(longSlope) < 0.025f;
        boolean deadMarket = averageMove / averageHeight < 0.04f;
        boolean volatilityShock = maximumMove / averageHeight > 2.8f;
        boolean trendConflict = (shortSlope > 0.08f && longSlope < -0.04f)
                || (shortSlope < -0.08f && longSlope > 0.04f);
        boolean lateEntry = ("UP".equals(direction) && rangePosition > 0.92f)
                || ("DOWN".equals(direction) && rangePosition < 0.08f);
        float latestHeightRatio = candles.get(candles.size() - 1).height / averageHeight;
        boolean oversizedFinalCandle = latestHeightRatio > 2.4f;
        if (overextended || choppy || deadMarket || volatilityShock || trendConflict
                || lateEntry || oversizedFinalCandle) {
            direction = "WAIT";
        }

        int winningSide = Math.max(bullish, bearish);
        int strength = "WAIT".equals(direction)
                ? 0
                : Math.min(93, 50 + winningSide * 3
                        + Math.round(Math.min(10f, trendEfficiency * 12f)));
        String filter = overextended ? " • overextended"
                : choppy ? " • choppy"
                : deadMarket ? " • low volatility"
                : volatilityShock ? " • volatility shock"
                : trendConflict ? " • trend conflict"
                : lateEntry ? " • late entry"
                : oversizedFinalCandle ? " • oversized candle" : "";
        String detail = "UP " + bullish + "/" + MAX_SCORE
                + " • DOWN " + bearish + "/" + MAX_SCORE
                + " • trend " + Math.round(trendEfficiency * 100f) + "%" + filter;
        return new Decision(direction, strength, rsi, bullish, bearish, detail);
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
                ? 0f
                : (n * sumXY - sumX * sumY) / denominator;
    }
}
