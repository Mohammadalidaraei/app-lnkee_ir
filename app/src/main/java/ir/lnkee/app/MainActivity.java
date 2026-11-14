package ir.lnkee.app;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://lnkee.ir/";
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(MainActivity.this, R.string.no_handler, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }
}
