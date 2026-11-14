package ir.lnkee.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://lnkee.ir/";
    private static final String NOTIFICATION_CHANNEL_ID = "lnkee_updates";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    private WebView webView;
    private ProgressBar loading;
    private View statusScreen;
    private TextView statusTitle;
    private TextView statusMessage;
    private TextView retryButton;
    private ConnectivityManager connectivityManager;
    private boolean pageLoaded;

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            runOnUiThread(() -> {
                if (!pageLoaded) {
                    loadHome();
                }
            });
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Lnkee);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View rootLayout = findViewById(R.id.root_layout);
        rootLayout.setOnApplyWindowInsetsListener((view, insets) -> {
            // Android 15+ places content behind system bars by default. Keep the
            // website entirely inside the safe area on every Android version.
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        loading = findViewById(R.id.loading);
        webView = findViewById(R.id.web_view);
        statusScreen = findViewById(R.id.status_screen);
        statusTitle = findViewById(R.id.status_title);
        statusMessage = findViewById(R.id.status_message);
        retryButton = findViewById(R.id.retry_button);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        retryButton.setOnClickListener(view -> loadHome());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setSupportMultipleWindows(false);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);
        String userAgent = webView.getSettings().getUserAgentString();
        if (!userAgent.contains("LnkeeApp")) {
            webView.getSettings().setUserAgentString(userAgent + " LnkeeApp/1.0");
        }
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(android.graphics.Color.WHITE);
        webView.addJavascriptInterface(new NotificationBridge(), "LnkeeAndroid");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                loading.setProgress(progress);
                loading.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            }
        });
        webView.setWebViewClient(new AppWebViewClient());
        setUpNotifications();

        if (savedInstanceState == null) {
            loadHome();
        } else {
            webView.restoreState(savedInstanceState);
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private void loadHome() {
        pageLoaded = false;
        if (!hasInternet()) {
            showStatus(R.string.offline_title, R.string.offline_message, true);
            return;
        }
        showStatus(R.string.connecting_title, R.string.connecting_message, false);
        webView.loadUrl(HOME_URL);
    }

    private boolean hasInternet() {
        if (connectivityManager == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void showStatus(int titleRes, int messageRes, boolean showRetry) {
        statusTitle.setText(titleRes);
        statusMessage.setText(messageRes);
        retryButton.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        statusScreen.setVisibility(View.VISIBLE);
        loading.setVisibility(View.GONE);
    }

    private void showWebContent() {
        pageLoaded = true;
        statusScreen.setVisibility(View.GONE);
    }

    private void setUpNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private boolean isLnkeeUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return host != null && (host.equals("lnkee.ir") || host.endsWith(".lnkee.ir"));
    }

    private void showNativeNotification(String title, String message) {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title == null || title.trim().isEmpty() ? getString(R.string.app_name) : title)
                .setContentText(message == null ? "" : message)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        getSystemService(NotificationManager.class)
                .notify((int) (System.currentTimeMillis() & 0x0fffffff), builder.build());
    }

    private final class NotificationBridge {
        @JavascriptInterface
        public void showNotification(String title, String message) {
            webView.post(() -> {
                if (isLnkeeUrl(webView.getUrl())) {
                    showNativeNotification(title, message);
                }
            });
        }
    }

    private final class AppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return openUrl(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return openUrl(Uri.parse(url));
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            loading.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (url.startsWith("http")) {
                showWebContent();
                installNotificationBridge(view, url);
                applyAppLikeWebBehavior(view, url);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    android.webkit.WebResourceError error) {
            if (request.isForMainFrame()) {
                view.stopLoading();
                view.loadUrl("about:blank");
                showStatus(R.string.server_error_title, R.string.server_error_message, true);
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceResponse errorResponse) {
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                view.stopLoading();
                view.loadUrl("about:blank");
                showStatus(R.string.server_error_title, R.string.server_error_message, true);
            }
        }

        private boolean openUrl(Uri uri) {
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && isLnkeeUrl(uri.toString())) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(MainActivity.this, R.string.no_handler, Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        private void installNotificationBridge(WebView view, String url) {
            if (!isLnkeeUrl(url)) return;
            String script = "(function(){"
                    + "if(window.__lnkeeNativeNotifications)return;"
                    + "window.__lnkeeNativeNotifications=true;"
                    + "function LnkeeNotification(title,options){"
                    + "var body=options&&options.body?String(options.body):'';"
                    + "window.LnkeeAndroid.showNotification(String(title),body);"
                    + "return {close:function(){}};}"
                    + "LnkeeNotification.permission='granted';"
                    + "LnkeeNotification.requestPermission=function(){return Promise.resolve('granted');};"
                    + "window.Notification=LnkeeNotification;"
                    + "})();";
            view.evaluateJavascript(script, null);
        }

        private void applyAppLikeWebBehavior(WebView view, String url) {
            if (!isLnkeeUrl(url)) return;
            String script = "(function(){"
                    + "document.querySelectorAll('a[target=\\\"_blank\\\"]').forEach(function(a){a.target='_self';});"
                    + "document.documentElement.style.webkitTapHighlightColor='transparent';"
                    + "})();";
            view.evaluateJavascript(script, null);
        }
    }
}
