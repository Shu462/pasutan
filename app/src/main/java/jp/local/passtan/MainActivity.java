package jp.local.passtan;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.View;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int EXPORT_JSON = 1001;
    private static final int IMPORT_JSON = 1002;
    private WebView web;
    private TextToSpeech tts;
    private String pendingBackup = null;
    private boolean voiceReady = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247,248,252));
        getWindow().setNavigationBarColor(Color.rgb(247,248,252));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(247,248,252));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new JsBridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        tts = new TextToSpeech(this, this);
    }

    @Override public void onInit(int status) {
        if(status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            voiceReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            if(voiceReady) tts.setSpeechRate(0.85f);
        }
    }

    final class JsBridge {
        @JavascriptInterface public void say(String text) {
            runOnUiThread(() -> {
                if(voiceReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "passtan-tts");
                else Toast.makeText(MainActivity.this, "端末の英語音声データを確認してください", Toast.LENGTH_LONG).show();
            });
        }
        @JavascriptInterface public void saveBackup(String json) {
            runOnUiThread(() -> {
                pendingBackup = json;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.setType("application/json");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.putExtra(Intent.EXTRA_TITLE, "passtan3_backup.json");
                startActivityForResult(i, EXPORT_JSON);
            });
        }
        @JavascriptInterface public void openBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("application/json");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, IMPORT_JSON);
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if(requestCode == EXPORT_JSON && pendingBackup != null) {
                try(OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if(out == null) throw new IllegalStateException("出力先を開けません");
                    out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                }
                pendingBackup = null;
                Toast.makeText(this, "バックアップを書き出しました", Toast.LENGTH_SHORT).show();
            } else if(requestCode == IMPORT_JSON) {
                byte[] bytes;
                try(InputStream in = getContentResolver().openInputStream(data.getData())) {
                    if(in == null) throw new IllegalStateException("読み込み元を開けません");
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192]; int n;
                    while((n = in.read(chunk)) != -1) {
                        buffer.write(chunk,0,n);
                        if(buffer.size() > 10_000_000) throw new IllegalArgumentException("バックアップが大きすぎます");
                    }
                    bytes = buffer.toByteArray();
                }
                if(bytes.length > 10_000_000) throw new IllegalArgumentException("バックアップが大きすぎます");
                String raw = new String(bytes, StandardCharsets.UTF_8);
                web.evaluateJavascript("window.acceptBackup(" + JSONObject.quote(raw) + ")", null);
            }
        } catch(Exception e) {
            Toast.makeText(this, "ファイル操作に失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.goHomeFromNative()", null);
    }

    @Override protected void onDestroy() {
        if(tts != null) {tts.stop();tts.shutdown();}
        web.destroy();
        super.onDestroy();
    }
}
