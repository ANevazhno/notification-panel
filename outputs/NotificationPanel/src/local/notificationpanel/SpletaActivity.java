package local.notificationpanel;
import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import android.view.*;
import android.widget.*;

/** Official portal login, isolated from the system browser. No JavaScript bridge. */
public final class SpletaActivity extends Activity {
    private WebView web;private TextView state;private boolean opening;
    @Override public void onCreate(Bundle b){super.onCreate(b);opening=getIntent().getBooleanExtra("open",false);
        getWindow().setStatusBarColor(PanelColors.BACKGROUND);getWindow().setNavigationBarColor(PanelColors.BACKGROUND);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(new ShadeBackground());
        root.setPadding(dp(16),dp(12),dp(16),0);root.setOnApplyWindowInsetsListener((v,i)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=i.getInsets(android.view.WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}else{top=i.getSystemWindowInsetTop();bottom=i.getSystemWindowInsetBottom();}v.setPadding(dp(16),top+dp(12),dp(16),bottom);return i;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_spleta);header.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView title=new TextView(this);title.setText("Spleta");title.setTextSize(24);title.setTextColor(PanelColors.TEXT);title.setPadding(dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button back=button("Готово",this::finish);header.addView(back);root.addView(header);
        state=new TextView(this);state.setTextColor(PanelColors.BODY);state.setTextSize(14);state.setPadding(0,dp(8),0,dp(8));state.setText(opening?"portal.skenergoresurs.ru":"Войдите в свой профиль на сайте Spleta, затем нажмите «Подключить».");root.addView(state);
        if(!opening){root.addView(button("Подключить уведомления",()->SpletaSource.connect(ok->{if(isFinishing())return;state.setText(SpletaSource.status());if(ok){startPush();}})));}
        web=new WebView(this);web.setBackgroundColor(PanelColors.BACKGROUND);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(SpletaSource.allowed(r.getUrl().toString()))return false;state.setText("Внешняя ссылка недоступна в окне Spleta");return true;}
            public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){h.cancel();state.setText("Не удалось проверить защищённое соединение");}
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())state.setText("Не удалось открыть Spleta. Проверьте подключение.");}
            public void onPageFinished(WebView v,String url){CookieManager.getInstance().flush();}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();
        web.loadUrl(opening?SpletaSource.safeUrl(getIntent().getDataString()):SpletaSource.ORIGIN+"/login");
    }
    private void startPush(){SpletaPushService.restore(this);finish();}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(PanelColors.TEXT);b.setMinHeight(dp(48));android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(PanelColors.BUTTON);d.setCornerRadius(dp(12));b.setBackground(d);b.setOnClickListener(v->action.run());return b;}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null)web.destroy();super.onDestroy();}
}
