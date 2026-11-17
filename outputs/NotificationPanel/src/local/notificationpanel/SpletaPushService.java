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
    private static final class LoginRequired extends IOException {}
    private static ConnectionFactory factory=()->(HttpsURLConnection)new java.net.URL(SpletaSource.ORIGIN+"/api/notifications/stream").openConnection();
    private Thread worker;private final Handler main=new Handler(Looper.getMainLooper());
    private static SpletaPushService instance;static volatile String state="Фоновое соединение выключено";
    static boolean running(){return instance!=null;}
    static void stop(Context c){c.stopService(new Intent(c,SpletaPushService.class));}
    private void wake(){if(worker!=null)worker.interrupt();HttpsURLConnection c=connection;if(c!=null)c.disconnect();}
    static void restore(Context c){
        if(!SpletaSource.enabled())return;
        if(running()){if(!"Прямое соединение со Spleta".equals(state))instance.wake();return;}
        try{c.startForegroundService(new Intent(c,SpletaPushService.class));}
        catch(RuntimeException ex){state="Фоновый приём недоступен. Откройте Шторку для повтора.";SpletaSource.scheduleSoon();}
    }
    public IBinder onBind(Intent i){return null;}
    public int onStartCommand(Intent i,int flags,int id){
        if(!SpletaSource.enabled()){stopSelf();return START_NOT_STICKY;}
        if(worker!=null&&worker.isAlive())return START_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("spleta_connection","Соединение со Spleta",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,733,new Intent(this,PanelActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"spleta_connection").setSmallIcon(R.drawable.ic_spleta).setContentTitle("Spleta · приём уведомлений").setContentText("Личное соединение с порталом").setOngoing(true).setContentIntent(open).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(733,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(733,n);
        instance=this;worker=new Thread(this::listen,"spleta-push");worker.start();return START_STICKY;
    }
    private void listen(){int delay=3;String revision="",rejectedCookie=null;boolean firstConnection=true,missingCookieReported=false;
        while(!stopped){try{
            String cookie=CookieManager.getInstance().getCookie(SpletaSource.ORIGIN);
            if(cookie==null||cookie.isEmpty()){
                if(!missingCookieReported){missingCookieReported=true;main.post(()->SpletaSource.sync(true,null));}
                throw new LoginRequired();
            }
            missingCookieReported=false;
            if(cookie.equals(rejectedCookie))throw new LoginRequired();
            HttpsURLConnection c=factory.open();connection=c;
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(35000);c.setRequestProperty("Cookie",cookie);c.setRequestProperty("Accept","text/event-stream");c.setUseCaches(false);
            int code=c.getResponseCode();if(code==401||code==403){rejectedCookie=cookie;main.post(()->SpletaSource.sync(true,null));throw new LoginRequired();}
            if(code!=200||c.getContentType()==null||!c.getContentType().startsWith("text/event-stream")){state=code==404?"Серверный канал ещё не установлен":"Переподключаемся к Spleta";throw new IOException();}
            rejectedCookie=null;state="Прямое соединение со Spleta";delay=3;
            if(firstConnection){firstConnection=false;main.post(()->SpletaSource.sync(true,null));}
            try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){
                String event="",line;while(!stopped&&(line=in.readLine())!=null){if(line.length()>4096)throw new IOException();
                    if(line.startsWith("event: "))event=line.substring(7);
                    if(line.startsWith("data: ")&&"sync".equals(event)){String next=new org.json.JSONObject(line.substring(6)).getString("revision");if(!next.equals(revision)){revision=next;main.post(()->SpletaSource.sync(true,null));}}
                    if(line.startsWith("data: ")&&"auth".equals(event)){rejectedCookie=cookie;main.post(()->SpletaSource.sync(true,null));throw new LoginRequired();}
                    if(line.isEmpty())event="";
                }
            }
            if(!stopped)state="Переподключаемся к Spleta";
        }catch(LoginRequired ex){if(!stopped)state="Нужен вход в Spleta";delay=Math.min(120,delay*2);}
        catch(Exception ex){if(!stopped&&!"Серверный канал ещё не установлен".equals(state))state="Ожидаем соединения со Spleta";delay=Math.min(120,delay*2);}
        finally{HttpsURLConnection c=connection;connection=null;if(c!=null)c.disconnect();}
        if(!stopped)try{Thread.sleep(delay*1000L);}catch(InterruptedException ex){if(stopped)break;}
        }
    }
    @Override public void onDestroy(){stopped=true;if(worker!=null)worker.interrupt();HttpsURLConnection c=connection;if(c!=null)c.disconnect();if(instance==this)instance=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
