package com.sloflix.tv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.sloflix.tv.ui.TestTags
import org.json.JSONObject

/**
 * Desktop Chrome UA — some StreamP2P embeds behave better than leanback/TV agents.
 */
private const val DesktopChromeUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** Site origin used so StreamP2P sees a Sloflix Referer / document.referrer. */
private const val SloflixRefererBase = "https://www.sloflix.com/"

/** JS that tries JW Player, HTML5 video, then common play-button selectors. */
private val TryStartPlaybackJs = """
(function() {
  try {
    if (typeof jwplayer === 'function') {
      var p = jwplayer();
      if (p && typeof p.play === 'function') { p.play(); return 'jwplayer'; }
      var ids = (typeof jwplayer.getPlayers === 'function') ? jwplayer.getPlayers() : [];
      if (ids && ids.length) {
        var first = jwplayer(ids[0].id || ids[0]);
        if (first && typeof first.play === 'function') { first.play(); return 'jwplayer-first'; }
      }
    }
  } catch (e) {}
  try {
    var v = document.querySelector('video');
    if (v && typeof v.play === 'function') {
      var r = v.play();
      if (r && typeof r.catch === 'function') r.catch(function(){});
      return 'video';
    }
  } catch (e) {}
  try {
    var sel = [
      '.jw-icon-playback',
      '.jw-display-icon-container',
      '.jw-display-icon-display',
      'button[aria-label*="Play" i]',
      'button[aria-label*="play" i]',
      '[aria-label*="Play" i]',
      '.vjs-big-play-button',
      '.ytp-large-play-button'
    ];
    for (var i = 0; i < sel.length; i++) {
      var el = document.querySelector(sel[i]);
      if (el) { el.click(); return 'click:' + sel[i]; }
    }
  } catch (e) {}
  return 'noop';
})();
""".trimIndent()

private val PlaybackRetryDelaysMs = longArrayOf(500L, 1_500L, 3_000L)

/**
 * Bootstrap from https://www.sloflix.com/ then location.replace(embed).
 * Sets document.referrer for JS checks while keeping the player top-level for play injection.
 */
private fun loadEmbedWithSloflixReferrer(webView: WebView, embedUrl: String) {
    val quotedUrl = JSONObject.quote(embedUrl)
    val html =
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8"/>
          <script>location.replace($quotedUrl);</script>
        </head>
        <body style="margin:0;background:#000"></body>
        </html>
        """.trimIndent()
    webView.loadDataWithBaseURL(
        SloflixRefererBase,
        html,
        "text/html",
        "UTF-8",
        null,
    )
}

private fun isStreamP2PHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = Uri.parse(url).host?.lowercase() ?: return false
    return host == "strp2p.com" || host.endsWith(".strp2p.com")
}

/**
 * Fullscreen WebView for StreamP2P (and similar) HTML embeds.
 * Hardware Back exits HTML5 fullscreen first, then closes the screen.
 */
@Composable
fun WebViewPlayerScreen(
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var fullscreenActive by remember { mutableStateOf(false) }
    val container = remember(context) {
        PlayerWebContainer(context) { fullscreenActive = it }
    }

    DisposableEffect(url) {
        // Navigate from sloflix.com base so document.referrer is set for StreamP2P checks.
        loadEmbedWithSloflixReferrer(container.webView, url)
        onDispose {
            container.webView.stopLoading()
        }
    }

    DisposableEffect(Unit) {
        onDispose { container.destroy() }
    }

    BackHandler {
        when {
            fullscreenActive -> container.exitFullscreen()
            container.webView.canGoBack() -> container.webView.goBack()
            else -> onBack()
        }
    }

    AndroidView(
        factory = {
            container.root.also {
                container.webView.requestFocus()
            }
        },
        update = {
            container.webView.requestFocus()
        },
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTags.WebViewPlayerRoot),
    )
}

@SuppressLint("SetJavaScriptEnabled")
private class PlayerWebContainer(
    context: Context,
    private val onFullscreenChanged: (Boolean) -> Unit,
) {
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val playbackRetryRunnables = mutableListOf<Runnable>()

    val root = FrameLayout(context).apply {
        setBackgroundColor(AndroidColor.BLACK)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    val webView = WebView(context).apply {
        setBackgroundColor(AndroidColor.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = DesktopChromeUserAgent
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view ?: return
                view.requestFocus()
                // Skip bootstrap HTML; inject play only after StreamP2P finishes loading.
                if (isStreamP2PHost(url)) {
                    schedulePlaybackRetries(view)
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                visibility = View.GONE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                onFullscreenChanged(true)
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }
        setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                -> {
                    injectTryStartPlayback()
                    dispatchCenterClick()
                    true
                }
                else -> false
            }
        }
        root.addView(
            this,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        post { requestFocus() }
    }

    fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        hideCustomView()
    }

    fun destroy() {
        cancelPlaybackRetries()
        hideCustomView()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        root.removeView(webView)
        webView.destroy()
    }

    private fun hideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        customViewCallback = null
        webView.visibility = View.VISIBLE
        onFullscreenChanged(false)
    }

    private fun schedulePlaybackRetries(view: WebView) {
        cancelPlaybackRetries()
        for (delayMs in PlaybackRetryDelaysMs) {
            val runnable = Runnable { injectTryStartPlayback(view) }
            playbackRetryRunnables += runnable
            view.postDelayed(runnable, delayMs)
        }
    }

    private fun cancelPlaybackRetries() {
        for (runnable in playbackRetryRunnables) {
            webView.removeCallbacks(runnable)
        }
        playbackRetryRunnables.clear()
    }

    private fun injectTryStartPlayback(target: WebView = webView) {
        target.evaluateJavascript(TryStartPlaybackJs, null)
    }

    /** Synthetic center tap — some embeds still require a user-gesture click. */
    private fun dispatchCenterClick() {
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) return
        val x = w / 2f
        val y = h / 2f
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + 50,
            MotionEvent.ACTION_UP,
            x,
            y,
            0,
        )
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }
}
