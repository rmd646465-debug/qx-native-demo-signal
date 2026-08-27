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

## What it does

- Shows the official Quotex page inside the Android app.
- Reads visible candle pixels every 2.5 seconds.
- Uses symmetric trend slope, EMA proxy, RSI proxy, candle momentum, breakout and timing filters.
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
