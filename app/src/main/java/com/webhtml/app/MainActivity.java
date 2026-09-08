package com.webhtml.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;

    @SuppressLint({"SetJavaScriptEnabled", "QueryPermissionsNeeded"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        );
        super.onCreate(savedInstanceState);
        
        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.parseColor("#070707"));
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // DownloadListener with native filename dialog and robust downloading support
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            String rawSuggestedName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype);
            if (rawSuggestedName == null || rawSuggestedName.isEmpty() || rawSuggestedName.equals("downloadfile.bin")) {
                rawSuggestedName = "file.txt";
            }
            final String suggestedFileName = rawSuggestedName;

            runOnUiThread(() -> {
                android.text.InputFilter[] filters = new android.text.InputFilter[1];
                filters[0] = new android.text.InputFilter.LengthFilter(100);

                final EditText input = new EditText(MainActivity.this);
                input.setText(suggestedFileName);
                input.setSelection(suggestedFileName.length());
                input.setTextColor(android.graphics.Color.WHITE);
                input.setHintTextColor(android.graphics.Color.GRAY);
                input.setFilters(filters);

                int padding = (int) (20 * getResources().getDisplayMetrics().density);
                FrameLayout container = new FrameLayout(MainActivity.this);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.leftMargin = padding;
                params.rightMargin = padding;
                input.setLayoutParams(params);
                container.addView(input);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Save File");
                builder.setMessage("Enter File Name:");
                builder.setView(container);

                builder.setPositiveButton("Save", (dialog, which) -> {
                    String fileName = input.getText().toString().trim();
                    if (fileName.isEmpty()) {
                        fileName = suggestedFileName;
                    }

                    // If it's a blob URL, we extract it using a direct synchronous data URI approach or fallback
                    if (url.startsWith("blob:")) {
                        Toast.makeText(getApplicationContext(), "Saving blob files is handled by your HTML app directly.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    try {
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                        request.setMimeType(mimetype);
                        request.setTitle(fileName);
                        request.setDescription("Downloading file...");
                        request.allowScanningByMediaScanner();
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                        } else {
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                        }
                        
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        if (dm != null) {
                            dm.enqueue(request);
                            Toast.makeText(getApplicationContext(), "Download started...", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                builder.show();
            });
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlStr = request.getUrl().toString();
                
                if (!urlStr.contains("#")) {
                    return super.shouldInterceptRequest(view, request);
                }

                try {
                    String[] mainParts = urlStr.split("#", 2);
                    String cleanUrlStr = mainParts[0];
                    String fragment = mainParts[1];

                    boolean isHtmlMode = fragment.contains("html");
                    boolean isApiMode = fragment.contains("api");

                    String defaultUa;
                    if (isHtmlMode) {
                        defaultUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
                    } else if (isApiMode) {
                        defaultUa = "LFM_MusicApp/1.0 (contact: moj@gmail.com)";
                    } else {
                        defaultUa = webView.getSettings().getUserAgentString();
                    }

                    String finalUa = defaultUa;
                    if (fragment.contains("ua=")) {
                        try {
                            String[] uaParts = fragment.split("ua=");
                            if (uaParts.length > 1) {
                                String customUa = URLDecoder.decode(uaParts[1].split("&")[0], "UTF-8");
                                if (!customUa.isEmpty() && customUa.length() < 300) {
                                    finalUa = customUa;
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    URL url = new URL(cleanUrlStr);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", finalUa);
                    
                    if (isHtmlMode) {
                        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                    }
                    
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);

                    InputStream inputStream = connection.getInputStream();
                    String mimeType = connection.getContentType();
                    if (mimeType == null) {
                        mimeType = isHtmlMode ? "text/html; charset=UTF-8" : "application/json; charset=UTF-8";
                    }
                    
                    String encoding = "UTF-8";
                    if (mimeType.contains("charset=")) {
                        try {
                            encoding = mimeType.split("charset=")[1].split(";")[0].trim();
                        } catch (Exception ignored) {}
                    }

                    return new WebResourceResponse(mimeType.split(";")[0].trim(), encoding, inputStream);

                } catch (Exception e) {
                    // Fallback
                }
                
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (uploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && intent != null) {
                String dataString = intent.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
