package local.notificationpanel;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** Foreground-only approximate location; no coordinates or weather history written to disk. */
final class WeatherController {
    private final Activity activity;private final Runnable changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocationManager manager;private LocationListener listener;
    private boolean active,busy;private int generation;private long retryAt;
    private String problem="Нажмите, чтобы включить погоду по местоположению.";
    private static Double temperature;private static long fetchedAt;
    private static String place="Местоположение",coordinateLabel="";
    WeatherController(Activity a,Runnable changed){activity=a;this.changed=changed;manager=a.getSystemService(LocationManager.class);}
    boolean permitted(){return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    String label(){return permitted()&&temperature!=null&&SystemClock.elapsedRealtime()-fetchedAt<30*60000L?Math.round(temperature)+"°":"—°";}
    String placeLabel(){return !permitted()?"Геолокация":temperature!=null&&SystemClock.elapsedRealtime()-fetchedAt<30*60000L?place:"Местоположение";}
    private void resolvePlace(double lat,double lon,int token){
        if(!Geocoder.isPresent())return;
        Geocoder geocoder=new Geocoder(activity.getApplicationContext(),new Locale("ru"));
        if(Build.VERSION.SDK_INT>=33){
            try{geocoder.getFromLocation(lat,lon,1,new Geocoder.GeocodeListener(){
                public void onGeocode(List<Address> addresses){applyPlace(addresses,token);}
                public void onError(String error){}
            });}catch(Exception ignored){}
        }else new Thread(()->{try{applyPlace(geocoder.getFromLocation(lat,lon,1),token);}catch(Exception ignored){}},"panel-place").start();
    }
    static String nameFromAddresses(List<Address> addresses){
        if(addresses==null||addresses.isEmpty())return null;
        Address a=addresses.get(0);
        for(String name:new String[]{a.getLocality(),a.getSubAdminArea(),a.getAdminArea()})if(name!=null&&!name.trim().isEmpty())return name.trim();
        return null;
    }
    private void applyPlace(List<Address> addresses,int token){
        String name=nameFromAddresses(addresses);if(name==null)return;
        main.post(()->{if(token==generation&&active&&permitted()){place=name;changed.run();}});
    }
    void resume(){active=true;refresh(false);}
    void pause(){active=false;generation++;busy=false;stopLocation();main.removeCallbacksAndMessages(null);}
    void details(){
        String message=permitted()?(place+"\n"+coordinateLabel+"\n\n"+problem+"\n\nПогодные данные: Open-Meteo, CC BY 4.0. Название места определяет системная служба Android."):"Для погоды нужно приблизительное местоположение. Координаты, округлённые примерно до 1 км, передаются Open-Meteo только при открытой панели. Доступ в фоне не запрашивается.";
        new PanelDialog.Builder(activity).setTitle("Температура на улице").setMessage(message)
            .setNegativeButton("Закрыть",null).setNeutralButton("Open-Meteo",(d,w)->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/"))))
            .setPositiveButton(permitted()?"Обновить":"Разрешить",(d,w)->{
                if(permitted())refresh(true);else activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},420);
            }).show();
    }
    void refresh(boolean force){
        if(!active||busy||!permitted())return;
        long now=SystemClock.elapsedRealtime();
        if(!force&&(now<retryAt||(temperature!=null&&now-fetchedAt<15*60000L)))return;
        if(manager==null){problem="Геолокация недоступна.";return;}
        try{
            if(!manager.isLocationEnabled()){problem="Включите геолокацию телефона и нажмите «Обновить».";temperature=null;changed.run();return;}
            busy=true;retryAt=now+60000;final int token=++generation;
            List<String> providers=manager.getProviders(true);Location best=null;
            for(String p:providers)try{Location l=manager.getLastKnownLocation(p);if(l!=null&&l.hasAccuracy()&&l.getAccuracy()<=20000&&SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos()<=10L*60*1000000000&&(best==null||l.getElapsedRealtimeNanos()>best.getElapsedRealtimeNanos()))best=l;}catch(SecurityException ignored){}
            if(best!=null){fetch(best,token);return;}
            String provider=providers.contains("fused")?"fused":providers.contains(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:null;
            if(provider==null){fail("Не удалось получить приблизительное местоположение. Проверьте геолокацию телефона.",token);return;}
            listener=new LocationListener(){
                public void onLocationChanged(Location l){if(token!=generation||!active)return;if(l.hasAccuracy()&&l.getAccuracy()<=20000){stopLocation();fetch(l,token);}}
                public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}
            };
            manager.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());
            main.postDelayed(()->{if(token==generation&&listener!=null){stopLocation();fail("Местоположение пока недоступно. Попробуйте обновить погоду позже.",token);}},20000);
        }catch(Exception e){busy=false;stopLocation();problem="Не удалось получить местоположение. Проверьте разрешение и геолокацию.";changed.run();}
    }
    private void stopLocation(){if(listener!=null&&manager!=null)try{manager.removeUpdates(listener);}catch(Exception ignored){}listener=null;}
    private void fail(String message,int token){if(token!=generation||!active)return;busy=false;problem=message;changed.run();}
    private void fetch(Location location,int token){
        final double lat=Math.round(location.getLatitude()*100)/100.0,lon=Math.round(location.getLongitude()*100)/100.0;
        new Thread(()->{try{
            String url=String.format(Locale.ROOT,"https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m&temperature_unit=celsius",lat,lon);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();UpdateCore.fetch(url,bytes,65536);
            JSONObject j=new JSONObject(bytes.toString("UTF-8"));double value=j.getJSONObject("current").getDouble("temperature_2m");
            if(!Double.isFinite(value)||value < -100||value > 70)throw new IOException("Invalid temperature");
            main.post(()->{if(token!=generation||!active||!permitted())return;busy=false;temperature=value;fetchedAt=SystemClock.elapsedRealtime();coordinateLabel=String.format(Locale.ROOT,"%.2f, %.2f",lat,lon);place=coordinateLabel;resolvePlace(lat,lon,token);problem="Погодная оценка Open-Meteo по вашему местоположению. Обновлено в "+android.text.format.DateFormat.format("HH:mm",new Date())+". Данные погодной модели, не датчика телефона.";changed.run();});
        }catch(Exception e){main.post(()->fail("Не удалось загрузить погоду. Проверьте интернет и повторите попытку.",token));}},"panel-weather").start();
    }
}
