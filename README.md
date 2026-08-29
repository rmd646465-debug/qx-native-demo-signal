# QX Adaptive Demo Signal

An Android **demo-only learning tool** that reads the currently rendered Quotex candle chart on-device and shows `UP`, `DOWN`, or `WAIT`. This is a completely separate application from `qx-native-demo-signal`:

- new repository/project name: `qx-adaptive-demo-signal`
- new Android application ID: `com.qx.adaptiveedge`
- new app label: `QX Adaptive Demo Signal`
- version starts at `1.0.0-adaptive-regime`

The old app and this app can be installed side-by-side.

## What is different

### Chart-regime adaptive analysis

The app does not force one formula onto every currency. Each visible currency is analyzed independently and the engine chooses one of these profiles from that chart:

1. **TREND + MOMENTUM** — multi-window regression, EMA alignment, MACD impulse, RSI, rate of change, candle balance and trend efficiency.
2. **BREAKOUT + ROC** — channel break, controlled volatility expansion, multi-window trend agreement and momentum confirmation.
3. **RANGE REVERSAL** — range location, RSI, deviation from the rolling mean, multi-candle turn and wick rejection.
4. **WAIT profiles** — low volatility, erratic/choppy flow, volatility shock, conflicting trend, oversized candle, late entry or incomplete confluence.

Every bullish condition has an equivalent mirrored bearish condition. A deterministic test verifies UP/DOWN symmetry.

### Per-currency state

Confirmation history is stored by the visible currency name. Frames from two different currencies are never combined. A signal requires two matching recent frames for the same currency, profile, direction and expiry.

### Faster eligible signals and useful WAIT output

- current-chart checks run about every 1.25 seconds;
- a valid setup normally appears after the second matching frame;
- `AUTO SCAN` checks up to 12 currently visible/open assets, higher displayed payouts first;
- a strong eligible asset is returned immediately without finishing the full queue;
- a developing setup shows `PLEASE WAIT ~1 MIN` or `~2 MIN` with a live estimate;
- a weak/conflicting chart stays `WAIT` and does not manufacture a signal.

The wait time is a setup-development estimate, not a promise that a signal will appear.

### Separate 2/3/5-minute rules

The duration appears beside the direction, for example `UP • 3 MIN`.

- **2 minutes:** normal eligible setup or range reversal;
- **3 minutes:** at least 29 closed candles, stronger directional lead, persistent trend and controlled volatility;
- **5 minutes:** at least 38 closed candles with stricter long-horizon slope, persistence and trend-efficiency checks.

Strength alone cannot unlock 3 or 5 minutes. Use a **1-minute chart** so the displayed 2/3/5-minute demo horizon has the intended meaning.

## How chart reading works

- the full platform page remains visible in an Android WebView;
- Android `PixelCopy` captures only the rendered chart frame;
- the chart crop excludes the large red/green trade controls;
- colored candle groups are converted into approximate open/high/low/close points;
- the rightmost still-forming candle is discarded;
- at least 20 closed candles plus the forming candle must be visible;
- all captured frames stay on the phone.

The app does not read passwords, send screenshots to a server, set an amount, click UP/DOWN, or place an order.

## Research boundary

The adaptive design follows the general market-condition guidance in Quotex's own indicator article: trend tools for trending markets, oscillators/range tools for ranging markets, and volatility plus trend confirmation for volatile markets. It also uses the idea of time-series trend persistence as supporting research.

These sources do **not** prove profitable 1-minute OTC or binary-option signals:

- [Quotex indicator and market-condition guide](https://blog.qxbroker.com/updates/how-to-choose-the-right-indicator-for-trading-on-quotex/)
- [Quotex Rules of Trading Operations](https://qxbroker.com/documents/en/Rules_of_Trading_operations_QTX.pdf)
- [Moskowitz, Ooi and Pedersen — Time Series Momentum](https://w4.stern.nyu.edu/facdir/lpederse/papers/TimeSeriesMomentum.pdf)
- [Bailey et al. — The Probability of Backtest Overfitting](https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2326253)

There is no verified secret “foreign high-performance Quotex strategy” that can honestly be copied into an APK. This engine is a transparent rules-based experiment, not a hidden API, exploit, cloud AI model, or guaranteed-profit system.

## Platform and risk limits

Quotex's current rules prohibit automated mechanisms that perform operations without the client's direct participation. This app therefore never submits a trade or touches the amount/direction controls. The user remains responsible for checking current platform terms and local law.

Binary options can lose the full stake. Setup score is **not win probability**. Keep this tool on a demo account and record at least 100 forward demo observations before evaluating any profile. Do not use real money based on this display.

## Deterministic engine test

With JDK 17:

```bash
mkdir -p build/engine-test
javac -d build/engine-test \
  app/src/main/java/com/qx/adaptiveedge/SignalEngine.java \
  engine-test/com/qx/adaptiveedge/SignalEngineSelfTest.java
java -cp build/engine-test com.qx.adaptiveedge.SignalEngineSelfTest
```

The test covers:

- mirrored trend UP/DOWN scoring;
- trend, breakout and range-regime selection;
- flat/choppy WAIT and volatility-shock WAIT;
- symmetric range reversals;
- distinct 2, 3 and 5-minute horizon gates.

## Build the APK

GitHub Actions runs the engine test before the Android build. Open the latest successful **Build QX Adaptive Demo APK** run and download `QX-Adaptive-Demo-APK-v1`. Unzip it and install `app-debug.apk` on Android 8.0 or newer.

Because the application ID is new, installing this APK does not update or overwrite `QX Native Demo Signal`.
