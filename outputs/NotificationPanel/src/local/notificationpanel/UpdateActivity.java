package local.notificationpanel;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class UpdateActivity extends Activity {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private EditText address;
    private TextView status;
    private Button check,install;
    private android.content.SharedPreferences prefs;
    private boolean returningFromPermission;
    private static final class Release {
        final long code;final String name,url,hash,notes;
        Release(JSONObject j) throws Exception {
            code=j.getLong("versionCode");name=j.getString("versionName");url=j.getString("apkUrl");hash=j.getString("sha256");notes=j.optString("notes","");
            UpdateCore.https(url);
            if(code<=0||!hash.matches("[a-fA-F0-9]{64}")||name.length()>100||notes.length()>10000)throw new IOException("Неверный файл версии");
        }
    }
    public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("panel_updates",MODE_PRIVATE);
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);int p=(int)(20*getResources().getDisplayMetrics().density);box.setPadding(p,p,p,p);
        box.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(p,i.getSystemWindowInsetTop()+p,p,i.getSystemWindowInsetBottom()+p);return i;});
        ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Обновления · "+version());title.setTextSize(24);box.addView(title);
        TextView info=new TextView(this);info.setText("Проверка выполняется только по кнопке. Сервер получает запрос файла версии и APK; содержимое уведомлений не передаётся.\n\nАдрес файла обновлений (HTTPS)");box.addView(info);
        address=new EditText(this);address.setSingleLine(true);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setHint("https://…/latest.json");address.setText(prefs.getString("url","https://github.com/ANevazhno/notification-panel/releases/latest/download/latest.json"));box.addView(address);
        check=new Button(this);check.setText("Проверить обновления");check.setOnClickListener(v->check());box.addView(check);
        install=new Button(this);install.setText("Установить загруженное обновление");install.setOnClickListener(v->install());box.addView(install);
        status=new TextView(this);status.setTextIsSelectable(true);box.addView(status);
        Button back=new Button(this);back.setText("Назад к панели");back.setOnClickListener(v->finish());box.addView(back);
        status.setText(address.getText().length()==0?"Источник пока не настроен. Нужен постоянный адрес latest.json с нашего сервера обновлений.":"Готово к проверке.");
        box.setBackground(new ShadeBackground());PanelDialog.style(box);
        if(state!=null)returningFromPermission=state.getBoolean("permission");
    }
    protected void onSaveInstanceState(Bundle b){b.putBoolean("permission",returningFromPermission);super.onSaveInstanceState(b);}
    protected void onResume(){super.onResume();discardInstalledDownload();refresh();if(returningFromPermission){returningFromPermission=false;if(getPackageManager().canRequestPackageInstalls())install();else status.setText("Разрешение не выдано. Загруженный APK сохранён; можно повторить установку позже.");}}
    private String version(){try{return own().versionName;}catch(Exception e){return "?";}}
    private PackageInfo own() throws Exception{return getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);}
    private void discardInstalledDownload(){
        if(BUSY.get())return;
        File file=UpdateProvider.file(this);if(!file.exists())return;
        try{PackageInfo next=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
            if(next!=null&&getPackageName().equals(next.packageName)&&next.getLongVersionCode()<=own().getLongVersionCode()){
                file.delete();status.setText("Предыдущая загрузка уже установлена. Нажмите «Проверить обновления», чтобы получить новую версию.");
            }
        }catch(Exception ignored){}
    }
    private String downloadedVersion(){try{PackageInfo p=getPackageManager().getPackageArchiveInfo(UpdateProvider.file(this).getAbsolutePath(),0);if(p!=null)return p.versionName;}catch(Exception ignored){}return "";}
    private void refresh(){install.setText("Установить загруженное обновление "+downloadedVersion());check.setEnabled(!BUSY.get());address.setEnabled(!BUSY.get());install.setEnabled(!BUSY.get()&&UpdateProvider.file(this).exists());}
    private void ui(Runnable r){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){r.run();refresh();}});}
    private void error(Exception e){status.setText("Не удалось обновить: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
    private void check(){
        final String url=address.getText().toString().trim();
        try{UpdateCore.https(url);}catch(Exception e){error(e);return;}
        if(!BUSY.compareAndSet(false,true))return;
        prefs.edit().putString("url",url).apply();status.setText("Проверяем версию…");refresh();
        new Thread(()->{
            try{
                ByteArrayOutputStream data=new ByteArrayOutputStream();UpdateCore.fetch(url,data,65536);
                Release r=new Release(new JSONObject(data.toString("UTF-8")));
                if(r.code<=own().getLongVersionCode())ui(()->status.setText("Установлена актуальная версия "+version()));
                else ui(()->new PanelDialog.Builder(this).setTitle("Доступна версия "+r.name).setMessage(r.notes+"\n\nЗагрузить обновление?").setNegativeButton("Позже",null).setPositiveButton("Загрузить",(d,w)->download(r)).show());
            }catch(Exception e){ui(()->error(e));}finally{BUSY.set(false);ui(()->{});}
        },"panel-update-check").start();
    }
    private void download(Release r){
        if(!BUSY.compareAndSet(false,true))return;
        status.setText("Загружаем APK и проверяем подпись…");refresh();
        new Thread(()->{
            File temp=new File(getCacheDir(),"update.part");
            try{
                try(FileOutputStream out=new FileOutputStream(temp)){UpdateCore.fetch(r.url,out,64L*1024*1024);out.getFD().sync();}
                UpdateCore.verifyHash(temp,r.hash);validate(temp,r.code);
                if(!temp.renameTo(UpdateProvider.file(this)))throw new IOException("Не удалось сохранить APK");
                ui(()->status.setText("Версия "+r.name+" загружена. Контрольная сумма, пакет и подпись проверены. Нажмите «Установить загруженное обновление»."));
            }catch(Exception e){ui(()->error(e));}finally{temp.delete();BUSY.set(false);ui(()->{});}
        },"panel-update-download").start();
    }
    static Set<String> signers(PackageInfo p) throws IOException {
        if(p.signingInfo==null)throw new IOException("Не удалось прочитать подпись APK");
        Set<String> set=new HashSet<>();for(Signature s:p.signingInfo.getApkContentsSigners())set.add(s.toCharsString());
        if(set.isEmpty())throw new IOException("Пустая подпись APK");return set;
    }
    void validate(File file,long expected) throws Exception {
        PackageInfo local=own(),next=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(next==null||!getPackageName().equals(next.packageName))throw new IOException("APK не является обновлением панели");
        if(next.getLongVersionCode()<=local.getLongVersionCode()||(expected>0&&next.getLongVersionCode()!=expected))throw new IOException("Версия загруженного APK: "+next.versionName+" ("+next.getLongVersionCode()+"), установленная: "+local.versionName+" ("+local.getLongVersionCode()+"). Нажмите «Проверить обновления» и загрузите новую версию");
        if(!signers(local).equals(signers(next)))throw new IOException("APK подписан другим ключом");
        if(next.applicationInfo!=null&&next.applicationInfo.minSdkVersion>android.os.Build.VERSION.SDK_INT)throw new IOException("Для APK нужна более новая версия Android");
    }
    private void install(){
        if(BUSY.get())return;
        try{
            validate(UpdateProvider.file(this),0);
            if(!getPackageManager().canRequestPackageInstalls()){
                new PanelDialog.Builder(this).setTitle("Разрешение установки").setMessage("Android попросит разрешить установку обновлений из «Панели уведомлений». После возвращения появится системное подтверждение установки.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Открыть настройки",(d,w)->{
                        try{returningFromPermission=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(Exception e){returningFromPermission=false;error(e);}
                    }).show();return;
            }
            Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(UpdateProvider.URI,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("Обновление панели",UpdateProvider.URI));startActivity(i);
            status.setText("APK передан системному установщику. Установка требует вашего подтверждения.");
        }catch(Exception e){error(e);}
    }
}

