package local.smstest;
import android.app.*;
import android.os.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import android.view.*;
import android.widget.*;

public final class SmsReadTest extends Activity {
 private TextView status,content;
 private boolean loading;
 protected void onCreate(Bundle state){super.onCreate(state);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);int pad=(int)(20*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
  root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(pad,bars.top+pad,pad,bars.bottom+pad);return i;});
  TextView title=new TextView(this);title.setText("Тест чтения SMS");title.setTextSize(24);root.addView(title);
  status=new TextView(this);status.setTextSize(16);status.setText("Покажет одно последнее SMS. Основное приложение «Сообщения» не меняется. Данные остаются на телефоне.");root.addView(status);
  Button read=new Button(this);read.setText("Прочитать последнее SMS");read.setOnClickListener(v->{if(checkSelfPermission(android.Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.READ_SMS},1);else read();});root.addView(read);
  ScrollView scroll=new ScrollView(this);content=new TextView(this);content.setTextSize(18);content.setPadding(0,pad,0,pad);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  Button close=new Button(this);close.setText("Очистить и закрыть");close.setOnClickListener(v->{content.setText("");finishAndRemoveTask();});root.addView(close);setContentView(root);
 }
 private void read(){if(loading)return;loading=true;status.setText("Чтение…");
  new Thread(()->{String result="",message;boolean hasBody=false;int count=0;
   try(Cursor c=getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,new String[]{"address","body","date"},null,null,"date DESC")){
    if(c!=null&&c.moveToFirst()){String body=c.getString(1);hasBody=body!=null&&!body.trim().isEmpty();count=1;result=c.getString(0)+"\n"+android.text.format.DateFormat.format("dd.MM.yyyy HH:mm",c.getLong(2))+"\n\n"+(body==null?"":body);message=hasBody?"Успешно: текст SMS прочитан напрямую.":"SMS найдено, но текст пуст.";}else message="Входящих SMS не найдено.";
    android.util.Log.i("SmsReadProbe","READ_OK rows="+count+" bodyPresent="+hasBody+" defaultSms="+Telephony.Sms.getDefaultSmsPackage(this));
   }catch(Exception e){message="Чтение недоступно: "+e.getClass().getSimpleName();android.util.Log.i("SmsReadProbe","READ_FAILED type="+e.getClass().getSimpleName());}
   final String shown=result,summary=message;runOnUiThread(()->{loading=false;if(!isFinishing()&&!isDestroyed()){status.setText(summary);content.setText(shown);}});
  },"sms-read-test").start();
 }
 @Override public void onRequestPermissionsResult(int code,String[] p,int[] grants){super.onRequestPermissionsResult(code,p,grants);if(code==1){if(grants.length>0&&grants[0]==0)read();else{status.setText("Android не разрешил чтение SMS.");android.util.Log.i("SmsReadProbe","PERMISSION_DENIED");}}}
 @Override protected void onStop(){content.setText("");super.onStop();}
}
