# QX Native Demo Signal

Android demo-only Quotex chart analyzer. The app loads the official Quotex web page inside a secure WebView and analyzes the rendered red/green candle pixels on-device.

## Version 2 fix

The original wide pixel crop also saw red/green controls below the chart. In
particular, the large red **Down** area could be mistaken for chart evidence and
create a bearish-only bias. Version 2:

- restricts detection to the mobile chart band and excludes the trade controls;
- rejects abnormal-height objects that are not candles;
- uses exactly mirrored bullish and bearish scoring rules;
- requires two consecutive matching scans before showing UP or DOWN;
- reports **setup strength**, not a made-up win probability;
- includes a deterministic mirror test proving that rising and falling inputs
  can produce UP and DOWN with equal strength.

## Version 2.1 connection fix

- Does not mark a WebView error page as `LIVE PAGE`.
- Automatically tries the official `quotex.com` address when `qxbroker.com`
  fails with a DNS host-lookup error.
- Shows a clear DNS error message when neither official address can load.

## Version 2.2 live-demo fix

- Opens the official no-registration demo-trade route instead of the marketing home page.
- Tries the official `qxbroker.com`, `market-qx.trade`, `market-qx.pro`, and
  `quotex.com` demo routes in sequence when DNS, 403, 429, or server errors occur.
- Accepts official cross-domain redirects, so a successful redirect can become
  `LIVE DEMO` and start chart analysis.
- Keeps the normal Android WebView identity and gives the live chart four
  seconds to initialize before scanning.
- Reloads the current selected asset page without discarding its session.

## Version 2.3 adaptive high-confirmation engine

- Requires at least 16 detected candles and 9/15 directional confluence.
- Combines short/medium/long trend, EMA alignment, MACD impulse, RSI,
  momentum, candle balance, breakout, trend efficiency and range position.
- Rejects choppy, very low-volatility, one-candle shock, conflicting-trend and
  overextended setups.
- Requires three consecutive matching scans before displaying UP or DOWN.
- Keeps bullish and bearish rules exact mirrors and verifies both directions,
  flat-market WAIT and volatility-shock WAIT in the deterministic build test.

## Version 2.4 reference UI and persistent official session

- Restores the reference title and `LIVE PAGE` status presentation.
- Detects official Sign In/Registration pages and pauses chart analysis instead
  of treating authentication pixels as candles.
- Saves the official Quotex WebView cookie/session after the user signs in on
  the official page and switches to DEMO, so later launches can return to the
  live demo dashboard without the app reading or storing the password itself.
- The embedded middle section remains the complete official page; it is not a
  fake balance, copied chart, or simulated Quotex interface.

## Version 2.5 current live-domain fix

- Opens the user's currently working `market-qx.info/en/` live page first.
- Keeps `market-qx.info` inside the app instead of sending it to Chrome.
- Accepts the current `market-quotex.pro` redirect as part of the embedded live
  page flow, while retaining the previous official fallback addresses.

## Version 2.6 adaptive live-signal fix

- Removes the incorrect phone-clock timing gate. The embedded chart countdown
  cannot be inferred from the phone's wall-clock seconds, and that gate could
  hold a valid chart setup at `WAIT`.
- Starts analysis with 12 detected candles, requires balanced 8/15 directional
  confluence, and still keeps medium/long-trend agreement plus all safety filters.
- Requires two matching scans before showing UP or DOWN, so a stable valid setup
  appears automatically in about five seconds instead of waiting for three scans.
- Continues to show `WAIT` for choppy, low-volatility, shock, conflicting or
  overextended charts. It does not force a trade signal.

## Version 2.7 rendered-chart capture fix

- Replaces `WebView.draw(Canvas)` capture with Android `PixelCopy`, which reads
  the hardware-rendered WebView pixels actually visible on the phone.
- Fixes the confirmed case where many red/green candles were visible but the
  app continuously reported `Detected 0 candles` because only the WebView
  background reached the analyzer bitmap.
- Keeps the v2.6 12-candle minimum, balanced 8/15 confluence, two-scan
  confirmation and all symmetric safety filters unchanged.

## Version 3.0 visible-asset scanner and expiry assistant

- Keeps the complete embedded DEMO page and the existing current-chart analyzer.
- Adds a separate `AUTO SCAN` mode. It opens the platform asset selector, reads
  up to 12 currently visible/open currency names, switches only among those
  currency elements, and analyzes two rendered chart frames per asset.
- Ranks only stable UP/DOWN results, returns the strongest asset name and
  direction, and brings that asset back into view. If none pass, it returns
  WAIT instead of manufacturing a recommendation.
- Shows a rule-based manual DEMO expiry suggestion of 1, 2, 3 or 5 minutes from
  setup strength. The duration is not a win probability or guarantee.
- The scanner never clicks UP/DOWN, never sets an investment and never places a
  trade. If the platform changes its asset-list HTML, current-chart mode remains
  available and the scanner reports that the list is unavailable.

## Version 3.1 precision and closed-candle update

- Removes the unvalidated 3/5-minute expiry mapping. A 1-minute rendered chart
  cannot justify a 5-minute recommendation from setup strength alone. Precision
  mode now suggests only 1 minute, or 2 minutes for the strongest aligned setup.
- Requires 25 visible candles, discards the rightmost still-forming candle, and
  analyzes at least 24 closed candles to reduce repainting.
- Raises directional acceptance from 8/15 with a 3-point lead to 10/15 with a
  5-point lead, strict short/medium/long alignment, trend-efficiency and recent
  candle-direction agreement.
- Requires three matching frames in both current-chart and AUTO SCAN modes.
- Uses the median coloured body edges as a more stable close-price proxy instead
  of the old full-candle center, and rejects late, overextended, oversized,
  low-volatility, choppy, shock and trend-conflict entries.
- AUTO SCAN prefers assets with at least 90% payout when the payout is readable
  from the platform list. The changes are designed to reduce false positives;
  they do not establish or guarantee a win rate.

## Version 3.2 adaptive eligible-signal update

- Reduces the minimum from 24 to 20 closed candles (21 visible including the
  still-forming candle), while continuing to exclude that forming candle.
- Uses adaptive 9/15 directional confluence with a four-point lead. Short,
  medium and long trend alignment, recent candle agreement, RSI, trend
  efficiency and all v3.1 safety filters are still mandatory.
- Requires two matching rendered frames instead of three, reducing current-chart
  confirmation time without accepting a single-frame signal.
- AUTO SCAN sorts readable payouts high-to-low, checks up to eight visible/open
  assets, uses two frames per asset and returns an eligible 86+ setup immediately.
  If no such asset exists it completes the scan and ranks the best valid result.
- No signal is forced: choppy, low-volatility, shock, trend-conflict,
  overextended, late-entry or oversized-candle setups remain WAIT.
- This is legal on-device visual technical analysis, not an exploit, hidden
  Quotex API, credential reader, auto-trader or guaranteed-profit system.

## What it does

- Shows the full official Quotex demo page inside the Android app, including
  its own asset/currency selector when the platform makes it available.
- Reads visible candle pixels every 2.5 seconds.
- Uses symmetric trend slope, EMA proxy, MACD impulse, RSI proxy, candle momentum,
  breakout, trend-efficiency, range-position and market-safety filters.
- Shows **UP**, **DOWN**, or **WAIT** only when strict confluence is present.
- Keeps captured frames on the device and does not collect credentials.
- Never places a trade or clicks Quotex controls.

## Important

This is an experimental **demo-account learning tool**, not financial advice. It cannot guarantee 8/10 wins or any profit. OTC prices are proprietary and no external Binance/forex feed is used. Test at least 100 demo observations and record every signal before judging the model. Do not use real money based on the displayed setup strength.

## Engine test

The GitHub workflow runs the plain-Java symmetry test before building the APK.
Locally, with a JDK installed:

```bash
mkdir -p build/engine-test
javac -d build/engine-test \
  app/src/main/java/com/qx/strictsignal/SignalEngine.java \
  engine-test/com/qx/strictsignal/SignalEngineSelfTest.java
java -cp build/engine-test com.qx.strictsignal.SignalEngineSelfTest
```

## APK

Open the latest successful **Actions** run and download the `QX-Native-Demo-APK-v2` artifact. Unzip it, then install `app-debug.apk` on Android 8.0 or newer. Uninstall the old APK first if Android reports a signature conflict.
