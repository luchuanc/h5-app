package com.example.myapplication

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.h5.H5BundleInfo
import com.example.myapplication.h5.H5PackageManager

class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var webView: WebView
    private lateinit var packageManager: H5PackageManager
    private lateinit var recordingManager: RecordingManager
    private lateinit var nativeBridge: NativeBridge
    private var currentBundle: H5BundleInfo? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CookieManager.getInstance().setAcceptCookie(true)

        packageManager = H5PackageManager(applicationContext)
        packageManager.syncLauncherAliasWithSelection()
        recordingManager = RecordingManager(applicationContext)

        webView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        nativeBridge = NativeBridge(this, packageManager, webView, recordingManager)

        setContentView(createRootView())
        configureWebView()
        installBackHandler()
        loadBestAvailableBundle()
        syncRemoteBundleInBackground()
    }

    private fun createRootView(): View {
        val root = FrameLayout(this).apply {
            clipToPadding = true
            addView(webView)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        return root
    }

    @SuppressLint("JavascriptInterface")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.addJavascriptInterface(nativeBridge, "NativeBridgeHost")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    TAG,
                    "H5 console ${consoleMessage.messageLevel()}: ${consoleMessage.message()}"
                )
                return super.onConsoleMessage(consoleMessage)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val scheme = url.scheme.orEmpty()
                val isInternal =
                    scheme == "file" || scheme == "data" || scheme == "about" || scheme == "javascript"
                if (isInternal) {
                    return false
                }

                if (isTrustedRemoteNavigation(url)) {
                    return false
                }

                if (request.isForMainFrame) {
                    openExternalUri(url)
                    return true
                }

                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                Log.d(TAG, "Loading page: $url")
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (BuildConfig.DEBUG &&
                    request.url.lastPathSegment == "vconsole.min.js"
                ) {
                    return try {
                        val data = readAssetText("www/js/vconsole.min.js")
                        WebResourceResponse(
                            "application/javascript", "utf-8",
                            data.byteInputStream()
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (BuildConfig.DEBUG) {
                    view.evaluateJavascript(
                        """
                        (function(){
                          if(window.VConsole) return;
                          var s=document.createElement('script');
                          s.src='js/vconsole.min.js';
                          s.onload=function(){
                            try{ new VConsole(); }catch(e){}
                          };
                          document.head.appendChild(s);
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    showErrorPage(
                        title = "Bundle page failed to load",
                        message = error.description?.toString() ?: "Unknown error"
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame) {
                    showErrorPage(
                        title = "Bundle resource error",
                        message = "HTTP ${errorResponse.statusCode}"
                    )
                }
            }
        }
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun loadBestAvailableBundle() {
        currentBundle = packageManager.resolveLaunchBundle()
        loadBundle(requireNotNull(currentBundle), forceRefresh = false)
    }

    private fun syncRemoteBundleInBackground() {
        packageManager.syncRemotePackageAsync(
            onSuccess = { bundle ->
                val shouldReload = currentBundle?.mode != bundle.mode ||
                    currentBundle?.version != bundle.version
                currentBundle = bundle

                if (shouldReload) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Downloaded package updated to ${bundle.version}",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadBundle(bundle, forceRefresh = true)
                    }
                }
            },
            onFailure = { throwable ->
                Log.w(TAG, "Remote bundle sync failed", throwable)
            }
        )
    }

    private fun showErrorPage(title: String, message: String) {
        val fallbackUrl = currentBundle?.entryUrl ?: packageManager.resolveLaunchBundle().entryUrl
        val errorHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                body {
                  margin: 0;
                  min-height: 100vh;
                  display: grid;
                  place-items: center;
                  background: linear-gradient(160deg, #f8fbff, #eef3f7);
                  color: #17324d;
                  font-family: sans-serif;
                }
                .card {
                  width: min(88vw, 460px);
                  padding: 24px;
                  border-radius: 20px;
                  background: rgba(255, 255, 255, 0.94);
                  box-shadow: 0 18px 50px rgba(23, 50, 77, 0.12);
                }
                h1 { margin-top: 0; font-size: 22px; }
                p { line-height: 1.6; }
                button {
                  border: none;
                  padding: 12px 16px;
                  border-radius: 999px;
                  background: #17324d;
                  color: white;
                }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>$title</h1>
                <p>$message</p>
                <button onclick="location.href='$fallbackUrl'">Back to bundle home</button>
              </div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "utf-8", null)
    }

    private fun openExternalUri(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Unable to open external link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBundle(bundle: H5BundleInfo, forceRefresh: Boolean) {
        currentBundle = bundle
        val isRemoteBundle = isRemoteBundle(bundle.entryUrl)
        webView.settings.cacheMode = if (isRemoteBundle) {
            WebSettings.LOAD_NO_CACHE
        } else {
            WebSettings.LOAD_DEFAULT
        }
        if (forceRefresh) {
            webView.clearCache(isRemoteBundle)
            webView.clearHistory()
        }
        webView.loadUrl(bundle.entryUrl)
    }

    private fun isTrustedRemoteNavigation(uri: Uri): Boolean {
        val currentEntry = currentBundle?.entryUrl ?: return false
        val currentUri = runCatching { Uri.parse(currentEntry) }.getOrNull() ?: return false
        val currentScheme = currentUri.scheme.orEmpty()
        if (currentScheme != "http" && currentScheme != "https") {
            return false
        }

        val targetScheme = uri.scheme.orEmpty()
        if (targetScheme != "http" && targetScheme != "https") {
            return false
        }

        return currentUri.scheme == uri.scheme &&
            currentUri.host == uri.host &&
            normalizedPort(currentUri) == normalizedPort(uri)
    }

    private fun normalizedPort(uri: Uri): Int {
        if (uri.port != -1) {
            return uri.port
        }
        return when (uri.scheme) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
    }

    private fun isRemoteBundle(entryUrl: String): Boolean =
        entryUrl.startsWith("https://") || entryUrl.startsWith("http://")
    private fun readAssetText(path: String): String =
        assets.open(path).bufferedReader().use { it.readText() }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            nativeBridge.onPermissionResult(granted)
        }
    }

    override fun onDestroy() {
        recordingManager.shutdown()
        recordingManager.cleanupAllRecordings()
        webView.removeJavascriptInterface("NativeBridgeHost")
        webView.stopLoading()
        webView.destroy()
        packageManager.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "H5Container"
    }
}
