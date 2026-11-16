package local.notificationpanel;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.provider.Settings;import android.view.*;import android.widget.Toast;
/** Opt-in left-edge launcher. Does not replace or disable SystemUI. */
public final class EdgePanelService extends Service {
 static String lastError="";static EdgePanelService instance;static int visibleActivities;private WindowManager wm;private View handle;private boolean registered;
 private final Handler visibilityHandler=new Handler(Looper.getMainLooper());
 private boolean destroyed;
 // Screen/user-present broadcasts can arrive before Keyguard finishes dismissing.
 // Reconcile while interactive, without wake locks or waking a sleeping device.
 private final Runnable reconcileVisibility=new Runnable(){public void run(){
  if(destroyed||handle==null)return;
  updateVisibility();
  if(getSystemService(PowerManager.class).isInteractive())visibilityHandler.postDelayed(this,1000);
 }};
 private void refreshVisibility(){
  visibilityHandler.removeCallbacks(reconcileVisibility);
  if(!destroyed)reconcileVisibility.run();
 }
 static final String STOP="local.notificationpanel.STOP_EDGE";
 static void activityVisible(boolean visible){visibleActivities=Math.max(0,visibleActivities+(visible?1:-1));if(instance!=null)instance.updateVisibility();}
 static boolean running(){return instance!=null;}
 static boolean enabled(Context c){return c.getSharedPreferences("panel_settings",0).getBoolean("edge_enabled",false);}
 static void stop(Context c){c.getSharedPreferences("panel_settings",0).edit().putBoolean("edge_enabled",false).putBoolean("edge_pending",false).apply();c.stopService(new Intent(c,EdgePanelService.class));}
 static void restore(Context c){if(enabled(c)&&!running()&&Settings.canDrawOverlays(c)){try{c.startForegroundService(new Intent(c,EdgePanelService.class));}catch(RuntimeException e){lastError="Не удалось восстановить полоску: "+e.getClass().getSimpleName();}}}
 private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){visibilityHandler.removeCallbacks(reconcileVisibility);if(handle!=null)handle.setVisibility(View.GONE);}else refreshVisibility();}};
 public IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null&&!enabled(this)){stopSelf();return START_NOT_STICKY;}
  if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
  try{
   NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("edge_panel","Боковая панель",NotificationManager.IMPORTANCE_LOW));
   PendingIntent open=PendingIntent.getActivity(this,900,new Intent(this,PanelActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=new Notification.Builder(this,"edge_panel").setSmallIcon(R.drawable.ic_panel).setContentTitle("Панель у левого края включена").setContentText("Открыть Шторку").setOngoing(true).setContentIntent(open).build();
   if(Build.VERSION.SDK_INT>=34)startForeground(900,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(900,n);
   if(handle==null)attach();instance=this;getSharedPreferences("panel_settings",0).edit().putBoolean("edge_enabled",true).apply();lastError="";refreshVisibility();
  }catch(RuntimeException e){lastError="Ошибка боковой панели: "+e.getClass().getSimpleName();Toast.makeText(this,"Не удалось включить боковую панель. Проверьте разрешение поверх приложений.",Toast.LENGTH_LONG).show();stopSelf();return START_NOT_STICKY;}
  return START_STICKY;
 }
 private void attach(){wm=getSystemService(WindowManager.class);handle=new View(this){final Paint paint=new Paint(3);protected void onDraw(Canvas c){paint.setColor(0xbfc7e4ff);int barHeight=dp(264f/3.5f);float top=(getHeight()-barHeight)/2f;c.drawRoundRect(dp(4),top,dp(8),top+barHeight,dp(2),dp(2),paint);}};
  handle.setContentDescription("Открыть панель уведомлений. Проведите вправо или нажмите.");handle.setClickable(true);handle.setOnClickListener(v->openPanel());
  handle.setOnTouchListener(new View.OnTouchListener(){float x,y;boolean opened;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();opened=false;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!opened&&e.getRawX()-x>dp(36)&&e.getRawX()-x>Math.abs(e.getRawY()-y)){opened=true;v.cancelLongPress();openPanel();return true;}if(opened)return true;return false;}});
  // Mirror the measured OxygenOS region: 74 x 266 px at density 3.5.
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(dp(74f/3.5f),dp(76),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.LEFT;lp.y=edgeOffset();wm.addView(handle,lp);
  IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);registered=true;
 }
 private int edgeOffset(){
  int height,top;
  if(Build.VERSION.SDK_INT>=30){android.view.WindowMetrics metrics=wm.getMaximumWindowMetrics();height=metrics.getBounds().height();top=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()|WindowInsets.Type.displayCutout()).top;}
  else{android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(metrics);height=metrics.heightPixels;int id=getResources().getIdentifier("status_bar_height","dimen","android");top=id==0?0:getResources().getDimensionPixelSize(id);}
  // Match the right trigger's screen-space top: 266 px on the 2640 px reference.
  return Math.max(0,Math.round(height*(266f/2640f))-top);
 }
 @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(handle!=null){WindowManager.LayoutParams lp=(WindowManager.LayoutParams)handle.getLayoutParams();lp.y=edgeOffset();wm.updateViewLayout(handle,lp);}}
 private boolean unlocked(){return !getSystemService(KeyguardManager.class).isKeyguardLocked()&&getSystemService(PowerManager.class).isInteractive();}
 private void updateVisibility(){if(handle!=null)handle.setVisibility(visibleActivities==0&&unlocked()?View.VISIBLE:View.GONE);}
 private void openPanel(){if(!unlocked())return;try{startActivity(new Intent(this,OverlayPanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){Toast.makeText(this,"Android не разрешил открыть панель",Toast.LENGTH_SHORT).show();}}
 public void onDestroy(){destroyed=true;visibilityHandler.removeCallbacks(reconcileVisibility);if(registered)unregisterReceiver(screen);if(handle!=null){try{wm.removeView(handle);}catch(RuntimeException ignored){}handle=null;}if(instance==this)instance=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
