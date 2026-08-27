package com.qx.strictsignal;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Demo-only on-device chart reader. It loads the official Quotex web page in a
 * WebView and analyzes rendered candle pixels. It never reads passwords,
 * injects trade actions, or sends captured frames to a server.
 */
public final class MainActivity extends Activity {
    private static final String[] START_URLS = {
            "https://market-qx.info/en/",
            "https://market-quotex.pro/en/",
            "https://qxbroker.com/en/demo-trade",
            "https://market-qx.trade/en/demo-trade",
            "https://market-qx.pro/en/demo-trade",
            "https://quotex.com/en/demo-trade"
    };
    private static final long SCAN_INTERVAL_MS = 2500L;
    private static final long CHART_WARMUP_MS = 4000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private TextView signalView;
    private TextView strengthView;
    private TextView detailView;
    private TextView connectionView;
    private Button toggleButton;
    private Button assetScanButton;
    private volatile boolean pageReady;
    private volatile boolean scanning = true;
    private volatile boolean analysisBusy;
    private boolean assetScanActive;
    private final List<String> assetQueue = new ArrayList<>();
    private final List<AssetScanResult> assetScanResults = new ArrayList<>();
    private int assetScanIndex;
    private String currentAssetName = "CURRENT ASSET";
    private String lastCandidate = "WAIT";
    private int candidateStreak;
    private int startUrlIndex;
    private String currentLoadUrl = START_URLS[0];
    private String failedMainFrameUrl = "";

    private final Runnable scanLoop = new Runnable() {
        @Override public void run() {
            if (scanning && pageReady && !analysisBusy && !assetScanActive) {
                refreshCurrentAssetName(null);
                captureAndAnalyze();
            }
            mainHandler.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
        configureWebView();
        loadStartUrl(0, true);
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

        TextView title = text("QX Native Demo Signal", 16, Color.WHITE, true);
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
        strengthView = text("Live chart loading…", 11, Color.rgb(171, 183, 201), false);
        strengthView.setGravity(Gravity.CENTER);
        detailView = text("15-layer adaptive confirmation • DEMO ONLY", 9, Color.rgb(114, 130, 153), false);
        detailView.setGravity(Gravity.CENTER);
        signalPanel.addView(signalView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        signalPanel.addView(strengthView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
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

        assetScanButton = button("AUTO SCAN", Color.rgb(113, 74, 181));
        assetScanButton.setOnClickListener(v -> {
            if (assetScanActive) {
                stopAssetScan("Asset scan stopped", "Current chart analysis continues");
            } else {
                beginAssetScan();
            }
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        scanParams.setMarginStart(dp(6));
        controls.addView(assetScanButton, scanParams);

        Button reload = button("RELOAD CHART", Color.rgb(33, 97, 156));
        reload.setOnClickListener(v -> {
            pageReady = false;
            connectionView.setText("RELOADING");
            String visibleUrl = webView.getUrl();
            if (isOfficialUrl(visibleUrl)) webView.reload();
            else loadStartUrl(0, true);
        });
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        reloadParams.setMarginStart(dp(6));
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
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Keep Android's normal mobile WebView user-agent. Appending a custom
        // bot-style token can make an otherwise valid Quotex session fail.
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (isOfficialUrl(url)) {
                    pageReady = false;
                    connectionView.setText("LOADING");
                    connectionView.setTextColor(Color.rgb(245, 179, 66));
                }
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!isOfficialUrl(url) || sameHost(url, failedMainFrameUrl)) return;
                CookieManager.getInstance().flush();
                if (isAuthenticationUrl(url)) {
                    pageReady = false;
                    connectionView.setText("LOGIN ONCE");
                    connectionView.setTextColor(Color.rgb(245, 179, 66));
                    showWait("Official login required",
                            "Sign in here, switch to DEMO, then the session stays saved");
                    return;
                }
                connectionView.setText("OPENING DEMO");
                connectionView.setTextColor(Color.rgb(245, 179, 66));
                mainHandler.postDelayed(() -> markOfficialPageReady(view.getUrl()), CHART_WARMUP_MS);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  WebResourceError error) {
                if (!request.isForMainFrame() || !isOfficialUrl(request.getUrl().toString())) return;
                pageReady = false;
                failedMainFrameUrl = request.getUrl().toString();
                if (error.getErrorCode() == WebViewClient.ERROR_HOST_LOOKUP
                        && startUrlIndex + 1 < START_URLS.length) {
                    connectionView.setText("TRY BACKUP");
                    connectionView.setTextColor(Color.rgb(245, 179, 66));
                    showWait("DNS fallback", "Primary address failed • trying official backup");
                    mainHandler.postDelayed(
                            () -> loadStartUrl(startUrlIndex + 1, false), 500L);
                } else {
                    connectionView.setText("DNS ERROR");
                    connectionView.setTextColor(Color.rgb(239, 83, 80));
                    showWait("Website unavailable",
                            "Try mobile data or Private DNS: dns.google");
                }
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                       WebResourceResponse response) {
                if (!request.isForMainFrame() || !isOfficialUrl(request.getUrl().toString())) return;
                int status = response.getStatusCode();
                if (status != 403 && status != 429 && status < 500) return;
                pageReady = false;
                failedMainFrameUrl = request.getUrl().toString();
                if (startUrlIndex + 1 < START_URLS.length) {
                    connectionView.setText("TRY BACKUP");
                    showWait("Official page retry", "Server " + status + " • trying another official address");
                    mainHandler.postDelayed(() -> loadStartUrl(startUrlIndex + 1, false), 700L);
                } else {
                    connectionView.setText("PAGE BLOCKED");
                    connectionView.setTextColor(Color.rgb(239, 83, 80));
                    showWait("Official page blocked", "Update Android System WebView or try mobile data");
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

    private void markOfficialPageReady(String url) {
        if (!isOfficialUrl(url) || isAuthenticationUrl(url)
                || sameHost(url, failedMainFrameUrl)) return;
        pageReady = true;
        failedMainFrameUrl = "";
        connectionView.setText("LIVE PAGE");
        connectionView.setTextColor(Color.rgb(80, 230, 169));
        detailView.setText("Official demo page • 15-layer on-device analysis");
        refreshCurrentAssetName(null);
    }

    private void loadStartUrl(int index, boolean clearFailure) {
        startUrlIndex = Math.max(0, Math.min(index, START_URLS.length - 1));
        currentLoadUrl = START_URLS[startUrlIndex];
        if (clearFailure) failedMainFrameUrl = "";
        pageReady = false;
        webView.loadUrl(currentLoadUrl);
    }

    private boolean sameHost(String firstUrl, String secondUrl) {
        if (firstUrl == null || secondUrl == null || secondUrl.isEmpty()) return false;
        String firstHost = Uri.parse(firstUrl).getHost();
        String secondHost = Uri.parse(secondUrl).getHost();
        return firstHost != null && secondHost != null
                && firstHost.equalsIgnoreCase(secondHost);
    }

    private boolean isQuotexHost(String host) {
        return isHostOrSubdomain(host, "qxbroker.com")
                || isHostOrSubdomain(host, "quotex.com")
                || isHostOrSubdomain(host, "market-qx.info")
                || isHostOrSubdomain(host, "market-quotex.pro")
                || isHostOrSubdomain(host, "market-qx.trade")
                || isHostOrSubdomain(host, "market-qx.pro");
    }

    private boolean isOfficialUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme())
                && host != null && isQuotexHost(host.toLowerCase(Locale.US));
    }

    private boolean isAuthenticationUrl(String url) {
        if (url == null) return false;
        String path = Uri.parse(url).getPath();
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.US);
        return lower.contains("sign-in") || lower.contains("sign-up")
                || lower.contains("login") || lower.contains("registration");
    }

    private boolean isHostOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private String visibleAssetScript() {
        return "(function(){const re=/([A-Z]{3})\\s*\\/\\s*([A-Z]{3})/i;"
                + "let best='',area=1e30;for(const e of document.querySelectorAll('body *')){"
                + "const r=e.getBoundingClientRect(),s=getComputedStyle(e);"
                + "if(r.width<18||r.height<8||r.bottom<0||r.top>innerHeight||s.display==='none'||s.visibility==='hidden')continue;"
                + "const t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim().toUpperCase();"
                + "if(t.length>80)continue;const m=t.match(re);if(!m)continue;"
                + "const a=r.width*r.height;if(a<area){area=a;best=m[1]+'/'+m[2]+(t.includes('OTC')?' (OTC)':'');}}"
                + "return best;})()";
    }

    private String openAssetListScript() {
        return "(function(){const re=/([A-Z]{3})\\s*\\/\\s*([A-Z]{3})/i;let all=[];"
                + "for(const e of document.querySelectorAll('body *')){const r=e.getBoundingClientRect(),s=getComputedStyle(e);"
                + "if(r.width<18||r.height<8||r.bottom<0||r.top>innerHeight||s.display==='none'||s.visibility==='hidden')continue;"
                + "const t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim();"
                + "if(t.length<=80&&re.test(t))all.push({e:e,a:r.width*r.height});}"
                + "all.sort((x,y)=>x.a-y.a);if(!all.length)return false;let t=all[0].e;"
                + "for(let i=0;i<5&&t.parentElement;i++){const p=t.parentElement,pt=(p.innerText||'').trim();"
                + "if(pt.length>160)break;if(getComputedStyle(p).cursor==='pointer'||p.getAttribute('role')==='button'||p.onclick){t=p;break;}t=p;}"
                + "t.click();return true;})()";
    }

    private String collectVisibleAssetsScript() {
        return "(function(){const re=/([A-Z]{3})\\s*\\/\\s*([A-Z]{3})/i,map=new Map();"
                + "for(const e of document.querySelectorAll('body *')){const r=e.getBoundingClientRect(),s=getComputedStyle(e);"
                + "if(r.width<18||r.height<8||r.bottom<0||r.top>innerHeight||s.display==='none'||s.visibility==='hidden')continue;"
                + "const t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim().toUpperCase();"
                + "if(t.length>100)continue;const m=t.match(re);if(!m)continue;const pair=m[1]+'/'+m[2];"
                + "const name=pair+(t.includes('OTC')?' (OTC)':'');if(!map.has(pair)||name.includes('OTC'))map.set(pair,name);}"
                + "return Array.from(map.values()).slice(0,12);})()";
    }

    private String selectAssetScript(String asset) {
        String wanted = JSONObject.quote(asset.replace(" (OTC)", "").trim().toUpperCase());
        return "(function(){const want=" + wanted
                + ",re=/([A-Z]{3})\\s*\\/\\s*([A-Z]{3})/i;let all=[];"
                + "for(const e of document.querySelectorAll('body *')){const r=e.getBoundingClientRect(),s=getComputedStyle(e);"
                + "if(r.width<18||r.height<8||r.bottom<0||r.top>innerHeight||s.display==='none'||s.visibility==='hidden')continue;"
                + "const t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim();if(t.length>100)continue;"
                + "const m=t.match(re);if(!m||m[1].toUpperCase()+'/'+m[2].toUpperCase()!==want)continue;"
                + "all.push({e:e,a:r.width*r.height});}all.sort((x,y)=>x.a-y.a);if(!all.length)return false;"
                + "let t=all[0].e;for(let i=0;i<5&&t.parentElement;i++){const p=t.parentElement,pt=(p.innerText||'').trim();"
                + "if(pt.length>160)break;if(getComputedStyle(p).cursor==='pointer'||p.getAttribute('role')==='button'||p.onclick){t=p;break;}t=p;}"
                + "t.click();return true;})()";
    }

    private void refreshCurrentAssetName(Runnable after) {
        if (webView == null || !pageReady) {
            if (after != null) after.run();
            return;
        }
        webView.evaluateJavascript(visibleAssetScript(), value -> {
            try {
                Object parsed = new JSONTokener(value).nextValue();
                if (parsed instanceof String && !((String) parsed).trim().isEmpty()) {
                    currentAssetName = ((String) parsed).trim();
                }
            } catch (Throwable ignored) {
                // The current-chart analyzer remains usable if page labels change.
            }
            if (after != null) after.run();
        });
    }

    private void beginAssetScan() {
        if (!pageReady || webView == null) {
            showWait("Live demo not ready", "Open DEMO chart, then tap AUTO SCAN");
            return;
        }
        assetScanActive = true;
        assetQueue.clear();
        assetScanResults.clear();
        assetScanIndex = 0;
        lastCandidate = "WAIT";
        candidateStreak = 0;
        assetScanButton.setText("STOP SCAN");
        showWait("Opening currency list", "Reading visible/open Quotex assets only");
        webView.evaluateJavascript(openAssetListScript(), ignored ->
                mainHandler.postDelayed(this::collectAssetQueue, 900L));
    }

    private void collectAssetQueue() {
        if (!assetScanActive) return;
        webView.evaluateJavascript(collectVisibleAssetsScript(), value -> {
            try {
                JSONArray array = new JSONArray(value);
                for (int i = 0; i < array.length(); i++) {
                    String asset = array.optString(i, "").trim();
                    if (!asset.isEmpty() && !assetQueue.contains(asset)) assetQueue.add(asset);
                }
            } catch (Throwable ignored) {
                assetQueue.clear();
            }
            if (assetQueue.isEmpty()) {
                stopAssetScan("Currency list unavailable",
                        "Current chart mode is safe • open the asset list and retry");
                return;
            }
            scanNextAsset();
        });
    }

    private void scanNextAsset() {
        if (!assetScanActive) return;
        if (assetScanIndex >= assetQueue.size()) {
            finishAssetScan();
            return;
        }
        String asset = assetQueue.get(assetScanIndex);
        showWait("Scanning " + (assetScanIndex + 1) + "/" + assetQueue.size(),
                asset + " • two rendered-chart confirmations");
        Consumer<Boolean> selected = ok -> {
            if (!assetScanActive) return;
            if (!ok) {
                assetScanIndex++;
                scanNextAsset();
                return;
            }
            currentAssetName = asset;
            mainHandler.postDelayed(() -> scanFirstFrame(asset), CHART_WARMUP_MS);
        };
        if (assetScanIndex == 0) selectVisibleAsset(asset, selected);
        else openThenSelectAsset(asset, selected);
    }

    private void openThenSelectAsset(String asset, Consumer<Boolean> callback) {
        webView.evaluateJavascript(openAssetListScript(), ignored ->
                mainHandler.postDelayed(() -> selectVisibleAsset(asset, callback), 650L));
    }

    private void selectVisibleAsset(String asset, Consumer<Boolean> callback) {
        webView.evaluateJavascript(selectAssetScript(asset), value ->
                callback.accept("true".equalsIgnoreCase(value)));
    }

    private void scanFirstFrame(String asset) {
        if (!assetScanActive) return;
        captureAndAnalyze(first -> {
            if (!assetScanActive) return;
            mainHandler.postDelayed(() -> captureAndAnalyze(second -> {
                if (!assetScanActive) return;
                AnalysisResult stable = second;
                if (!first.direction.equals(second.direction)
                        || ("WAIT".equals(first.direction) && !first.status.equals(second.status))) {
                    stable = AnalysisResult.waiting("Unstable setup",
                            Math.max(first.candles, second.candles), second.rsi,
                            "Two scans did not agree • skipped");
                }
                assetScanResults.add(new AssetScanResult(asset, stable));
                assetScanIndex++;
                scanNextAsset();
            }), SCAN_INTERVAL_MS);
        });
    }

    private void finishAssetScan() {
        AssetScanResult best = null;
        for (AssetScanResult candidate : assetScanResults) {
            if ("WAIT".equals(candidate.result.direction)) continue;
            if (best == null || candidate.result.strength > best.result.strength) best = candidate;
        }
        assetScanActive = false;
        assetScanButton.setText("AUTO SCAN");
        if (best == null) {
            currentAssetName = "SCANNED " + assetScanResults.size() + " ASSETS";
            renderResult(AnalysisResult.waiting("No high-confirmation asset", 0, 0,
                    "All scanned setups were WAIT • no forced signal"));
            return;
        }
        AssetScanResult winner = best;
        currentAssetName = winner.asset;
        renderResult(winner.result);
        openThenSelectAsset(winner.asset, ignored -> { });
    }

    private void stopAssetScan(String status, String detail) {
        assetScanActive = false;
        assetQueue.clear();
        assetScanResults.clear();
        assetScanButton.setText("AUTO SCAN");
        showWait(status, detail);
    }

    private void captureAndAnalyze() {
        captureAndAnalyze(null);
    }

    private void captureAndAnalyze(Consumer<AnalysisResult> receiver) {
        int width = webView.getWidth();
        int height = webView.getHeight();
        if (width < 200 || height < 300) {
            deliverAnalysis(receiver, AnalysisResult.waiting("Chart size unavailable", 0, 0,
                    "Reload chart and try again"));
            return;
        }
        analysisBusy = true;
        final Bitmap frame;
        try {
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable error) {
            deliverAnalysis(receiver, AnalysisResult.waiting("Capture unavailable", 0, 0,
                    "Reload chart and try again"));
            return;
        }

        // WebView.draw(Canvas) can return only the WebView background on some
        // hardware-accelerated Android/WebView combinations even though the
        // chart is visible on screen. PixelCopy reads the actually rendered
        // window pixels and prevents the false "Detected 0 candles" result.
        int[] location = new int[2];
        webView.getLocationInWindow(location);
        Rect source = new Rect(location[0], location[1],
                location[0] + width, location[1] + height);
        try {
            PixelCopy.request(getWindow(), source, frame, copyResult -> {
                if (copyResult == PixelCopy.SUCCESS) {
                    analyzeFrame(frame, receiver);
                } else {
                    frame.recycle();
                    deliverAnalysis(receiver, AnalysisResult.waiting("Capture retry", 0, 0,
                            "Chart frame not ready • automatic retry"));
                }
            }, mainHandler);
        } catch (Throwable error) {
            frame.recycle();
            deliverAnalysis(receiver, AnalysisResult.waiting("Capture unavailable", 0, 0,
                    "Reload chart and try again"));
        }
    }

    private void analyzeFrame(Bitmap frame, Consumer<AnalysisResult> receiver) {
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
            runOnUiThread(() -> deliverAnalysis(receiver, finalResult));
        });
    }

    private void deliverAnalysis(Consumer<AnalysisResult> receiver, AnalysisResult result) {
        analysisBusy = false;
        if (receiver != null) receiver.accept(result);
        else renderResult(stabilize(result));
    }

    private AnalysisResult stabilize(AnalysisResult result) {
        if ("WAIT".equals(result.direction)) {
            lastCandidate = "WAIT";
            candidateStreak = 0;
            return result;
        }
        if (result.direction.equals(lastCandidate)) candidateStreak++;
        else {
            lastCandidate = result.direction;
            candidateStreak = 1;
        }
        if (candidateStreak < 2) {
            return AnalysisResult.waiting("Confirming " + result.direction, result.candles,
                    result.rsi, result.detail + " • need 2 stable scans");
        }
        return result;
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
        if (result.strength > 0) {
            int minutes = suggestedExpiryMinutes(result);
            strengthView.setText(currentAssetName + " • " + result.direction
                    + " • " + minutes + " MIN DEMO");
            detailView.setText(result.strength + "% setup strength • Detected "
                    + result.candles + " candles • RSI " + result.rsi);
        } else {
            strengthView.setText(currentAssetName + " • " + result.status);
            detailView.setText("Detected " + result.candles + " candles • RSI "
                    + result.rsi + " • " + result.detail);
        }
    }

    private int suggestedExpiryMinutes(AnalysisResult result) {
        if (result.strength >= 90) return 5;
        if (result.strength >= 86) return 3;
        if (result.strength >= 82) return 2;
        return 1;
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
        CookieManager.getInstance().flush();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    private static final class AssetScanResult {
        final String asset;
        final AnalysisResult result;

        AssetScanResult(String asset, AnalysisResult result) {
            this.asset = asset;
            this.result = result;
        }
    }

    private static final class AnalysisResult {
        final String direction;
        final int strength;
        final int candles;
        final int rsi;
        final String status;
        final String detail;

        AnalysisResult(String direction, int strength, int candles, int rsi, String status, String detail) {
            this.direction = direction;
            this.strength = strength;
            this.candles = candles;
            this.rsi = rsi;
            this.status = status;
            this.detail = detail;
        }

        static AnalysisResult waiting(String status, int candles, int rsi, String detail) {
            return new AnalysisResult("WAIT", 0, candles, rsi, status, detail);
        }
    }

    private static final class CandleAnalyzer {
        private static AnalysisResult analyze(Bitmap source) {
            int targetWidth = Math.min(720, source.getWidth());
            int targetHeight = Math.max(1, Math.round(source.getHeight() * (targetWidth / (float) source.getWidth())));
            Bitmap bitmap = source.getWidth() == targetWidth
                    ? source
                    : Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
            try {
                List<SignalEngine.CandlePoint> candles = extractCandles(bitmap);
                if (candles.size() < SignalEngine.MIN_CANDLES) {
                    return AnalysisResult.waiting("Chart not ready", candles.size(), 0,
                            "Keep at least 12 red/green candles visible");
                }

                int greenCount = 0;
                int redCount = 0;
                for (SignalEngine.CandlePoint candle : candles) {
                    if (candle.up) greenCount++;
                    else redCount++;
                }
                if (greenCount == 0 || redCount == 0) {
                    return AnalysisResult.waiting("Colour check failed", candles.size(), 0,
                            "Both green and red candles must be visible");
                }

                SignalEngine.Decision decision = SignalEngine.analyze(candles);

                if ("WAIT".equals(decision.direction)) {
                    return AnalysisResult.waiting("No 8-point confluence", candles.size(), decision.rsi,
                            decision.detail + " • filters disagree");
                }
                return new AnalysisResult(decision.direction, decision.strength, candles.size(),
                        decision.rsi, "HIGH CONFIRMATION",
                        decision.detail + " • manual next-candle entry • demo only");
            } finally {
                if (bitmap != source) bitmap.recycle();
            }
        }

        private static List<SignalEngine.CandlePoint> extractCandles(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            // On the mobile Quotex layout the chart is in this band. The old
            // 14%-86% crop also included the large red/green trade buttons,
            // which created a persistent DOWN bias.
            int left = Math.round(width * 0.01f);
            int right = Math.round(width * 0.82f);
            int top = Math.round(height * 0.20f);
            int bottom = Math.round(height * 0.60f);
            int columns = Math.max(1, right - left);
            int[] green = new int[columns];
            int[] red = new int[columns];
            int[] greenMinY = new int[columns];
            int[] greenMaxY = new int[columns];
            int[] redMinY = new int[columns];
            int[] redMaxY = new int[columns];
            java.util.Arrays.fill(greenMinY, Integer.MAX_VALUE);
            java.util.Arrays.fill(redMinY, Integer.MAX_VALUE);
            java.util.Arrays.fill(greenMaxY, -1);
            java.util.Arrays.fill(redMaxY, -1);

            float[] hsv = new float[3];
            for (int x = left; x < right; x += 2) {
                int index = x - left;
                for (int y = top; y < bottom; y += 2) {
                    int color = bitmap.getPixel(x, y);
                    Color.colorToHSV(color, hsv);
                    float hue = hsv[0], saturation = hsv[1], value = hsv[2];
                    boolean isGreen = saturation > 0.30f && value > 0.24f && hue >= 75f && hue <= 190f;
                    boolean isRed = saturation > 0.34f && value > 0.28f && (hue <= 28f || hue >= 332f);
                    if (isGreen) {
                        green[index]++;
                        greenMinY[index] = Math.min(greenMinY[index], y);
                        greenMaxY[index] = Math.max(greenMaxY[index], y);
                    } else if (isRed) {
                        red[index]++;
                        redMinY[index] = Math.min(redMinY[index], y);
                        redMaxY[index] = Math.max(redMaxY[index], y);
                    }
                }
                if (index + 1 < columns) {
                    green[index + 1] = green[index];
                    red[index + 1] = red[index];
                    greenMinY[index + 1] = greenMinY[index];
                    greenMaxY[index + 1] = greenMaxY[index];
                    redMinY[index + 1] = redMinY[index];
                    redMaxY[index + 1] = redMaxY[index];
                }
            }

            List<SignalEngine.CandlePoint> found = new ArrayList<>();
            int start = -1;
            int side = 0;
            for (int i = 0; i <= columns; i++) {
                int nextSide = 0;
                if (i < columns && Math.max(green[i], red[i]) >= 2) nextSide = green[i] >= red[i] ? 1 : -1;
                if (start < 0 && nextSide != 0) {
                    start = i;
                    side = nextSide;
                } else if (start >= 0 && (nextSide == 0 || nextSide != side || i - start > 28)) {
                    addCandle(found, start, i - 1, side, left, green, red,
                            greenMinY, greenMaxY, redMinY, redMaxY);
                    start = nextSide == 0 ? -1 : i;
                    side = nextSide;
                }
            }
            Collections.sort(found, Comparator.comparingDouble(candle -> candle.x));
            return rejectHeightOutliers(found);
        }

        private static void addCandle(List<SignalEngine.CandlePoint> out, int start, int end,
                                      int side, int left,
                                      int[] green, int[] red,
                                      int[] greenMinY, int[] greenMaxY,
                                      int[] redMinY, int[] redMaxY) {
            if (end < start) return;
            int bestCount = -1;
            int top = Integer.MAX_VALUE;
            int bottom = -1;
            for (int i = start; i <= end; i++) {
                int count = side > 0 ? green[i] : red[i];
                if (count > bestCount) bestCount = count;
                int columnTop = side > 0 ? greenMinY[i] : redMinY[i];
                int columnBottom = side > 0 ? greenMaxY[i] : redMaxY[i];
                if (columnTop != Integer.MAX_VALUE) top = Math.min(top, columnTop);
                bottom = Math.max(bottom, columnBottom);
            }
            int candleHeight = bottom - top;
            int candleWidth = end - start + 1;
            if (bestCount < 2 || candleHeight < 6 || candleWidth > 34) return;
            float centerY = (top + bottom) * 0.5f;
            out.add(new SignalEngine.CandlePoint(
                    left + (start + end) * 0.5f, -centerY, candleHeight, side > 0));
        }

        private static List<SignalEngine.CandlePoint> rejectHeightOutliers(
                List<SignalEngine.CandlePoint> points) {
            if (points.size() < 5) return points;
            List<Float> heights = new ArrayList<>();
            for (SignalEngine.CandlePoint point : points) heights.add(point.height);
            Collections.sort(heights);
            float median = heights.get(heights.size() / 2);
            float maximum = Math.max(36f, median * 4.5f);
            List<SignalEngine.CandlePoint> filtered = new ArrayList<>();
            for (SignalEngine.CandlePoint point : points) {
                if (point.height <= maximum) filtered.add(point);
            }
            return filtered;
        }
    }
}
