package com.qx.strictsignal;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demo-only on-device chart reader. It loads the official Quotex web page in a
 * WebView and analyzes rendered candle pixels. It never reads passwords,
 * injects trade actions, or sends captured frames to a server.
 */
public final class MainActivity extends Activity {
    private static final String[] START_URLS = {
            "https://quotex.io/en/",
            "https://qxbroker.com/en/"
    };
    private static final long SCAN_INTERVAL_MS = 2500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private TextView signalView;
    private TextView confidenceView;
    private TextView detailView;
    private TextView connectionView;
    private Button toggleButton;
    private volatile boolean pageReady;
    private volatile boolean scanning = true;
    private volatile boolean analysisBusy;
    private int startUrlIndex;
    private String activeMainUrl = START_URLS[0];
    private String failedMainUrl = "";

    private final Runnable scanLoop = new Runnable() {
        @Override public void run() {
            if (scanning && pageReady && !analysisBusy) captureAndAnalyze();
            mainHandler.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
        configureWebView();
        loadStartUrl(0);
        mainHandler.postDelayed(scanLoop, 3500L);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 12, 22));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(10), dp(9));
        header.setBackgroundColor(Color.rgb(10, 18, 32));

        TextView title = text("QX Native Demo Signal", 17, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));
        connectionView = text("CONNECTING", 10, Color.rgb(245, 179, 66), true);
        connectionView.setGravity(Gravity.CENTER);
        connectionView.setBackground(panel(Color.rgb(46, 55, 72), 10));
        header.addView(connectionView, new LinearLayout.LayoutParams(dp(95), dp(34)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout signalPanel = new LinearLayout(this);
        signalPanel.setOrientation(LinearLayout.VERTICAL);
        signalPanel.setGravity(Gravity.CENTER);
        signalPanel.setPadding(dp(12), dp(9), dp(12), dp(9));
        signalPanel.setBackground(panel(Color.rgb(17, 27, 44), 0));

        signalView = text("WAIT", 28, Color.rgb(245, 179, 66), true);
        signalView.setGravity(Gravity.CENTER);
        confidenceView = text("Live chart loading…", 11, Color.rgb(171, 183, 201), false);
        confidenceView.setGravity(Gravity.CENTER);
        detailView = text("Official Quotex page • On-device pixel analysis • DEMO ONLY", 9, Color.rgb(114, 130, 153), false);
        detailView.setGravity(Gravity.CENTER);
        signalPanel.addView(signalView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        signalPanel.addView(confidenceView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        signalPanel.addView(detailView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        root.addView(signalPanel);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 12, 22));
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), dp(8), dp(8), dp(8));
        controls.setBackgroundColor(Color.rgb(10, 18, 32));

        toggleButton = button("PAUSE ANALYSIS", Color.rgb(11, 139, 91));
        toggleButton.setOnClickListener(v -> {
            scanning = !scanning;
            toggleButton.setText(scanning ? "PAUSE ANALYSIS" : "START ANALYSIS");
            if (!scanning) showWait("Paused by user", "Analysis stopped • Chart remains live");
        });
        controls.addView(toggleButton, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button reload = button("RELOAD CHART", Color.rgb(33, 97, 156));
        reload.setOnClickListener(v -> {
            pageReady = false;
            connectionView.setText("RELOADING");
            webView.reload();
        });
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        reloadParams.setMarginStart(dp(8));
        controls.addView(reload, reloadParams);
        root.addView(controls);

        TextView risk = text("DEMO ONLY • Signals are experimental • No win guarantee • No auto-trade", 9, Color.rgb(255, 204, 111), true);
        risk.setGravity(Gravity.CENTER);
        risk.setPadding(dp(8), dp(6), dp(8), dp(7));
        root.addView(risk);
        return root;
    }

    private void configureWebView() {
        android.webkit.WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " QXNativeDemo/1.0");
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                activeMainUrl = url;
                failedMainUrl = "";
                pageReady = false;
                connectionView.setText("LOADING");
                connectionView.setTextColor(Color.rgb(245, 179, 66));
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!url.equals(activeMainUrl) || url.equals(failedMainUrl)) return;
                pageReady = true;
                connectionView.setText("LIVE PAGE");
                connectionView.setTextColor(Color.rgb(80, 230, 169));
                detailView.setText("Chart rendered inside app • Analysis stays on this device");
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  android.webkit.WebResourceError error) {
                if (!request.isForMainFrame()) return;
                String failedUrl = request.getUrl().toString();
                failedMainUrl = failedUrl;
                pageReady = false;
                if (startUrlIndex + 1 < START_URLS.length) {
                    int nextIndex = startUrlIndex + 1;
                    connectionView.setText("SWITCHING");
                    showWait("Site unavailable", "Trying alternative official domain");
                    mainHandler.postDelayed(() -> loadStartUrl(nextIndex), 500L);
                } else {
                    connectionView.setText("NETWORK ERROR");
                    connectionView.setTextColor(Color.rgb(239, 83, 80));
                    showWait("Connection failed", "Check internet or Private DNS, then reload");
                }
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equalsIgnoreCase(uri.getScheme())) return true;
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
                if (isQuotexHost(host)) return false;
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri));
                return true;
            }

            @Override public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
                showWait("Security block", "Unsafe redirect was blocked");
            }
        });
    }

    private void loadStartUrl(int index) {
        startUrlIndex = Math.max(0, Math.min(index, START_URLS.length - 1));
        activeMainUrl = START_URLS[startUrlIndex];
        failedMainUrl = "";
        pageReady = false;
        webView.loadUrl(activeMainUrl);
    }

    private boolean isQuotexHost(String host) {
        return host.contains("qxbroker") || host.contains("quotex") || host.contains("market-qx") || host.contains("qx-market");
    }

    private void captureAndAnalyze() {
        int width = webView.getWidth();
        int height = webView.getHeight();
        if (width < 200 || height < 300) return;
        analysisBusy = true;
        final Bitmap frame;
        try {
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            webView.draw(canvas);
        } catch (Throwable error) {
            analysisBusy = false;
            showWait("Capture unavailable", "Reload chart and try again");
            return;
        }

        analyzerExecutor.execute(() -> {
            AnalysisResult result;
            try {
                result = CandleAnalyzer.analyze(frame);
            } catch (Throwable error) {
                result = AnalysisResult.waiting("Analysis error", 0, 0, "Chart pixels could not be read");
            } finally {
                frame.recycle();
            }
            AnalysisResult finalResult = result;
            runOnUiThread(() -> {
                renderResult(finalResult);
                analysisBusy = false;
            });
        });
    }

    private void renderResult(AnalysisResult result) {
        int color;
        String symbol;
        if ("UP".equals(result.direction)) {
            color = Color.rgb(22, 199, 132);
            symbol = "↑ UP";
        } else if ("DOWN".equals(result.direction)) {
            color = Color.rgb(239, 83, 80);
            symbol = "↓ DOWN";
        } else {
            color = Color.rgb(245, 179, 66);
            symbol = "WAIT";
        }
        signalView.setText(symbol);
        signalView.setTextColor(color);
        confidenceView.setText(result.confidence > 0
                ? result.status + " • " + result.confidence + "% confidence"
                : result.status);
        detailView.setText("Detected " + result.candles + " candles • RSI " + result.rsi + " • " + result.detail);
    }

    private void showWait(String status, String detail) {
        renderResult(AnalysisResult.waiting(status, 0, 0, detail));
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(10);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setBackground(panel(color, 10));
        return button;
    }

    private GradientDrawable panel(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), Color.rgb(42, 56, 78));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(scanLoop);
        analyzerExecutor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private static final class AnalysisResult {
        final String direction;
        final int confidence;
        final int candles;
        final int rsi;
        final String status;
        final String detail;

        AnalysisResult(String direction, int confidence, int candles, int rsi, String status, String detail) {
            this.direction = direction;
            this.confidence = confidence;
            this.candles = candles;
            this.rsi = rsi;
            this.status = status;
            this.detail = detail;
        }

        static AnalysisResult waiting(String status, int candles, int rsi, String detail) {
            return new AnalysisResult("WAIT", 0, candles, rsi, status, detail);
        }
    }

    private static final class Candle {
        final float x;
        final float price;
        final float height;
        final boolean up;

        Candle(float x, float price, float height, boolean up) {
            this.x = x;
            this.price = price;
            this.height = height;
            this.up = up;
        }
    }

    private static final class CandleAnalyzer {
        private static AnalysisResult analyze(Bitmap source) {
            int targetWidth = Math.min(540, source.getWidth());
            int targetHeight = Math.max(1, Math.round(source.getHeight() * (targetWidth / (float) source.getWidth())));
            Bitmap bitmap = source.getWidth() == targetWidth
                    ? source
                    : Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
            try {
                List<Candle> candles = extractCandles(bitmap);
                if (candles.size() < 18) {
                    return AnalysisResult.waiting("Chart not ready", candles.size(), 0,
                            "Zoom chart until 20+ red/green candles are visible");
                }

                if (candles.size() > 45) candles = new ArrayList<>(candles.subList(candles.size() - 45, candles.size()));
                float[] prices = new float[candles.size()];
                float averageHeight = 0f;
                for (int i = 0; i < candles.size(); i++) {
                    prices[i] = candles.get(i).price;
                    averageHeight += candles.get(i).height;
                }
                averageHeight = Math.max(2f, averageHeight / candles.size());

                float shortSlope = regressionSlope(prices, Math.min(8, prices.length)) / averageHeight;
                float longSlope = regressionSlope(prices, Math.min(18, prices.length)) / averageHeight;
                float ema5 = ema(prices, 5);
                float ema13 = ema(prices, 13);
                int rsi = Math.round(rsi(prices));

                int momentum = 0;
                for (int i = Math.max(0, candles.size() - 7); i < candles.size(); i++) momentum += candles.get(i).up ? 1 : -1;
                float latest = prices[prices.length - 1];
                float priorMax = -Float.MAX_VALUE;
                float priorMin = Float.MAX_VALUE;
                for (int i = Math.max(0, prices.length - 9); i < prices.length - 1; i++) {
                    priorMax = Math.max(priorMax, prices[i]);
                    priorMin = Math.min(priorMin, prices[i]);
                }

                int votes = 0;
                votes += shortSlope > 0.10f ? 1 : shortSlope < -0.10f ? -1 : 0;
                votes += longSlope > 0.06f ? 1 : longSlope < -0.06f ? -1 : 0;
                votes += ema5 > ema13 + averageHeight * 0.08f ? 1 : ema5 < ema13 - averageHeight * 0.08f ? -1 : 0;
                votes += momentum >= 3 ? 1 : momentum <= -3 ? -1 : 0;
                votes += latest > priorMax ? 1 : latest < priorMin ? -1 : 0;
                votes += rsi >= 54 && rsi <= 72 ? 1 : rsi <= 46 && rsi >= 28 ? -1 : 0;

                int confidence = Math.min(92, 48 + Math.abs(votes) * 8 + Math.min(8, candles.size() / 6));
                String candidate = votes >= 4 ? "UP" : votes <= -4 ? "DOWN" : "WAIT";
                if (("UP".equals(candidate) && rsi > 76) || ("DOWN".equals(candidate) && rsi < 24)) candidate = "WAIT";

                int second = (int) ((System.currentTimeMillis() / 1000L) % 60L);
                boolean timingWindow = second >= 50 || second <= 10;
                if ("WAIT".equals(candidate)) {
                    return AnalysisResult.waiting("No 4/6 confluence", candles.size(), rsi,
                            "Trend/momentum filters disagree");
                }
                if (!timingWindow) {
                    return new AnalysisResult("WAIT", confidence, candles.size(), rsi,
                            "Timing WAIT", candidate + " candidate • scan again at 00:50–00:10");
                }
                String status = second >= 50 ? "PREPARE NEXT CANDLE" : "ENTRY WINDOW";
                return new AnalysisResult(candidate, confidence, candles.size(), rsi, status,
                        "6-layer pixel confluence • fixed 2m demo expiry");
            } finally {
                if (bitmap != source) bitmap.recycle();
            }
        }

        private static List<Candle> extractCandles(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int left = Math.round(width * 0.04f);
            int right = Math.round(width * 0.88f);
            int top = Math.round(height * 0.14f);
            int bottom = Math.round(height * 0.86f);
            int columns = Math.max(1, right - left);
            int[] green = new int[columns];
            int[] red = new int[columns];
            int[] minY = new int[columns];
            int[] maxY = new int[columns];
            java.util.Arrays.fill(minY, Integer.MAX_VALUE);

            float[] hsv = new float[3];
            for (int x = left; x < right; x += 2) {
                int index = x - left;
                for (int y = top; y < bottom; y += 2) {
                    int color = bitmap.getPixel(x, y);
                    Color.colorToHSV(color, hsv);
                    float hue = hsv[0], saturation = hsv[1], value = hsv[2];
                    boolean isGreen = saturation > 0.30f && value > 0.24f && hue >= 75f && hue <= 190f;
                    boolean isRed = saturation > 0.34f && value > 0.28f && (hue <= 28f || hue >= 332f);
                    if (isGreen || isRed) {
                        if (isGreen) green[index]++; else red[index]++;
                        minY[index] = Math.min(minY[index], y);
                        maxY[index] = Math.max(maxY[index], y);
                    }
                }
                if (index + 1 < columns) {
                    green[index + 1] = green[index];
                    red[index + 1] = red[index];
                    minY[index + 1] = minY[index];
                    maxY[index + 1] = maxY[index];
                }
            }

            List<Candle> found = new ArrayList<>();
            int start = -1;
            int side = 0;
            for (int i = 0; i <= columns; i++) {
                int nextSide = 0;
                if (i < columns && Math.max(green[i], red[i]) >= 2) nextSide = green[i] >= red[i] ? 1 : -1;
                if (start < 0 && nextSide != 0) {
                    start = i;
                    side = nextSide;
                } else if (start >= 0 && (nextSide == 0 || nextSide != side || i - start > 28)) {
                    addCandle(found, start, i - 1, side, left, green, red, minY, maxY);
                    start = nextSide == 0 ? -1 : i;
                    side = nextSide;
                }
            }
            Collections.sort(found, Comparator.comparingDouble(candle -> candle.x));
            return found;
        }

        private static void addCandle(List<Candle> out, int start, int end, int side, int left,
                                      int[] green, int[] red, int[] minY, int[] maxY) {
            if (end < start) return;
            int best = start;
            int bestCount = -1;
            int top = Integer.MAX_VALUE;
            int bottom = -1;
            for (int i = start; i <= end; i++) {
                int count = side > 0 ? green[i] : red[i];
                if (count > bestCount) { bestCount = count; best = i; }
                if (minY[i] != Integer.MAX_VALUE) top = Math.min(top, minY[i]);
                bottom = Math.max(bottom, maxY[i]);
            }
            int candleHeight = bottom - top;
            int candleWidth = end - start + 1;
            if (bestCount < 2 || candleHeight < 6 || candleWidth > 30) return;
            float centerY = (top + bottom) * 0.5f;
            out.add(new Candle(left + (start + end) * 0.5f, -centerY, candleHeight, side > 0));
        }

        private static float ema(float[] values, int period) {
            float alpha = 2f / (period + 1f);
            float value = values[0];
            for (int i = 1; i < values.length; i++) value = values[i] * alpha + value * (1f - alpha);
            return value;
        }

        private static float rsi(float[] values) {
            int start = Math.max(1, values.length - 14);
            float gains = 0f, losses = 0f;
            int count = 0;
            for (int i = start; i < values.length; i++) {
                float delta = values[i] - values[i - 1];
                if (delta > 0) gains += delta; else losses -= delta;
                count++;
            }
            if (count == 0) return 50f;
            gains /= count;
            losses /= count;
            if (losses < 0.0001f) return 100f;
            float rs = gains / losses;
            return 100f - 100f / (1f + rs);
        }

        private static float regressionSlope(float[] values, int count) {
            int start = Math.max(0, values.length - count);
            int n = values.length - start;
            if (n < 2) return 0f;
            float sumX = 0f, sumY = 0f, sumXY = 0f, sumXX = 0f;
            for (int i = 0; i < n; i++) {
                float y = values[start + i];
                sumX += i;
                sumY += y;
                sumXY += i * y;
                sumXX += i * i;
            }
            float denominator = n * sumXX - sumX * sumX;
            return Math.abs(denominator) < 0.0001f ? 0f : (n * sumXY - sumX * sumY) / denominator;
        }
    }
}
