# QX Structure Pulse Demo

Android demo-only chart reader that displays `UP`, `DOWN`, or `WAIT` from the
currently rendered Quotex candle chart. Version `1.1.0-structure-pulse` keeps the
separate application ID `com.qx.adaptiveedge`, so the original native demo app
is not replaced.

## What changed in v1.1

The previous EMA, MACD, RSI, breakout/ROC, range-reversal, multi-indicator
confluence and two-frame confirmation engine was removed. The app no longer
shows a one- or two-minute signal countdown.

Every completed analysis now gives an immediate result from one closed-candle
snapshot:

- `UP • 2/3/5 MIN`;
- `DOWN • 2/3/5 MIN`; or
- `WAIT — NO EDGE NOW`, with no countdown.

The rightmost forming candle remains excluded so a partially formed candle
cannot repaint an entry decision. At least 17 visible red/green candles are
needed: 16 closed candles plus the forming candle.

## New price-structure engine

The engine uses only normalized OHLC candle geometry and current-chart context:

1. **Liquidity Sweep** — a local high/low is swept, then price closes back
   inside with directional rejection.
2. **Break + Retest** — a closed candle breaks a local structure level and the
   next closed candle holds the retest.
3. **Pullback Pulse** — an efficient directional leg, controlled opposing
   pullback and a resumption candle.
4. **Structure Flow** — recent displacement, directional efficiency,
   higher-high/higher-low or lower-high/lower-low structure, body quality and
   close location.
5. **Local Pattern Match** — the latest three-candle pulse is compared with
   earlier patterns on the same visible currency chart; the closest local
   analogues vote separately for 2-, 3- and 5-candle outcomes.

Flat movement, abnormal range expansion and random colour whipsaw remain hard
safety filters. A `SETUP 0-100` value is a rule-quality score, not a claimed win
probability.

## Speed and per-currency behavior

- Normal live analysis uses one snapshot; the old second-frame delay is gone.
- Auto Scan uses one snapshot per visible currency after the chart loads.
- Each currency is analyzed from its own visible candle history; frames from
  different currencies are never combined.
- A strong eligible currency can end Auto Scan early; otherwise the strongest
  available signal is selected after the scan.
- If no chart has a complete structure, the best current `WAIT` is shown
  immediately rather than estimating a future signal time.

## Expiry selection

- **2 minutes:** liquidity sweep or controlled short structure.
- **3 minutes:** break/retest or stronger six-candle continuation.
- **5 minutes:** at least 34 closed candles, persistent 12-candle structure and
  supporting same-direction local analogues.

## Evidence limits

This design deliberately avoids a promised win rate. Research on intraday FX
technical rules has found that attractive in-sample rules may fail out of sample,
and testing many variants increases backtest-overfitting risk. Candlestick
patterns also have weak predictive evidence when used without market context.

- Quotex short-term chart guide:
  https://blog.qxbroker.com/updates/fast-moves-quick-results-a-step-by-step-guide-to-short-term-stock-trading/
- Intraday FX out-of-sample study:
  https://ideas.repec.org/p/fip/fedlwp/1999-016.html
- Probability of Backtest Overfitting:
  https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2326253
- CFTC/SEC binary-options warning:
  https://www.cftc.gov/LearnAndProtect/AdvisoriesAndArticles/fraudadv_binaryoptions.html

The local-pattern component is adaptive but is not a substitute for verified
out-of-sample demo results. Use the app only to collect a demo trade log before
judging performance.

## Safe use

1. Use the Quotex demo account only.
2. Set the chart timeframe to one minute.
3. Keep at least 17 candles visible.
4. Enter only after a closed-candle signal appears; do not enter during the
   forming candle.
5. Record every signal, including losses. Do not use martingale.
6. Stop testing a version if the verified demo sample does not beat the payout's
   break-even win rate after a meaningful sample.

The app never clicks an amount/direction button and never places an order. It
does not guarantee profit or platform/legal eligibility in any jurisdiction.

## Build and test

GitHub Actions installs Android API 35 and build-tools 35.0.0, runs the plain
Java symmetry/safety test, builds the debug APK and uploads
`QX-Structure-Pulse-Demo-APK-v2`.

The deterministic test checks:

- mirrored UP/DOWN behavior;
- immediate structure-flow and liquidity-sweep signals;
- 2-, 3- and 5-minute expiry selection; and
- flat/whipsaw and abnormal-range `WAIT` filters.
