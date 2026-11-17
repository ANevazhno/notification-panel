package local.notificationpanel;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.media.session.MediaController;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Visible-only media observer. Never starts playback without an explicit tap. */
final class MusicCard extends LinearLayout {
    private final Activity activity;
    private final MediaSessionManager manager;
    private final ComponentName listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<MediaController,MediaController.Callback> watched=new LinkedHashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions=this::bind;
    private final Runnable notifications=this::update;
    private static final MediaDismissal dismissals=new MediaDismissal();
    private MediaController selected;
    private boolean started;
    private final ImageView artwork;
    private final TextView title,subtitle;
    private final ImageButton previous,toggle,next;
    MusicCard(Activity activity){
        super(activity);this.activity=activity;
        manager=activity.getSystemService(MediaSessionManager.class);
        listener=new ComponentName(activity,NotificationBridge.class);
        setOrientation(VERTICAL);setPadding(dp(12),dp(10),dp(12),dp(6));
        GradientDrawable background=new GradientDrawable();background.setColor(PanelColors.SURFACE);background.setCornerRadius(dp(16));setBackground(background);
        LinearLayout info=new LinearLayout(activity);info.setGravity(Gravity.CENTER_VERTICAL);
        artwork=new ImageView(activity);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setContentDescription("Открыть плеер");artwork.setFocusable(true);artwork.setOnClickListener(v->open());
        GradientDrawable clip=new GradientDrawable();clip.setColor(PanelColors.BUTTON);clip.setCornerRadius(dp(10));artwork.setBackground(clip);artwork.setClipToOutline(true);
        info.addView(artwork,new LayoutParams(dp(52),dp(52)));
        LinearLayout labels=new LinearLayout(activity);labels.setOrientation(VERTICAL);labels.setPadding(dp(12),0,0,0);
        title=label(15,PanelColors.TEXT);title.setTypeface(null,Typeface.BOLD);subtitle=label(13,PanelColors.MUTED);labels.addView(title);labels.addView(subtitle);info.addView(labels,new LayoutParams(0,-2,1));
        ImageButton close=control(5,"Скрыть музыкальную карточку",this::dismiss);info.addView(close,new LayoutParams(dp(48),dp(48)));addView(info);
        LinearLayout controls=new LinearLayout(activity);controls.setGravity(Gravity.CENTER);
        previous=control(0,"Предыдущий трек",()->command(0));toggle=control(1,"Продолжить",()->command(1));next=control(3,"Следующий трек",()->command(3));
        controls.addView(previous,new LayoutParams(dp(64),dp(48)));controls.addView(toggle,new LayoutParams(dp(80),dp(48)));controls.addView(next,new LayoutParams(dp(64),dp(48)));addView(controls);setVisibility(GONE);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(int size,int color){TextView v=new TextView(activity);v.setTextSize(size);v.setTextColor(color);v.setMaxLines(1);v.setEllipsize(TextUtils.TruncateAt.END);return v;}
    private ImageButton control(int icon,String description,Runnable action){ImageButton b=new ImageButton(activity);b.setImageDrawable(new Symbol(icon));b.setContentDescription(description);b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(PanelColors.RIPPLE),null,null));b.setOnClickListener(v->action.run());return b;}
    void start(){if(started||manager==null)return;started=true;try{manager.addOnActiveSessionsChangedListener(sessions,listener,main);NotificationBridge.watch(notifications);refresh();}catch(SecurityException e){stop();}}
    void stop(){started=false;NotificationBridge.unwatch(notifications);if(manager!=null)manager.removeOnActiveSessionsChangedListener(sessions);clear();selected=null;artwork.setImageDrawable(null);setVisibility(GONE);}
    private void clear(){for(Map.Entry<MediaController,MediaController.Callback> e:watched.entrySet())e.getKey().unregisterCallback(e.getValue());watched.clear();}
    private void refresh(){if(!started)return;try{bind(manager.getActiveSessions(listener));}catch(SecurityException e){stop();}}
    private void bind(List<MediaController> controllers){
        clear();Set<MediaSession.Token> active=new HashSet<>();if(controllers!=null)for(MediaController c:controllers)active.add(c.getSessionToken());if(!active.isEmpty())dismissals.retain(active);
        if(controllers!=null)for(MediaController c:controllers){MediaController.Callback callback=new MediaController.Callback(){
            @Override public void onPlaybackStateChanged(PlaybackState state){update();}
            @Override public void onMetadataChanged(MediaMetadata metadata){update();}
            @Override public void onSessionDestroyed(){main.post(()->refresh());}
        };c.registerCallback(callback,main);watched.put(c,callback);}update();
    }
    private static boolean running(PlaybackState state){if(state==null)return false;int s=state.getState();return s==PlaybackState.STATE_PLAYING||s==PlaybackState.STATE_BUFFERING||s==PlaybackState.STATE_CONNECTING||s==PlaybackState.STATE_FAST_FORWARDING||s==PlaybackState.STATE_REWINDING||s==PlaybackState.STATE_SKIPPING_TO_NEXT||s==PlaybackState.STATE_SKIPPING_TO_PREVIOUS||s==PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;}
    private static int rank(MediaController c){PlaybackState s=c.getPlaybackState();return running(s)?2:s!=null&&s.getState()==PlaybackState.STATE_PAUSED?1:0;}
    private void update(){
        KeyguardManager keyguard=activity.getSystemService(KeyguardManager.class);
        if(!started||keyguard==null||keyguard.isDeviceLocked()){setVisibility(GONE);return;}
        MediaController best=null;for(MediaController c:watched.keySet()){
            if(dismissals.isHidden(c.getSessionToken(),running(c.getPlaybackState())))continue;
            if(rank(c)>0&&NotificationBridge.hasMediaNotification(c)&&(best==null||rank(c)>rank(best)||(rank(c)==rank(best)&&selected!=null&&c.getSessionToken().equals(selected.getSessionToken()))))best=c;
        }
        selected=best;if(best==null){setVisibility(GONE);artwork.setImageDrawable(null);return;}
        MediaMetadata m=best.getMetadata();CharSequence name=m==null?null:m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);if(TextUtils.isEmpty(name)&&m!=null)name=m.getText(MediaMetadata.METADATA_KEY_TITLE);
        String app=best.getPackageName();try{app=activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(app,0)).toString();}catch(Exception ignored){}
        title.setText(TextUtils.isEmpty(name)?app:name);
        CharSequence artist=m==null?null:m.getText(MediaMetadata.METADATA_KEY_ARTIST);if(TextUtils.isEmpty(artist)&&m!=null)artist=m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);subtitle.setText(TextUtils.isEmpty(artist)?app:artist+" · "+app);
        Bitmap art=m==null?null:m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);if(art==null&&m!=null)art=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(art==null&&m!=null)art=m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if(art!=null)artwork.setImageBitmap(art);else artwork.setImageDrawable(new Symbol(4));
        PlaybackState state=best.getPlaybackState();long actions=state==null?0:state.getActions();boolean playing=running(state);
        enabled(previous,(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0);enabled(next,(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0);
        enabled(toggle,(actions&(PlaybackState.ACTION_PLAY_PAUSE|(playing?PlaybackState.ACTION_PAUSE:PlaybackState.ACTION_PLAY)))!=0);
        toggle.setImageDrawable(new Symbol(playing?2:1));toggle.setContentDescription(playing?"Пауза":"Продолжить");setVisibility(VISIBLE);
    }
    private void dismiss(){
        if(selected==null)return;
        dismissals.hide(selected.getSessionToken(),running(selected.getPlaybackState()));
        selected=null;artwork.setImageDrawable(null);setVisibility(GONE);
    }
    private void enabled(View v,boolean value){v.setEnabled(value);v.setAlpha(value?1f:.35f);}
    private void command(int command){
        MediaController target=selected;KeyguardManager k=activity.getSystemService(KeyguardManager.class);if(target==null||k==null||k.isDeviceLocked())return;
        try{PlaybackState state=target.getPlaybackState();if(state==null)return;long action=command==0?PlaybackState.ACTION_SKIP_TO_PREVIOUS:command==3?PlaybackState.ACTION_SKIP_TO_NEXT:running(state)?PlaybackState.ACTION_PAUSE:PlaybackState.ACTION_PLAY;
            if((state.getActions()&(action|(command==1?PlaybackState.ACTION_PLAY_PAUSE:0)))==0){update();return;}
            MediaController.TransportControls t=target.getTransportControls();if(command==0)t.skipToPrevious();else if(command==3)t.skipToNext();else if(running(state))t.pause();else t.play();
        }catch(RuntimeException e){Toast.makeText(activity,"Плеер недоступен",Toast.LENGTH_SHORT).show();refresh();}
    }
    private void open(){if(selected==null)return;try{PendingIntent intent=selected.getSessionActivity();if(intent!=null){intent.send();return;}Intent launch=activity.getPackageManager().getLaunchIntentForPackage(selected.getPackageName());if(launch!=null){activity.startActivity(launch);return;}}catch(Exception ignored){}Toast.makeText(activity,"Не удалось открыть плеер",Toast.LENGTH_SHORT).show();}
    private final class Symbol extends Drawable {
        private final int kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Symbol(int kind){this.kind=kind;paint.setColor(PanelColors.ACCENT);}
        @Override public void draw(Canvas c){android.graphics.Rect b=getBounds();c.save();c.translate(b.exactCenterX()-dp(12),b.exactCenterY()-dp(12));c.scale(dp(24)/24f,dp(24)/24f);
            if(kind==5){paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(2.2f);c.drawLine(6,6,18,18,paint);c.drawLine(18,6,6,18,paint);}else if(kind==2){c.drawRect(6,4,10,20,paint);c.drawRect(14,4,18,20,paint);}else if(kind==4){c.drawRect(10,4,12,17,paint);c.drawRect(12,4,19,7,paint);c.drawOval(4,14,12,21,paint);}else{if(kind==0){c.translate(24,0);c.scale(-1,1);}Path p=new Path();p.moveTo(7,4);p.lineTo(19,12);p.lineTo(7,20);p.close();c.drawPath(p,paint);if(kind==0||kind==3)c.drawRect(19,4,22,20,paint);}c.restore();}
        @Override public void setAlpha(int a){paint.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
