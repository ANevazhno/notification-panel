package local.notificationpanel;
import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.CookieManager;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

/** Private SSE connection restored automatically for a connected portal. No wake lock. */
public final class SpletaPushService extends Service {
    private volatile boolean stopped;private volatile HttpsURLConnection connection;
    interface ConnectionFactory { HttpsURLConnection open() throws Exception; }
    private static ConnectionFactory factory=()->(HttpsURLConnection)new java.net.URL(SpletaSource.ORIGIN+"/api/notifications/stream").openConnection();
    private Thread worker;private final Handler main=new Handler(Looper.getMainLooper());
    private static SpletaPushService instance;static String state="Фоновое соединение выключено";
    static boolean running(){return instance!=null;}
    static void stop(Context c){c.stopService(new Intent(c,SpletaPushService.class));}
    static void restore(Context c){
        if(!SpletaSource.enabled()||running())return;
        try{c.startForegroundService(new Intent(c,SpletaPushService.class));}
        catch(RuntimeException ex){state="Фоновый приём недоступен. Откройте Шторку для повтора.";}
    }
    public IBinder onBind(Intent i){return null;}
    public int onStartCommand(Intent i,int flags,int id){
        if(!SpletaSource.enabled()){stopSelf();return START_NOT_STICKY;}
        if(worker!=null)return START_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("spleta_connection","Соединение со Spleta",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,733,new Intent(this,PanelActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"spleta_connection").setSmallIcon(R.drawable.ic_spleta).setContentTitle("Spleta · приём уведомлений").setContentText("Личное соединение с порталом").setOngoing(true).setContentIntent(open).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(733,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(733,n);
        instance=this;worker=new Thread(this::listen,"spleta-push");worker.start();return START_STICKY;
    }
    private void listen(){int delay=3;String revision="";
        while(!stopped){try{
            String cookie=CookieManager.getInstance().getCookie(SpletaSource.ORIGIN);if(cookie==null||cookie.isEmpty()){state="Нужен вход в Spleta";main.post(this::stopSelf);break;}
            HttpsURLConnection c=factory.open();connection=c;
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(35000);c.setRequestProperty("Cookie",cookie);c.setRequestProperty("Accept","text/event-stream");c.setUseCaches(false);
            int code=c.getResponseCode();if(code==401||code==403){state="Нужен вход в Spleta";main.post(()->{SpletaSource.sync(true,null);stopSelf();});break;}
            if(code!=200||c.getContentType()==null||!c.getContentType().startsWith("text/event-stream")){state=code==404?"Серверный канал ещё не установлен":"Переподключаемся к Spleta";throw new IOException();}
            state="Прямое соединение со Spleta";delay=3;
            try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){
                String event="",line;while(!stopped&&(line=in.readLine())!=null){if(line.length()>4096)throw new IOException();
                    if(line.startsWith("event: "))event=line.substring(7);
                    if(line.startsWith("data: ")&&"sync".equals(event)){String next=new org.json.JSONObject(line.substring(6)).getString("revision");if(!next.equals(revision)){revision=next;main.post(()->SpletaSource.sync(true,null));}}
                    if(line.startsWith("data: ")&&"auth".equals(event)){state="Нужен вход в Spleta";main.post(()->{SpletaSource.sync(true,null);stopSelf();});break;}
                    if(line.isEmpty())event="";
                }
            }
        }catch(Exception ex){if(!stopped)state="Ожидаем соединения со Spleta";delay=Math.min(120,delay*2);}
        finally{HttpsURLConnection c=connection;connection=null;if(c!=null)c.disconnect();}
        if(!stopped)try{Thread.sleep(delay*1000L);}catch(InterruptedException ex){break;}
        }
    }
    @Override public void onDestroy(){stopped=true;if(worker!=null)worker.interrupt();HttpsURLConnection c=connection;if(c!=null)c.disconnect();if(instance==this)instance=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
