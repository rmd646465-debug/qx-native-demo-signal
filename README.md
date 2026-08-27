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
