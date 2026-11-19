package kr.tero.factoryos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String START_URL = "https://os.tero.kr/edang/";
    private static final String TRUSTED_HOST = "os.tero.kr";
    private static final int FILE_REQUEST = 1001;
    private static final int CAMERA_REQUEST = 1002;
    private static final int CAMERA_CHOOSER_REQUEST = 1003;
    /** 태블릿 판정 기준: 화면 최소 폭 600dp(일반 태블릿) 이상 */
    private static final int TABLET_MIN_WIDTH_DP = 600;

    private WebView webView;
    private FrameLayout content;
    private LinearLayout errorPane;
    private ProgressBar progress;
    private ValueCallback<Uri[]> pendingFileCallback;
    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private PermissionRequest pendingCameraPermission;
    private WebChromeClient.FileChooserParams pendingChooserParams;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16, 41, 61));
        getWindow().setNavigationBarColor(Color.rgb(16, 41, 61));

        if (!isTablet()) {
            showTabletOnlyNotice();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets system = insets.getInsets(WindowInsets.Type.systemBars());
                top = system.top;
                bottom = system.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(root);

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        webView = new WebView(this);
        content.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        WebView.setWebContentsDebuggingEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri url = request.getUrl();
                if (isTrustedUrl(url)) return false;
                openExternal(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (isTrustedUrl(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (errorPane != null) errorPane.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError("서버에 연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.");
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 500)
                    showError("TERO 서버가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요.");
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showError("보안 연결을 확인할 수 없습니다. 서버 인증서를 점검해 주세요.");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
                pendingFileCallback = callback;
                pendingCameraUri = null;
                pendingCameraFile = null;
                pendingChooserParams = null;
                // CAMERA 권한을 선언한 앱은 권한 없이 촬영 앱을 실행하면 SecurityException이 발생하므로
                // 사진 첨부가 가능한 입력이면 먼저 권한을 요청하고, 결과가 오면 선택기를 연다.
                if (acceptsImages(params) && MainActivity.this.hasCamera()
                        && MainActivity.this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    pendingChooserParams = params;
                    MainActivity.this.requestPermissions(new String[] { Manifest.permission.CAMERA }, CAMERA_CHOOSER_REQUEST);
                    return true;
                }
                MainActivity.this.launchFileChooser(params);
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleCameraPermission(request));
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Uri uri = Uri.parse(url);
            if (!isTrustedUrl(uri)) {
                new AlertDialog.Builder(this)
                        .setTitle("다운로드 안내")
                        .setMessage("이 파일은 앱에서 직접 내려받지 못합니다. 브라우저에서 TERO를 열어 다운로드할 수 있습니다.")
                        .setPositiveButton("브라우저에서 열기", (d, which) -> openExternal(Uri.parse(START_URL)))
                        .setNegativeButton("취소", null)
                        .show();
                return;
            }
            try {
                String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
                DownloadManager.Request req = new DownloadManager.Request(uri);
                req.setTitle(name);
                req.setMimeType(mimeType == null ? "application/octet-stream" : mimeType);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                req.addRequestHeader("User-Agent", userAgent == null ? "TERO-Tablet" : userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.isEmpty()) req.addRequestHeader("Cookie", cookies);
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                manager.enqueue(req);
                Toast.makeText(this, "다운로드를 시작했어요.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "다운로드에 실패했어요. 브라우저에서 다시 시도해 주세요.", Toast.LENGTH_LONG).show();
            }
        });

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
        content.addView(progress, bar);

        buildErrorPane();
        if (savedInstanceState != null) {
            if (webView.restoreState(savedInstanceState) == null) webView.loadUrl(START_URL);
        } else {
            webView.loadUrl(START_URL);
        }
    }

    private void buildErrorPane() {
        errorPane = new LinearLayout(this);
        errorPane.setOrientation(LinearLayout.VERTICAL);
        errorPane.setGravity(Gravity.CENTER);
        errorPane.setPadding(dp(28), dp(28), dp(28), dp(28));
        errorPane.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("TERO Factory OS");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(16, 41, 61));
        title.setGravity(Gravity.CENTER);
        errorPane.addView(title);

        TextView description = new TextView(this);
        description.setText("네트워크 연결을 확인해 주세요.");
        description.setTextSize(15);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = dp(12);
        errorPane.addView(description, descParams);

        Button retry = new Button(this);
        retry.setText("다시 연결");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            errorPane.setVisibility(View.GONE);
            webView.loadUrl(START_URL);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-2, -2);
        retryParams.topMargin = dp(24);
        errorPane.addView(retry, retryParams);

        FrameLayout.LayoutParams overlay = new FrameLayout.LayoutParams(-1, -1);
        content.addView(errorPane, overlay);
        errorPane.setVisibility(View.GONE);
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (errorPane == null) return;
            if (errorPane.getChildAt(1) instanceof TextView) {
                ((TextView) errorPane.getChildAt(1)).setText(message);
            }
            errorPane.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
        });
    }

    private boolean isTablet() {
        // 분할 화면으로 실행된 경우 Activity 설정값은 창 크기 기준이므로, 기기 전체 화면 기준 값도 함께 확인
        return isTabletConfig(getResources().getConfiguration())
                || isTabletConfig(Resources.getSystem().getConfiguration());
    }

    private static boolean isTabletConfig(Configuration config) {
        if (config == null) return false;
        if (config.smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED
                && config.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP) return true;
        int size = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        return size >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private void showTabletOnlyNotice() {
        FrameLayout blank = new FrameLayout(this);
        blank.setBackgroundColor(Color.WHITE);
        setContentView(blank);
        new AlertDialog.Builder(this)
                .setTitle("태블릿 전용 앱")
                .setMessage("TERO Factory OS 앱은 Android 태블릿 전용입니다.\n휴대폰에서는 브라우저로 os.tero.kr/edang/ 에 접속해 주세요.")
                .setCancelable(false)
                .setPositiveButton("닫기", (d, which) -> finish())
                .setNegativeButton("브라우저로 열기", (d, which) -> {
                    openExternal(Uri.parse(START_URL));
                    finish();
                })
                .show();
    }

    private boolean hasCamera() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private void launchFileChooser(WebChromeClient.FileChooserParams params) {
        try {
            Intent picker = params.createIntent();
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(picker, "파일 또는 사진 선택");
            if (acceptsImages(params)) {
                Intent camera = createCameraIntent();
                if (camera != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[] { camera });
                }
            }
            startActivityForResult(chooser, FILE_REQUEST);
        } catch (Exception ex) {
            cancelFileChooser();
            Toast.makeText(this, "파일 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelFileChooser() {
        ValueCallback<Uri[]> old = pendingFileCallback;
        pendingFileCallback = null;
        pendingChooserParams = null;
        deleteEmptyCameraFile();
        pendingCameraUri = null;
        pendingCameraFile = null;
        if (old != null) old.onReceiveValue(null);
    }

    private void deleteEmptyCameraFile() {
        if (pendingCameraFile != null && pendingCameraFile.exists() && pendingCameraFile.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            pendingCameraFile.delete();
        }
    }

    private Intent createCameraIntent() {
        if (!hasCamera() || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            File folder = new File(getCacheDir(), "captures");
            if (!folder.exists() && !folder.mkdirs()) return null;
            File image = File.createTempFile("tero_photo_", ".jpg", folder);
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", image);
            pendingCameraFile = image;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.setClipData(android.content.ClipData.newRawUri("TERO camera photo", pendingCameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) {
                deleteEmptyCameraFile();
                pendingCameraUri = null;
                pendingCameraFile = null;
                return null;
            }
            return intent;
        } catch (IOException | IllegalArgumentException ex) {
            deleteEmptyCameraFile();
            pendingCameraUri = null;
            pendingCameraFile = null;
            return null;
        }
    }

    private boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        if (params.isCaptureEnabled()) return true;
        for (String accept : params.getAcceptTypes()) {
            if (accept != null && (accept.startsWith("image/") || accept.equals("*/*"))) return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_REQUEST) return;
        if (pendingFileCallback == null) return;

        Uri[] values = null;
        if (resultCode == Activity.RESULT_OK) {
            // 촬영용 임시 파일에 내용이 기록됐다면 카메라로 찍은 것 (일부 카메라 앱은 결과 Intent를 비워서 돌려줌)
            if (pendingCameraUri != null && pendingCameraFile != null && pendingCameraFile.length() > 0) {
                values = new Uri[] { pendingCameraUri };
            } else {
                Uri[] selected = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                if (selected != null) {
                    ArrayList<Uri> safe = new ArrayList<>();
                    for (Uri selectedUri : selected) {
                        if (selectedUri != null && "content".equalsIgnoreCase(selectedUri.getScheme())) safe.add(selectedUri);
                    }
                    if (!safe.isEmpty()) values = safe.toArray(new Uri[0]);
                }
            }
        }
        pendingFileCallback.onReceiveValue(values);
        pendingFileCallback = null;
        deleteEmptyCameraFile();
        pendingCameraUri = null;
        pendingCameraFile = null;
    }

    private void handleCameraPermission(PermissionRequest request) {
        if (!isTrustedUrl(request.getOrigin())) {
            request.deny();
            return;
        }
        if (!Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE });
        } else {
            if (pendingCameraPermission != null) pendingCameraPermission.deny();
            pendingCameraPermission = request;
            requestPermissions(new String[]{ Manifest.permission.CAMERA }, CAMERA_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_CHOOSER_REQUEST) {
            // 허용 여부와 관계없이 파일 선택기는 연다 (거부 시 카메라 항목만 빠짐)
            WebChromeClient.FileChooserParams params = pendingChooserParams;
            pendingChooserParams = null;
            if (params != null && pendingFileCallback != null) {
                launchFileChooser(params);
            } else {
                cancelFileChooser();
            }
            return;
        }
        if (requestCode != CAMERA_REQUEST || pendingCameraPermission == null) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingCameraPermission.grant(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE });
        } else {
            pendingCameraPermission.deny();
        }
        pendingCameraPermission = null;
    }

    private boolean isTrustedUrl(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && TRUSTED_HOST.equalsIgnoreCase(uri.getHost());
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (scheme == null) return;
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")
                && !scheme.equalsIgnoreCase("mailto") && !scheme.equalsIgnoreCase("tel")) {
            Toast.makeText(this, "지원하지 않는 링크입니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "링크를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int px) {
        return Math.round(px * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("TERO Factory OS")
                    .setMessage("앱을 종료할까요?")
                    .setPositiveButton("종료", (d, which) -> finish())
                    .setNegativeButton("취소", null)
                    .show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
        if (pendingCameraPermission != null) pendingCameraPermission.deny();
        if (webView != null) {
            content.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
