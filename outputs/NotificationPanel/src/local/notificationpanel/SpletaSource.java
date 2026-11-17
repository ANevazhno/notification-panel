package local.notificationpanel;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.webkit.CookieManager;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

/** Personal Spleta feed. Server owns recipient filtering and read state; no password is stored here. */
public final class SpletaSource {
    static final String ORIGIN="https://portal.skenergoresurs.ru", CHANNEL="spleta_personal";
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final Map<String,StatusBarNotification> items=new LinkedHashMap<>();
    private static final Map<String,String> signatures=new HashMap<>();
    private static final Map<String,Long> hidden=new HashMap<>();
    private static Context context;
    private static String account="",status="Не подключено";
    private static long generation,lastAttempt;
    private static boolean busy,pendingForce;
    private static final List<java.util.function.Consumer<Boolean>> pendingDone=new ArrayList<>();
    interface Transport { JSONObject request(String path,String method,String cookie) throws Exception; }
    private static Transport transport=SpletaSource::request;
    static void init(Context c){context=c.getApplicationContext();account=prefs().getString("account","");
        try{JSONObject h=new JSONObject(prefs().getString("hidden","{}"));Iterator<String> it=h.keys();while(it.hasNext()){String k=it.next();hidden.put(k,h.getLong(k));}}catch(Exception ignored){}
        status=enabled()?"Ожидаем синхронизации":"Не подключено";if(enabled())schedule();
    }
    private static SharedPreferences prefs(){return context.getSharedPreferences("spleta",0);}
    static boolean enabled(){return context!=null&&prefs().getBoolean("enabled",false);}
    static String status(){return status;}
    static boolean owns(StatusBarNotification s){return context!=null&&context.getPackageName().equals(s.getPackageName())&&CHANNEL.equals(s.getNotification().getChannelId())&&s.getTag()!=null&&s.getTag().startsWith("spleta:");}
    static String id(StatusBarNotification s){return s.getTag().substring(7);}
    static boolean current(StatusBarNotification s){return owns(s)&&items.get(id(s))==s&&!isHidden(id(s));}
    static List<StatusBarNotification> snapshot(){List<StatusBarNotification> result=new ArrayList<>();for(Map.Entry<String,StatusBarNotification> e:items.entrySet())if(!isHidden(e.getKey()))result.add(e.getValue());return result;}
    private static boolean isHidden(String id){return hidden.getOrDefault(id,0L)>System.currentTimeMillis();}
    static boolean hide(List<StatusBarNotification> source,long duration){
        for(StatusBarNotification s:source)if(!current(s))return false;
        for(StatusBarNotification s:source)hidden.put(id(s),duration>0?System.currentTimeMillis()+duration:Long.MAX_VALUE);
        saveHidden();NotificationBridge.sourceChanged();
        if(duration>0)main.postDelayed(NotificationBridge::sourceChanged,duration);return true;
    }
    private static void saveHidden(){prefs().edit().putString("hidden",new JSONObject(hidden).toString()).apply();}
    static void disconnect(){SpletaPushService.stop(context);generation++;pendingForce=false;List<java.util.function.Consumer<Boolean>> cancelled=new ArrayList<>(pendingDone);pendingDone.clear();for(java.util.function.Consumer<Boolean> done:cancelled)done.accept(false);items.clear();signatures.clear();hidden.clear();account="";prefs().edit().clear().apply();status="Не подключено";
        context.getSystemService(android.app.job.JobScheduler.class).cancel(731);
        context.getSystemService(android.app.job.JobScheduler.class).cancel(732);
        CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();NotificationBridge.sourceChanged();
    }
    static void connect(java.util.function.Consumer<Boolean> done){generation++;items.clear();signatures.clear();hidden.clear();account="";prefs().edit().putBoolean("enabled",true).remove("account").remove("hidden").apply();NotificationBridge.sourceChanged();sync(true,done);}
    static void schedule(){if(!enabled())return;android.app.job.JobScheduler jobs=context.getSystemService(android.app.job.JobScheduler.class);if(jobs.getPendingJob(731)!=null)return;jobs.schedule(new android.app.job.JobInfo.Builder(731,new ComponentName(context,SpletaSyncJob.class)).setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60000L).build());}
    static void scheduleSoon(){if(!enabled())return;android.app.job.JobScheduler jobs=context.getSystemService(android.app.job.JobScheduler.class);if(jobs.getPendingJob(732)==null)jobs.schedule(new android.app.job.JobInfo.Builder(732,new ComponentName(context,SpletaSyncJob.class)).setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY).build());}
    private static void drain(){
        if(!pendingForce||busy)return;
        pendingForce=false;List<java.util.function.Consumer<Boolean>> callbacks=new ArrayList<>(pendingDone);pendingDone.clear();
        sync(true,ok->{for(java.util.function.Consumer<Boolean> done:callbacks)done.accept(ok);});
    }
    static void sync(boolean force,java.util.function.Consumer<Boolean> done){
        if(!enabled()){if(done!=null)done.accept(false);return;}
        if(busy){if(force){pendingForce=true;if(done!=null)pendingDone.add(done);}else if(done!=null)done.accept(false);return;}
        if(!force&&SystemClock.elapsedRealtime()-lastAttempt<30000){if(done!=null)done.accept(false);return;}
        final String cookie=CookieManager.getInstance().getCookie(ORIGIN);final long token=generation;
        busy=true;lastAttempt=SystemClock.elapsedRealtime();status="Обновляем уведомления…";
        io.execute(()->{try{
            JSONObject user=transport.request("/api/auth/session","GET",cookie).optJSONObject("user");
            if(user==null||user.optString("id").isEmpty())throw new LoginRequired();
            String userId=user.getString("id");String name=user.optString("name",user.optString("email",""));
            LinkedHashMap<String,JSONObject> rows=fetchAll(cookie);
            main.post(()->{busy=false;if(token!=generation){try{if(done!=null)done.accept(false);}finally{drain();}return;}
                if(!account.equals(userId)){items.clear();signatures.clear();hidden.clear();}account=userId;
                apply(rows);prefs().edit().putString("account",account).apply();saveHidden();
                status=(name.isEmpty()?"Подключено":name)+" · "+rows.size()+" непрочитанных · "+android.text.format.DateFormat.format("HH:mm",new Date());
                schedule();NotificationBridge.sourceChanged();try{if(done!=null)done.accept(true);}finally{drain();}
            });
        }catch(Exception ex){main.post(()->{busy=false;if(token==generation){if(ex instanceof LoginRequired){items.clear();signatures.clear();status="Войдите в Spleta заново";}else status="Не удалось обновить. Проверьте сеть и повторите.";NotificationBridge.sourceChanged();}try{if(done!=null)done.accept(false);}finally{drain();}});}});
    }
    static LinkedHashMap<String,JSONObject> fetchAll(String cookie)throws Exception{
        LinkedHashMap<String,JSONObject> rows=new LinkedHashMap<>();Set<String> cursors=new HashSet<>();String cursor=null;
        for(int page=0;page<1000;page++){
            JSONObject response=transport.request("/api/notifications?tab=all"+(cursor==null?"":"&cursor="+Uri.encode(cursor)),"GET",cookie);
            JSONArray arr=response.getJSONArray("notifications");
            for(int i=0;i<arr.length();i++){JSONObject row=arr.getJSONObject(i);String id=row.getString("id");if(id.isEmpty())throw new IOException("Invalid ID");if(!row.has("readAt"))throw new IOException("Missing read state");java.time.Instant.parse(row.getString("createdAt"));if(row.isNull("readAt"))rows.put(id,row);}
            if(response.isNull("nextCursor"))return rows;cursor=response.optString("nextCursor","");if(cursor.isEmpty())return rows;
            if(!cursors.add(cursor))throw new IOException("Repeated cursor");
        }throw new IOException("Incomplete feed");
    }
    private static void apply(Map<String,JSONObject> rows){
        items.keySet().retainAll(rows.keySet());signatures.keySet().retainAll(rows.keySet());hidden.keySet().retainAll(rows.keySet());
        for(Map.Entry<String,JSONObject> row:rows.entrySet())try{
            String id=row.getKey(),signature=row.getValue().toString();if(signature.equals(signatures.get(id)))continue;
            JSONObject o=row.getValue();String url=safeUrl(o.isNull("targetUrl")?"/":o.optString("targetUrl","/"));
            Intent open=new Intent(context,SpletaActivity.class).setData(Uri.parse(url)).putExtra("open",true);
            PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            long time=java.time.Instant.parse(o.getString("createdAt")).toEpochMilli();
            Notification n=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_spleta).setContentTitle(o.optString("title","Spleta")).setContentText(o.optString("message","")).setStyle(new Notification.BigTextStyle().bigText(o.optString("message",""))).setContentIntent(pi).setWhen(time)
                .addAction(new Notification.Action.Builder(null,"Прочитать",pi).setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ).build()).build();
            StatusBarNotification s=new StatusBarNotification(context.getPackageName(),context.getPackageName(),0,"spleta:"+id,android.os.Process.myUid(),0,0,n,android.os.Process.myUserHandle(),time);
            items.put(id,s);signatures.put(id,signature);
        }catch(Exception ex){status="Некоторые уведомления имеют неподдерживаемый формат";}
    }
    static void read(StatusBarNotification s){
        if(!current(s)){ToastHelper.show(context,"Уведомление уже изменилось");return;}
        final String id=id(s),expected=account,cookie=CookieManager.getInstance().getCookie(ORIGIN);final long token=generation;
        io.execute(()->{try{
            JSONObject user=transport.request("/api/auth/session","GET",cookie).optJSONObject("user");
            if(user==null||!expected.equals(user.optString("id")))throw new LoginRequired();
            JSONObject result=transport.request("/api/notifications/"+Uri.encode(id),"PATCH",cookie);if(!result.optBoolean("ok"))throw new IOException();
            main.post(()->{if(token!=generation)return;items.remove(id);signatures.remove(id);hidden.remove(id);saveHidden();NotificationBridge.sourceChanged();sync(true,null);});
        }catch(Exception ex){main.post(()->{if(token==generation)ToastHelper.show(context,ex instanceof LoginRequired?"Войдите в Spleta заново":"Не удалось отметить прочитанным. Повторите действие.");});}});
    }
    static String safeUrl(String value){try{java.net.URI uri=java.net.URI.create(ORIGIN+"/").resolve(value);if("https".equals(uri.getScheme())&&"portal.skenergoresurs.ru".equalsIgnoreCase(uri.getHost())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443))return uri.toASCIIString();}catch(Exception ignored){}return ORIGIN+"/";}
    static boolean allowed(String value){return value!=null&&value.equals(safeUrl(value));}
    private static JSONObject request(String path,String method,String cookie)throws Exception{
        if(cookie==null||cookie.isEmpty())throw new LoginRequired();
        HttpsURLConnection c=(HttpsURLConnection)new java.net.URL(ORIGIN+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setUseCaches(false);c.setRequestMethod(method);
        c.setRequestProperty("Cookie",cookie);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Origin",ORIGIN);c.setRequestProperty("Referer",ORIGIN+"/");
        try{
            if("PATCH".equals(method)){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write("{\"read\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();if(code==401||code==403||(code>=300&&code<400))throw new LoginRequired();if(code!=200)throw new IOException("HTTP "+code);
            String type=c.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).contains("application/json"))throw new IOException("Not JSON");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>2097152)throw new IOException("Response too large");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
        }finally{c.disconnect();}
    }
    private static final class LoginRequired extends IOException{}
    private static final class ToastHelper {static void show(Context c,String s){android.widget.Toast.makeText(c,s,android.widget.Toast.LENGTH_LONG).show();}}
}
