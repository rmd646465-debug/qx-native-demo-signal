package com.qx.adaptiveedge;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final long SCAN_INTERVAL_MS = 1000L;
    private static final long CHART_WARMUP_MS = 1500L;
    private static final long CHART_REPAIR_DELAY_MS = 650L;
    private static final long STABILITY_FRAME_MS = 450L;
    private static final long MINUTE_MS = SmartScanPolicy.MINUTE_MS;
    private static final long CLOSE_WINDOW_START_MS = SmartScanPolicy.CLOSE_WINDOW_START_MS;
    private static final long AUTO_SCAN_FRESH_END_MS = SmartScanPolicy.FRESH_ENTRY_END_MS;
    private static final long SMART_SCAN_START_DELAY_MS = 2_100L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private TextView signalView;
    private TextView strengthView;
    private TextView detailView;
    private TextView waitView;
    private TextView connectionView;
    private TextView recordView;
    private Button toggleButton;
    private Button assetScanButton;
    private volatile boolean pageReady;
    private volatile boolean scanning = true;
    private volatile boolean analysisBusy;
    private boolean stableSequenceActive;
    private boolean assetScanActive;
    private boolean continuousScanEnabled = true;
    private boolean chartRepairBusy;
    private boolean chartRepairEscalated;
    private final List<String> assetQueue = new ArrayList<>();
    private final List<AssetScanResult> assetScanResults = new ArrayList<>();
    private int assetScanIndex;
    private String currentAssetName = "CURRENT ASSET";
    private int startUrlIndex;
    private String currentLoadUrl = START_URLS[0];
    private String failedMainFrameUrl = "";
    private long assetScanMinute = -1L;
    private final Map<String, Long> analyzedMinuteByAsset = new HashMap<>();
    private final SignalLockBook<AnalysisResult> signalLocks = new SignalLockBook<>();
    private SignalLockBook.Entry<AnalysisResult> lastIssuedSignal;
    private int demoWins;
    private int demoLosses;
    private DailyTargetSession dailySession = new DailyTargetSession();
    private String sessionDay = "";
    private boolean riskStopped;

    private final Runnable smartScanStarter = this::beginAssetScan;

    private final Runnable scanLoop = new Runnable() {
        @Override public void run() {
            ensureCurrentDemoDay();
            if (scanning && pageReady && !analysisBusy && !stableSequenceActive
                    && !assetScanActive && !continuousScanEnabled) {
                refreshCurrentAssetName(MainActivity.this::runLiveCloseCycle);
            }
            mainHandler.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        loadDemoRecord();
        setContentView(buildUi());
        if (riskStopped) toggleButton.setText(sessionButtonLabel());
        updateSmartScanButton();
        updateRecordView();
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

        TextView title = text("QX $5-$7 Target Demo", 16, Color.WHITE, true);
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
        detailView = text("3 STABLE READS • CLOSED-CANDLE ENTRY • DEMO ONLY", 9, Color.rgb(114, 130, 153), false);
        detailView.setGravity(Gravity.CENTER);
        detailView.setMaxLines(2);
        waitView = text("Reading the current currency chart…", 10, Color.rgb(245, 179, 66), true);
        waitView.setGravity(Gravity.CENTER);
        signalPanel.addView(signalView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        signalPanel.addView(strengthView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        signalPanel.addView(detailView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        signalPanel.addView(waitView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
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
            ensureCurrentDemoDay();
            if (riskStopped) {
                scanning = false;
                toggleButton.setText(sessionButtonLabel());
                showDailySessionStop();
                return;
            }
            scanning = !scanning;
            toggleButton.setText(scanning ? "PAUSE ANALYSIS" : "START ANALYSIS");
            if (!scanning) {
                disableSmartScan("Paused by user", "Analysis stopped • Chart remains live");
            } else {
                continuousScanEnabled = true;
                updateSmartScanButton();
                beginAssetScan();
            }
        });
        controls.addView(toggleButton, new LinearLayout.LayoutParams(0, dp(46), 1f));

        assetScanButton = button("SMART SCAN ON", Color.rgb(113, 74, 181));
        assetScanButton.setOnClickListener(v -> {
            ensureCurrentDemoDay();
            if (riskStopped) {
                showDailySessionStop();
            } else if (continuousScanEnabled) {
                disableSmartScan("SMART SCAN OFF",
                        "Current currency will still be checked at each candle close");
            } else {
                continuousScanEnabled = true;
                scanning = true;
                toggleButton.setText("PAUSE ANALYSIS");
                updateSmartScanButton();
                beginAssetScan();
            }
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        scanParams.setMarginStart(dp(6));
        controls.addView(assetScanButton, scanParams);

        Button reload = button("FIX CHART", Color.rgb(33, 97, 156));
        reload.setOnClickListener(v -> {
            chartRepairEscalated = false;
            repairChartView(true);
        });
        reload.setOnLongClickListener(v -> {
            reloadOfficialChart();
            return true;
        });
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        reloadParams.setMarginStart(dp(6));
        controls.addView(reload, reloadParams);
        root.addView(controls);

        LinearLayout resultControls = new LinearLayout(this);
        resultControls.setGravity(Gravity.CENTER);
        resultControls.setPadding(dp(8), dp(0), dp(8), dp(6));
        resultControls.setBackgroundColor(Color.rgb(10, 18, 32));

        Button markWin = button("MARK WIN", Color.rgb(11, 139, 91));
        markWin.setOnClickListener(v -> recordCurrentSignal(true));
        resultControls.addView(markWin, new LinearLayout.LayoutParams(0, dp(38), 1f));

        Button markLoss = button("MARK LOSS", Color.rgb(181, 61, 61));
        markLoss.setOnClickListener(v -> recordCurrentSignal(false));
        LinearLayout.LayoutParams lossParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lossParams.setMarginStart(dp(6));
        resultControls.addView(markLoss, lossParams);

        Button resetLog = button("RESET LOG", Color.rgb(75, 86, 105));
        resetLog.setOnClickListener(v -> resetDemoRecord());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        resetParams.setMarginStart(dp(6));
        resultControls.addView(resetLog, resetParams);
        root.addView(resultControls);

        recordView = text("TODAY $0.00 / +$5.00", 9, Color.rgb(171, 183, 201), true);
        recordView.setGravity(Gravity.CENTER);
        recordView.setMaxLines(2);
        recordView.setPadding(dp(8), dp(2), dp(8), dp(5));
        recordView.setBackgroundColor(Color.rgb(10, 18, 32));
        root.addView(recordView);

        TextView risk = text("$2 DEMO • USE PAYOUT ≥82% • TARGET +$5 • STOP -$4/2 LOSSES • 10 MAX • NO GUARANTEE/AUTO-TRADE", 9, Color.rgb(255, 204, 111), true);
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
        settings.setOffscreenPreRaster(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // Keep Android's normal mobile WebView user-agent. Appending a custom
        // bot-style token can make an otherwise valid Quotex session fail.
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (isOfficialUrl(url)) {
                    pageReady = false;
                    chartRepairBusy = false;
                    chartRepairEscalated = false;
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
        detailView.setText("SMART SCAN ON • 82%+ ASSETS • ONE ACTIVE SIGNAL");
        refreshCurrentAssetName(null);
        mainHandler.postDelayed(() -> repairChartView(false), CHART_REPAIR_DELAY_MS);
        if (continuousScanEnabled && scanning && !riskStopped) {
            mainHandler.removeCallbacks(smartScanStarter);
            mainHandler.postDelayed(smartScanStarter, SMART_SCAN_START_DELAY_MS);
        }
    }

    private void reloadOfficialChart() {
        pageReady = false;
        chartRepairBusy = false;
        chartRepairEscalated = false;
        connectionView.setText("RELOADING");
        connectionView.setTextColor(Color.rgb(245, 179, 66));
        String visibleUrl = webView.getUrl();
        if (isOfficialUrl(visibleUrl)) webView.reload();
        else loadStartUrl(0, true);
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

    private void repairChartView(boolean aggressive) {
        if (webView == null || !pageReady || chartRepairBusy) {
            if (aggressive) showWait("CHART NOT READY", "Wait for LIVE PAGE, then tap FIX CHART");
            return;
        }
        chartRepairBusy = true;
        if (aggressive) chartRepairEscalated = true;
        connectionView.setText("CHART CHECK");
        connectionView.setTextColor(Color.rgb(245, 179, 66));
        if (aggressive) {
            showWait("FIXING CANDLE VIEW",
                    "Opening chart type • long-press FIX CHART only to reload page");
        }
        webView.evaluateJavascript(openCandlestickMenuScript(aggressive), ignored ->
                mainHandler.postDelayed(() -> webView.evaluateJavascript(
                        selectCandlestickScript(), value -> {
                            chartRepairBusy = false;
                            boolean selected = "true".equalsIgnoreCase(value);
                            connectionView.setText(selected ? "CANDLE VIEW" : "LIVE PAGE");
                            connectionView.setTextColor(Color.rgb(80, 230, 169));
                            if (selected) {
                                showWait("CANDLE VIEW READY",
                                        "Candlestick mode selected • keep chart interval at 1 MIN");
                            } else if (aggressive) {
                                showWait("SELECT CANDLES ON CHART",
                                        "Tap chart's … menu • Chart type • Candles");
                            }
                        }), CHART_REPAIR_DELAY_MS));
    }

    private String openCandlestickMenuScript(boolean aggressive) {
        String fallback = aggressive
                ? "let ds=bs.filter(e=>{const t=lab(e),r=e.getBoundingClientRect();"
                + "return r.left<110&&r.top<innerHeight*.58&&r.width<100&&r.height<100"
                + "&&(/^(\\.\\.\\.|\u2026|•••)$/.test(t)||e.querySelectorAll('circle').length>=3);});"
                + "ds.sort((a,b)=>a.getBoundingClientRect().top-b.getBoundingClientRect().top);"
                + "if(ds.length){clk(ds[0]);return 'dots';}"
                : "";
        return "(function(){const vis=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);"
                + "return r.width>5&&r.height>5&&r.bottom>0&&r.top<innerHeight"
                + "&&s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity||1)>.05;};"
                + "const lab=e=>((e.innerText||e.textContent||'')+' '+(e.getAttribute('aria-label')||'')"
                + "+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('data-tooltip')||''))"
                + ".replace(/\\s+/g,' ').trim().toLowerCase();"
                + "const clk=e=>{const t=e.closest('button,[role=button],[role=menuitem],li')||e;"
                + "t.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));};"
                + "for(const e of document.querySelectorAll('body *')){const t=lab(e);"
                + "if(!vis(e)||t.length>100||!(/bonus/.test(t)&&/deposit/.test(t)))continue;"
                + "let n=e;for(let i=0;i<4&&n.parentElement;i++){const p=n.parentElement;"
                + "if(lab(p).length>180)break;n=p;}"
                + "const cs=[...n.querySelectorAll('button,[role=button]')].filter(vis);"
                + "if(cs.length){cs.sort((a,b)=>b.getBoundingClientRect().right-a.getBoundingClientRect().right);clk(cs[0]);}break;}"
                + "document.querySelectorAll('canvas').forEach(c=>{c.style.visibility='visible';c.style.opacity='1';});"
                + "window.dispatchEvent(new Event('resize'));"
                + "let es=[...document.querySelectorAll('button,[role=menuitem],li,div,span')].filter(vis)"
                + ".filter(e=>/^(candles?|candlesticks?|japanese candles?)$/.test(lab(e)));"
                + "es.sort((a,b)=>a.getBoundingClientRect().width*a.getBoundingClientRect().height"
                + "-b.getBoundingClientRect().width*b.getBoundingClientRect().height);"
                + "if(es.length){clk(es[0]);return 'selected';}"
                + "let bs=[...document.querySelectorAll('button,[role=button],[aria-label],[title]')].filter(vis);"
                + "let cs=bs.filter(e=>{const t=lab(e);return !/(deposit|trade|up|down)/.test(t)"
                + "&&(/chart.*(type|style)|(type|style).*chart|candlestick|^line$/.test(t));});"
                + "cs.sort((a,b)=>a.getBoundingClientRect().width*a.getBoundingClientRect().height"
                + "-b.getBoundingClientRect().width*b.getBoundingClientRect().height);"
                + "if(cs.length){clk(cs[0]);return 'menu';}" + fallback
                + "return 'layout';})()";
    }

    private String selectCandlestickScript() {
        return "(function(){const vis=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);"
                + "return r.width>5&&r.height>5&&r.bottom>0&&r.top<innerHeight"
                + "&&s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity||1)>.05;};"
                + "const lab=e=>(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                + "let es=[...document.querySelectorAll('button,[role=button],[role=menuitem],li,div,span')]"
                + ".filter(vis).filter(e=>/^(candles?|candlesticks?|japanese candles?)$/.test(lab(e)));"
                + "es.sort((a,b)=>a.getBoundingClientRect().width*a.getBoundingClientRect().height"
                + "-b.getBoundingClientRect().width*b.getBoundingClientRect().height);"
                + "if(es.length){const t=es[0].closest('button,[role=button],[role=menuitem],li')||es[0];"
                + "t.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));"
                + "setTimeout(()=>window.dispatchEvent(new Event('resize')),100);return true;}"
                + "window.dispatchEvent(new Event('resize'));return false;})()";
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
                + "const pm=t.match(/(\\d{2,3})\\s*%/),p=pm?parseInt(pm[1],10):0;"
                + "const name=pair+(t.includes('OTC')?' (OTC)':'');const old=map.get(pair);"
                + "if(!old||p>old.payout||(name.includes('OTC')&&!old.name.includes('OTC')))map.set(pair,{name:name,payout:p});}"
                + "return Array.from(map.values()).filter(x=>x.payout===0||x.payout>="
                + SmartScanPolicy.MIN_PAYOUT_PERCENT + ")"
                + ".sort((a,b)=>b.payout-a.payout).map(x=>x.name).slice(0,4);})()";
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

    private void runLiveCloseCycle() {
        ensureCurrentDemoDay();
        long now = System.currentTimeMillis();
        SignalLockBook.Entry<AnalysisResult> active = activeSignal(currentAssetName, now);
        if (active != null) {
            renderLockedSignal(active, now);
            return;
        }
        if (riskStopped) {
            showDailySessionStop();
            return;
        }
        if (!isCloseWindow(now)) {
            showWait("WAIT FOR NEXT CLOSED CANDLE",
                    "A signal is checked only just after a 1-minute candle closes");
            return;
        }

        String assetKey = assetKey(currentAssetName);
        long minuteKey = now / MINUTE_MS;
        Long analyzedMinute = analyzedMinuteByAsset.get(assetKey);
        if (analyzedMinute != null && analyzedMinute == minuteKey) return;

        captureStableAnalysis(result -> {
            if (!"UNSTABLE CHART READ".equals(result.status)) {
                analyzedMinuteByAsset.put(assetKey, minuteKey);
            }
            acceptStableResult(result);
        });
    }

    private boolean isCloseWindow(long now) {
        return SmartScanPolicy.isCloseWindow(now);
    }

    private long delayToCloseWindow(long now) {
        return SmartScanPolicy.delayToCloseWindow(now);
    }

    private boolean isAutoScanFresh(long now) {
        return assetScanMinute >= 0 && now / MINUTE_MS == assetScanMinute
                && now % MINUTE_MS <= AUTO_SCAN_FRESH_END_MS;
    }

    private void beginAssetScan() {
        ensureCurrentDemoDay();
        mainHandler.removeCallbacks(smartScanStarter);
        if (!continuousScanEnabled || !scanning || assetScanActive) return;
        if (!pageReady || webView == null) {
            showWait("Live demo not ready", "SMART SCAN starts when LIVE PAGE is ready");
            return;
        }
        if (riskStopped) {
            showDailySessionStop();
            return;
        }
        if (lastIssuedSignal != null && !lastIssuedSignal.rated) {
            long now = System.currentTimeMillis();
            if (now < lastIssuedSignal.expiresAt) {
                renderLockedSignal(lastIssuedSignal, now);
            } else {
                showWait("MARK PREVIOUS RESULT",
                        lastIssuedSignal.displayAsset
                                + " expired • tap MARK WIN or MARK LOSS to resume SMART SCAN");
            }
            return;
        }
        assetScanActive = true;
        assetQueue.clear();
        assetScanResults.clear();
        assetScanIndex = 0;
        updateSmartScanButton();
        armAssetScanAtClose();
    }

    private void armAssetScanAtClose() {
        if (!assetScanActive) return;
        long now = System.currentTimeMillis();
        if (!isCloseWindow(now)) {
            showWait("SMART SCAN ARMED",
                    "Automatic search starts just after the next 1-minute candle closes");
            mainHandler.postDelayed(this::armAssetScanAtClose,
                    Math.max(100L, delayToCloseWindow(now)));
            return;
        }
        assetScanMinute = now / MINUTE_MS;
        showWait("Opening currency list", "Up to 4 payout ≥82% charts • 3 reads per chart");
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
        if (!isAutoScanFresh(System.currentTimeMillis())) {
            if (assetScanResults.isEmpty()) {
                stopAssetScan("ENTRY WINDOW MISSED",
                        "No late signal was forced • SMART SCAN will retry next candle");
            } else {
                finishAssetScan();
            }
            return;
        }
        if (assetScanIndex >= assetQueue.size()) {
            finishAssetScan();
            return;
        }
        String asset = assetQueue.get(assetScanIndex);
        showWait("Scanning " + (assetScanIndex + 1) + "/" + assetQueue.size(),
                asset + " • verifying three stable chart reads");
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
        captureStableAnalysis(result -> {
            if (!assetScanActive) return;
            AnalysisResult candidate = result;
            if (!"WAIT".equals(candidate.direction)
                    && !isAutoScanFresh(System.currentTimeMillis())) {
                candidate = AnalysisResult.waiting("ENTRY WINDOW MISSED",
                        candidate.candles, candidate.flowBias, "CLOSE GATE", 0,
                        "The verified setup arrived too late • no signal issued");
            }
            assetScanResults.add(new AssetScanResult(asset, candidate));
            if (!"WAIT".equals(candidate.direction) && candidate.strength >= 80) {
                assetScanActive = false;
                assetScanMinute = -1L;
                updateSmartScanButton();
                currentAssetName = asset;
                acceptStableResult(candidate);
                openThenSelectAsset(asset, ignored -> { });
                return;
            }
            assetScanIndex++;
            scanNextAsset();
        });
    }

    private void finishAssetScan() {
        AssetScanResult best = null;
        AssetScanResult bestWait = null;
        for (AssetScanResult candidate : assetScanResults) {
            if ("WAIT".equals(candidate.result.direction)) {
                if (bestWait == null
                        || candidate.result.readiness > bestWait.result.readiness) {
                    bestWait = candidate;
                }
            } else if (best == null || candidate.result.strength > best.result.strength) {
                best = candidate;
            }
        }
        assetScanActive = false;
        updateSmartScanButton();
        boolean fresh = isAutoScanFresh(System.currentTimeMillis());
        assetScanMinute = -1L;
        if (best == null) {
            if (bestWait != null) {
                currentAssetName = bestWait.asset;
                renderResult(bestWait.result);
                openThenSelectAsset(bestWait.asset, ignored -> { });
            } else {
                currentAssetName = "SCANNED " + assetScanResults.size() + " ASSETS";
                renderResult(AnalysisResult.waiting("No eligible asset", 0, 0,
                        "SCAN COMPLETE", 0,
                        "All scanned charts remained WAIT • no forced signal"));
            }
            scheduleNextSmartScan();
            return;
        }
        AssetScanResult winner = best;
        currentAssetName = winner.asset;
        if (fresh) {
            acceptStableResult(winner.result);
        } else {
            renderResult(AnalysisResult.waiting("ENTRY WINDOW MISSED",
                    winner.result.candles, winner.result.flowBias, "CLOSE GATE", 0,
                    "Scan finished too late • no directional signal issued"));
            scheduleNextSmartScan();
        }
        openThenSelectAsset(winner.asset, ignored -> { });
    }

    private void stopAssetScan(String status, String detail) {
        assetScanActive = false;
        assetQueue.clear();
        assetScanResults.clear();
        assetScanMinute = -1L;
        updateSmartScanButton();
        showWait(status, detail);
        scheduleNextSmartScan();
    }

    private void scheduleNextSmartScan() {
        if (!continuousScanEnabled || !scanning || riskStopped || !pageReady) return;
        mainHandler.removeCallbacks(smartScanStarter);
        long now = System.currentTimeMillis();
        long delay = SmartScanPolicy.delayToNextClose(now);
        mainHandler.postDelayed(smartScanStarter, Math.max(500L, delay));
        if (waitView != null) {
            waitView.setText("SMART SCAN ON • NEXT CANDLE AUTO-CHECK");
            waitView.setTextColor(Color.rgb(245, 179, 66));
        }
    }

    private void holdSmartScanForSignal(SignalLockBook.Entry<AnalysisResult> signal) {
        lastIssuedSignal = signal;
        mainHandler.removeCallbacks(smartScanStarter);
        long delay = Math.max(250L,
                signal.expiresAt - System.currentTimeMillis() + 250L);
        mainHandler.postDelayed(() -> {
            if (!continuousScanEnabled || riskStopped || signal.rated) return;
            showWait("MARK PREVIOUS RESULT",
                    signal.displayAsset
                            + " expired • tap MARK WIN or MARK LOSS to resume SMART SCAN");
        }, delay);
    }

    private void disableSmartScan(String status, String detail) {
        continuousScanEnabled = false;
        assetScanActive = false;
        assetScanMinute = -1L;
        assetQueue.clear();
        assetScanResults.clear();
        mainHandler.removeCallbacks(smartScanStarter);
        updateSmartScanButton();
        showWait(status, detail);
    }

    private void updateSmartScanButton() {
        if (assetScanButton == null) return;
        if (riskStopped) assetScanButton.setText("DAILY STOP");
        else assetScanButton.setText(continuousScanEnabled
                ? "SMART SCAN ON" : "SMART SCAN OFF");
    }

    private void captureStableAnalysis(Consumer<AnalysisResult> receiver) {
        if (stableSequenceActive) return;
        stableSequenceActive = true;
        captureStableRead(new ArrayList<>(), receiver);
    }

    private void captureStableRead(List<AnalysisResult> reads,
                                   Consumer<AnalysisResult> receiver) {
        captureAndAnalyze(result -> {
            reads.add(result);
            if (reads.size() < SignalStability.REQUIRED_READS) {
                mainHandler.postDelayed(() -> captureStableRead(reads, receiver),
                        STABILITY_FRAME_MS);
                return;
            }
            stableSequenceActive = false;
            receiver.accept(stableConsensus(reads));
        });
    }

    private AnalysisResult stableConsensus(List<AnalysisResult> reads) {
        AnalysisResult first = reads.get(0);
        List<SignalStability.Read> signatures = new ArrayList<>();
        for (AnalysisResult read : reads) {
            signatures.add(new SignalStability.Read(read.candles, read.direction,
                    read.profile, read.expiryMinutes, read.strength, read.flowBias));
        }
        boolean stable = SignalStability.agrees(signatures);
        if (!stable) {
            StringBuilder trace = new StringBuilder("Reads ");
            for (int i = 0; i < reads.size(); i++) {
                if (i > 0) trace.append(" / ");
                AnalysisResult read = reads.get(i);
                trace.append(read.candles).append('-').append(read.direction);
                if (read.expiryMinutes > 0) trace.append('-').append(read.expiryMinutes).append('m');
            }
            return AnalysisResult.waiting("UNSTABLE CHART READ", first.candles, 0,
                    "STABILITY FILTER", 0, trace + " • no signal issued");
        }

        AnalysisResult conservative = first;
        for (AnalysisResult read : reads) {
            if (!"WAIT".equals(read.direction)
                    && read.strength < conservative.strength) conservative = read;
        }
        return conservative;
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
        else renderResult(result);
    }

    private void acceptStableResult(AnalysisResult result) {
        ensureCurrentDemoDay();
        if (riskStopped) {
            showDailySessionStop();
            return;
        }
        if ("WAIT".equals(result.direction)) {
            if ("CANDLE CHART REQUIRED".equals(result.status)
                    && !chartRepairEscalated && !chartRepairBusy) {
                repairChartView(true);
                return;
            }
            renderResult(result);
            return;
        }
        long now = System.currentTimeMillis();
        if (lastIssuedSignal != null && !lastIssuedSignal.rated) {
            if (now < lastIssuedSignal.expiresAt) {
                renderLockedSignal(lastIssuedSignal, now);
            } else {
                showWait("MARK PREVIOUS RESULT",
                        lastIssuedSignal.displayAsset
                                + " expired • mark its result before a new signal");
            }
            return;
        }
        SignalLockBook.Entry<AnalysisResult> active = activeSignal(currentAssetName, now);
        if (active == null) {
            String key = assetKey(currentAssetName);
            active = signalLocks.issueOrKeep(key, currentAssetName, result, now,
                    now + result.expiryMinutes * MINUTE_MS);
        }
        holdSmartScanForSignal(active);
        renderLockedSignal(active, now);
    }

    private SignalLockBook.Entry<AnalysisResult> activeSignal(String asset, long now) {
        return signalLocks.active(assetKey(asset), now);
    }

    private String assetKey(String asset) {
        return asset == null ? "CURRENT ASSET"
                : asset.trim().toUpperCase(Locale.US);
    }

    private void renderLockedSignal(SignalLockBook.Entry<AnalysisResult> signal,
                                    long now) {
        renderResult(signal.payload);
        detailView.setText("WHY " + signal.payload.direction + ": "
                + setupReason(signal.payload) + " • UP "
                + signal.payload.bullishPoints + " vs DOWN "
                + signal.payload.bearishPoints + "\n3/3 STABLE • "
                + signal.payload.candles + " CLOSED CANDLES");
        waitView.setText("SIGNAL LOCKED FOR ACTIVE EXPIRY • NO REVERSAL");
        waitView.setTextColor("UP".equals(signal.payload.direction)
                ? Color.rgb(22, 199, 132) : Color.rgb(239, 83, 80));
    }

    private void renderResult(AnalysisResult result) {
        int color;
        String symbol;
        if ("UP".equals(result.direction)) {
            color = Color.rgb(22, 199, 132);
            symbol = "↑ UP • " + result.expiryMinutes + " MIN";
        } else if ("DOWN".equals(result.direction)) {
            color = Color.rgb(239, 83, 80);
            symbol = "↓ DOWN • " + result.expiryMinutes + " MIN";
        } else {
            color = Color.rgb(245, 179, 66);
            symbol = "WAIT";
        }
        signalView.setText(symbol);
        signalView.setTextColor(color);
        if (result.strength > 0) {
            strengthView.setText(currentAssetName + " • " + result.profile
                    + " • SETUP " + result.strength + "/100");
            detailView.setText("Detected " + result.candles + " closed candles • FLOW "
                    + signed(result.flowBias) + " • manual demo only");
            waitView.setText("VERIFIED AT CANDLE CLOSE • NO AUTO-TRADE");
            waitView.setTextColor(color);
        } else {
            strengthView.setText(currentAssetName + " • " + result.profile
                    + (result.readiness > 0 ? " • READY " + result.readiness + "/100" : ""));
            if ("WAIT FOR NEXT CLOSED CANDLE".equals(result.status)) {
                strengthView.setText(currentAssetName + " • NEXT CLOSED-CANDLE CHECK");
                detailView.setText("Live demo page is open • candle detection has not run yet");
            } else if ("CANDLE CHART REQUIRED".equals(result.status)) {
                strengthView.setText(currentAssetName + " • CANDLE VIEW REQUIRED");
                detailView.setText("Price page loaded, but red/green candlesticks are not visible");
            } else if (result.candles > 0) {
                detailView.setText("Detected " + result.candles + " candles • FLOW "
                        + signed(result.flowBias) + " • " + result.detail);
            } else {
                detailView.setText(result.detail);
            }
            waitView.setTextColor(Color.rgb(245, 179, 66));
            if ("WAIT FOR NEXT CLOSED CANDLE".equals(result.status)) {
                waitView.setText("CLOSE GATE ACTIVE • NO MID-CANDLE REVERSAL");
            } else if ("CANDLE CHART REQUIRED".equals(result.status)) {
                waitView.setText("TAP FIX CHART • CHOOSE CANDLES IF MENU OPENS");
            } else if ("UNSTABLE CHART READ".equals(result.status)) {
                waitView.setText("3 READS DISAGREED • NO SIGNAL");
            } else if ("DAILY SESSION STOP".equals(result.status)
                    || "DEMO TARGET REACHED".equals(result.status)
                    || "DAILY STOP LOCKED".equals(result.status)) {
                waitView.setText("DAILY HARD STOP • NO MORE SIGNALS TODAY");
            } else if ("MARK PREVIOUS RESULT".equals(result.status)) {
                waitView.setText("MARK WIN/LOSS TO CONTINUE SMART SCAN");
            } else if ("SMART SCAN ARMED".equals(result.status)) {
                waitView.setText("NEXT 1-MIN CANDLE CLOSE • AUTOMATIC SEARCH");
            } else if ("SMART SCAN OFF".equals(result.status)
                    || "Paused by user".equals(result.status)) {
                waitView.setText("NO AUTOMATIC SEARCH WHILE PAUSED/OFF");
            } else {
                waitView.setText("NO VERIFIED EDGE • WAIT OR TRY ANOTHER CURRENCY");
            }
        }
    }

    private String setupReason(AnalysisResult result) {
        if (SignalEngine.SWEEP_PROFILE.equals(result.profile)) {
            return "UP".equals(result.direction)
                    ? "LOCAL LOW SWEEP + BULLISH REJECTION CLOSE"
                    : "LOCAL HIGH SWEEP + BEARISH REJECTION CLOSE";
        }
        if (SignalEngine.RETEST_PROFILE.equals(result.profile)) {
            return "UP".equals(result.direction)
                    ? "RESISTANCE BREAK + RETEST HELD"
                    : "SUPPORT BREAK + RETEST HELD";
        }
        if (SignalEngine.PULLBACK_PROFILE.equals(result.profile)) {
            return "UP".equals(result.direction)
                    ? "UP LEG + CONTROLLED PULLBACK + RESUMPTION"
                    : "DOWN LEG + CONTROLLED PULLBACK + RESUMPTION";
        }
        return "COMPLETE CLOSED-CANDLE CONTEXT";
    }

    private void loadDemoRecord() {
        android.content.SharedPreferences prefs = getSharedPreferences(
                "stable_close_demo_record", MODE_PRIVATE);
        demoWins = prefs.getInt("wins", 0);
        demoLosses = prefs.getInt("losses", 0);
        sessionDay = prefs.getString("daily_day", "");
        String today = currentDayKey();
        if (today.equals(sessionDay)) {
            dailySession = new DailyTargetSession(
                    prefs.getInt("daily_wins", 0),
                    prefs.getInt("daily_losses", 0),
                    prefs.getInt("daily_consecutive_losses", 0));
        } else {
            sessionDay = today;
            dailySession = new DailyTargetSession();
        }
        riskStopped = dailySession.isStopped();
        if (riskStopped) scanning = false;
        saveDemoRecord();
    }

    private void saveDemoRecord() {
        getSharedPreferences("stable_close_demo_record", MODE_PRIVATE).edit()
                .putInt("wins", demoWins)
                .putInt("losses", demoLosses)
                .putString("daily_day", sessionDay)
                .putInt("daily_wins", dailySession.wins())
                .putInt("daily_losses", dailySession.losses())
                .putInt("daily_consecutive_losses",
                        dailySession.consecutiveLosses())
                .apply();
    }

    private void updateRecordView() {
        if (recordView == null) return;
        int total = demoWins + demoLosses;
        String rate = total == 0 ? "NO SAMPLE"
                : Math.round(demoWins * 100f / total) + "%";
        int auditCents = demoWins * DailyTargetSession.WIN_PROFIT_CENTS
                - demoLosses * DailyTargetSession.STAKE_CENTS;
        String validation = total >= 500 ? "500+ SAMPLE" : "SAMPLE " + total + "/500";
        recordView.setText("TODAY "
                + DailyTargetSession.formatMoney(dailySession.profitCents())
                + " / +$5.00 • " + dailySession.trades() + "/10 TRADES • $2 @82%\n"
                + "AUDIT " + demoWins + "W-" + demoLosses + "L • " + rate
                + " • " + DailyTargetSession.formatMoney(auditCents)
                + " • " + validation);
        int color = Color.rgb(171, 183, 201);
        if (dailySession.stopReason() == DailyTargetSession.StopReason.TARGET_REACHED) {
            color = Color.rgb(80, 230, 169);
        } else if (riskStopped) {
            color = Color.rgb(239, 83, 80);
        }
        recordView.setTextColor(color);
    }

    private void recordCurrentSignal(boolean win) {
        ensureCurrentDemoDay();
        if (riskStopped) {
            showDailySessionStop();
            return;
        }
        SignalLockBook.Entry<AnalysisResult> signal = lastIssuedSignal != null
                && !lastIssuedSignal.rated ? lastIssuedSignal
                : signalLocks.latest(assetKey(currentAssetName));
        if (signal == null) {
            showWait("NO SIGNAL TO MARK", "A verified locked signal is required first");
            return;
        }
        if (System.currentTimeMillis() < signal.expiresAt) {
            renderLockedSignal(signal, System.currentTimeMillis());
            waitView.setText("MARK RESULT ONLY AFTER THE ACTIVE EXPIRY ENDS");
            return;
        }
        if (signal.rated) {
            showWait("RESULT ALREADY RECORDED",
                    signal.displayAsset + " can be marked only once");
            return;
        }
        signal.rated = true;
        if (win) {
            demoWins++;
        } else {
            demoLosses++;
        }
        dailySession.record(win);
        riskStopped = dailySession.isStopped();
        saveDemoRecord();
        updateRecordView();
        if (riskStopped) {
            scanning = false;
            continuousScanEnabled = false;
            mainHandler.removeCallbacks(smartScanStarter);
            toggleButton.setText(sessionButtonLabel());
            if (assetScanActive) stopAssetScan("DAILY SESSION STOP",
                    dailySession.stopDetail());
            updateSmartScanButton();
            showDailySessionStop();
        } else {
            showWait(win ? "WIN RECORDED" : "LOSS RECORDED",
                    (win ? "Demo result saved" : "No stake increase or recovery trade")
                            + " • today "
                            + DailyTargetSession.formatMoney(dailySession.profitCents())
                            + " • " + dailySession.trades() + "/10 trades");
            scheduleNextSmartScan();
        }
    }

    private void resetDemoRecord() {
        ensureCurrentDemoDay();
        if (riskStopped) {
            showWait("DAILY STOP LOCKED",
                    dailySession.stopDetail()
                            + " • a fresh session opens next local day");
            return;
        }
        assetScanActive = false;
        assetScanMinute = -1L;
        assetQueue.clear();
        assetScanResults.clear();
        mainHandler.removeCallbacks(smartScanStarter);
        demoWins = 0;
        demoLosses = 0;
        dailySession = new DailyTargetSession();
        sessionDay = currentDayKey();
        riskStopped = false;
        scanning = true;
        continuousScanEnabled = true;
        lastIssuedSignal = null;
        signalLocks.clear();
        analyzedMinuteByAsset.clear();
        saveDemoRecord();
        updateRecordView();
        toggleButton.setText("PAUSE ANALYSIS");
        updateSmartScanButton();
        showWait("DEMO LOG RESET",
                "$2 @82% session restarted • target +$5 • stop -$4 / two losses");
        if (pageReady) mainHandler.postDelayed(smartScanStarter, 700L);
    }

    private void ensureCurrentDemoDay() {
        String today = currentDayKey();
        if (today.equals(sessionDay)) return;
        boolean wasStopped = riskStopped;
        sessionDay = today;
        dailySession = new DailyTargetSession();
        riskStopped = false;
        lastIssuedSignal = null;
        signalLocks.clear();
        analyzedMinuteByAsset.clear();
        if (wasStopped) {
            scanning = true;
            continuousScanEnabled = true;
        }
        saveDemoRecord();
        updateRecordView();
        if (toggleButton != null) {
            toggleButton.setText(scanning ? "PAUSE ANALYSIS" : "START ANALYSIS");
        }
        updateSmartScanButton();
        if (pageReady && continuousScanEnabled && scanning) {
            mainHandler.postDelayed(smartScanStarter, 700L);
        }
    }

    private String currentDayKey() {
        return java.time.LocalDate.now().toString();
    }

    private String sessionButtonLabel() {
        return dailySession.stopReason() == DailyTargetSession.StopReason.TARGET_REACHED
                ? "TARGET COMPLETE" : "DAILY STOP";
    }

    private void showDailySessionStop() {
        updateSmartScanButton();
        String status = dailySession.stopReason()
                == DailyTargetSession.StopReason.TARGET_REACHED
                ? "DEMO TARGET REACHED" : "DAILY SESSION STOP";
        showWait(status, dailySession.stopDetail()
                + " • resumes automatically next local day");
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
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
        mainHandler.removeCallbacks(smartScanStarter);
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
        final int flowBias;
        final int expiryMinutes;
        final String status;
        final String profile;
        final int readiness;
        final int bullishPoints;
        final int bearishPoints;
        final String detail;

        AnalysisResult(String direction, int strength, int candles, int flowBias,
                       int expiryMinutes, String status, String profile,
                       int readiness, int bullishPoints, int bearishPoints,
                       String detail) {
            this.direction = direction;
            this.strength = strength;
            this.candles = candles;
            this.flowBias = flowBias;
            this.expiryMinutes = expiryMinutes;
            this.status = status;
            this.profile = profile;
            this.readiness = readiness;
            this.bullishPoints = bullishPoints;
            this.bearishPoints = bearishPoints;
            this.detail = detail;
        }

        static AnalysisResult waiting(String status, int candles, int flowBias, String detail) {
            return waiting(status, candles, flowBias, "WAIT", 0, detail);
        }

        static AnalysisResult waiting(String status, int candles, int flowBias,
                                      String profile, int readiness, String detail) {
            return new AnalysisResult("WAIT", 0, candles, flowBias, 0, status,
                    profile, readiness, 0, 0, detail);
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
                List<SignalEngine.CandlePoint> detected = extractCandles(bitmap);
                if (detected.isEmpty()) {
                    return AnalysisResult.waiting("CANDLE CHART REQUIRED", 0, 0,
                            "Price line may be selected • switch chart type to Candles");
                }
                if (detected.size() < SignalEngine.MIN_CANDLES + 1) {
                    return AnalysisResult.waiting("NEED MORE CANDLES", detected.size(), 0,
                            "Zoom out slightly and keep at least 31 red/green candles visible");
                }

                // The rightmost candle is normally still forming. Excluding it
                // prevents repainting from changing a signal after entry.
                List<SignalEngine.CandlePoint> candles = new ArrayList<>(
                        detected.subList(0, detected.size() - 1));

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
                    return AnalysisResult.waiting("No edge now", candles.size(), decision.flowBias,
                            decision.profile, decision.readiness,
                            decision.detail);
                }
                return new AnalysisResult(decision.direction, decision.strength, candles.size(),
                        decision.flowBias, decision.expiryMinutes, "ELIGIBLE SETUP",
                        decision.profile, 100, decision.bullishPoints,
                        decision.bearishPoints,
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
            List<Integer> bodyTops = new ArrayList<>();
            List<Integer> bodyBottoms = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                int count = side > 0 ? green[i] : red[i];
                if (count > bestCount) bestCount = count;
                int columnTop = side > 0 ? greenMinY[i] : redMinY[i];
                int columnBottom = side > 0 ? greenMaxY[i] : redMaxY[i];
                if (columnTop != Integer.MAX_VALUE) {
                    top = Math.min(top, columnTop);
                    bodyTops.add(columnTop);
                }
                if (columnBottom >= 0) bodyBottoms.add(columnBottom);
                bottom = Math.max(bottom, columnBottom);
            }
            int candleHeight = bottom - top;
            int candleWidth = end - start + 1;
            if (bestCount < 2 || candleHeight < 6 || candleWidth > 34
                    || bodyTops.isEmpty() || bodyBottoms.isEmpty()) return;
            Collections.sort(bodyTops);
            Collections.sort(bodyBottoms);
            int bodyTop = bodyTops.get(bodyTops.size() / 2);
            int bodyBottom = bodyBottoms.get(bodyBottoms.size() / 2);
            float open = side > 0 ? -bodyBottom : -bodyTop;
            float close = side > 0 ? -bodyTop : -bodyBottom;
            out.add(new SignalEngine.CandlePoint(
                    left + (start + end) * 0.5f,
                    open, -top, -bottom, close, side > 0));
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
