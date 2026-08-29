# QX Stable Close Demo

Android analysis-only chart reader that displays `UP`, `DOWN`, or `WAIT` from
the currently rendered Quotex demo candlestick chart. Version
`1.2.1-chart-repair` keeps the separate application ID
`com.qx.adaptiveedge` and never presses a trade button or places an order.

## Why v1.1 was replaced

A 30-second user recording showed the same USD/COP OTC chart being detected as
28, 29, 31, 30 and 28 candles on consecutive seconds. The displayed result
changed `WAIT -> DOWN 2 MIN -> DOWN 3 MIN -> WAIT -> DOWN 2 MIN` even though no
new one-minute candle had closed. This was pixel-segmentation instability, not
new market information. The one-snapshot Structure Flow / Local Pattern engine
also produced late continuation calls and failed the reported five-trade demo
sample.

Version 1.1 is deprecated. Fixing the display reversal does not create or prove
a profitable strategy; v1.2 is built to reject unstable inputs and collect an
honest demo record.

## Chart visibility repair in v1.2.1

The official demo page can remember a line-chart view or fail to repaint its
canvas after the mobile WebView changes size. In that state the price line and
axis are visible but red/green candles are missing. The app now explicitly
enables hardware-accelerated WebView canvas rendering, closes the small deposit
promo when possible, requests a chart relayout and tries to select the visible
`Candles`/`Candlestick` chart option after page load.

The former `RELOAD CHART` control is now `FIX CHART`. Tap it to run the stronger
chart-menu repair. If Quotex changes its page labels and the menu remains open,
manually choose `Chart type -> Candles`. Long-press `FIX CHART` only when a full
official-page reload is needed. A close-gate status no longer falsely says
`Detected 0 candles` before an actual frame check. A verified zero-candle frame
instead says `CANDLE VIEW REQUIRED` and never produces a signal.

## Stable closed-candle gate

- The chart must be set to one-minute candles.
- At least 31 visible red/green candles are required: 30 closed candles plus
  the rightmost forming candle.
- Normal analysis starts only 0.9-8 seconds after a minute boundary. Outside
  that window the result is `WAIT FOR NEXT CLOSED CANDLE`, with no estimated
  signal countdown.
- The app captures three chart frames about 450 ms apart.
- Candle count, direction, profile and expiry must match on all three reads;
  setup score may vary by at most 5 and flow bias by at most 8.
- Any disagreement produces `WAIT - UNSTABLE CHART READ` and no direction.
- The forming rightmost candle is excluded from every strategy calculation.

## Contextual-only strategy

The old EMA, MACD, RSI, Bollinger, generic confluence, Structure Flow and Local
Pattern Match signal paths remain removed. Recent direction/efficiency can add
supporting points, but flow alone cannot issue a signal.

Only three complete price-action contexts are eligible:

1. **Liquidity Sweep** - a local high/low is swept and the candle closes back
   inside with directional rejection. Expiry: 2 minutes.
2. **Break + Retest** - a closed candle breaks a local structure level and the
   next closed candle holds the retest. Expiry: 3 minutes.
3. **Pullback Pulse** - an efficient leg, controlled opposite pullback and a
   directional resumption close. Expiry: 2, 3 or 5 minutes according to the
   measured persistence of the closed-candle structure.

Eligibility requires a complete context, at least 9 primary points and at least
a 5-point lead over the opposing direction. Flat movement, abnormal volatility,
random whipsaw and late entry after an extended one-way move are hard `WAIT`
filters. `SETUP x/100` is a rule-quality score, not win probability.

## Per-currency signal lock

Once a verified signal is displayed, its currency, direction, profile and
2/3/5-minute expiry are locked until that expiry ends. Re-scans cannot reverse
an active `UP` into `DOWN`, change its minutes, or replace it with `WAIT`.
Other currencies keep their own independent lock state.

The locked panel also shows `WHY UP/DOWN`, a plain-language setup reason,
directional UP-vs-DOWN points, the closed-candle count and `3/3 STABLE`. This is
an audit trail for what the pixel reader detected, not proof that the outcome
will win.

Auto Scan is armed for the next one-minute close, checks at most four visible
high-payout currencies with three reads each, and refuses a directional result
if the fresh-entry window has already passed. It never touches trade controls.

## Demo result record and risk stop

`MARK WIN` and `MARK LOSS` become valid only after the displayed expiry ends.
Each signal can be recorded once. The app stores total wins, losses, observed
demo percentage and the current loss streak on-device. These are user-entered
results, not independently verified statistics.

After three consecutive marked losses, live analysis and Auto Scan stop. `RESET
LOG` clears the record and resumes demo testing. Do not use martingale or raise
the amount after a loss. The app deliberately has no loss-recovery staking
system because no next trade can be guaranteed to recover an earlier loss.

## Evidence limits

The app reads rendered screen pixels, not a broker OHLC feed, order book,
institutional flow or news feed. It cannot establish how an OTC quotation was
formed and cannot predict a future candle with certainty. A deterministic unit
test proves code symmetry and safety rules only; it does not prove market win
rate.

Research shows that rules selected from many backtests can look strong in
sample and fail on unseen data:

- Probability of Backtest Overfitting:
  https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2326253
- Intraday FX out-of-sample study:
  https://ideas.repec.org/p/fip/fedlwp/1999-016.html
- CFTC/SEC binary-options warning:
  https://www.cftc.gov/LearnAndProtect/AdvisoriesAndArticles/fraudadv_binaryoptions.html
- Quotex FAQ and demo-account description:
  https://qxbroker.com/en/faq/

Use only the demo account and record every eligible signal. A 100-trade demo
sample is still an estimate, not a guarantee; real-money use is not recommended
on the basis of this app.

## Build and deterministic checks

GitHub Actions uses Java 17 and Android API 35, runs the pure-Java engine test,
builds the debug APK and uploads `QX-Stable-Close-Demo-APK-v4`.

The deterministic test verifies mirrored UP/DOWN scoring, contextual 2/3/5
minute expiry selection, rejection of raw trend/flow, late-entry protection,
flat/abnormal-volatility `WAIT` filters and the 30-closed-candle minimum.
