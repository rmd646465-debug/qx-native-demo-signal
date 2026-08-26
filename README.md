# QX Native Demo Signal

Android demo-only Quotex chart analyzer. The app loads the official Quotex web page inside a secure WebView and analyzes the rendered red/green candle pixels on-device.

## What it does

- Shows the official Quotex page inside the Android app.
- Reads visible candle pixels every 2.5 seconds.
- Uses trend slope, EMA proxy, RSI proxy, candle momentum, breakout and timing filters.
- Shows **UP**, **DOWN**, or **WAIT** only when strict confluence is present.
- Keeps captured frames on the device and does not collect credentials.
- Never places a trade or clicks Quotex controls.

## Important

This is an experimental **demo-account learning tool**, not financial advice. It cannot guarantee 8/10 wins or any profit. OTC prices are proprietary and no external Binance/forex feed is used. Test at least 50–100 demo observations before judging the model.

## APK

Open the latest successful **Actions** run and download the `QX-Native-Demo-APK` artifact. Unzip it, then install `app-debug.apk` on Android 8.0 or newer.
