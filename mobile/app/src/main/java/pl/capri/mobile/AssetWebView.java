package pl.capri.mobile;

import android.content.Context;
import android.graphics.Color;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.util.Map;

final class AssetWebView {
    static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    static WebView create(Context context) {
        WebView view = new WebView(context);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.getSettings().setMediaPlaybackRequiresUserGesture(true);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                String path=req.getUrl().getPath();
                if (!"appassets.androidplatform.net".equals(req.getUrl().getHost()) || path==null || !path.startsWith("/assets/") || path.contains(".."))
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                String file=path.substring(8);
                String type = file.endsWith(".js") ? "application/javascript" : file.endsWith(".css") ? "text/css" : file.endsWith(".png") ? "image/png" : file.endsWith(".webp") ? "image/webp" : file.endsWith(".txt") ? "text/plain" : "text/html";
                try { return new WebResourceResponse(type, "UTF-8", 200, "OK", Map.of("Cache-Control","no-store"), context.getAssets().open(file)); }
                catch(Exception e) { return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", Map.of(), new ByteArrayInputStream(new byte[0])); }
            }
        });
        return view;
    }
}
