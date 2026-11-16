package local.notificationpanel;
import android.content.Context;
import android.view.*;
import android.widget.LinearLayout;

/** Intercepts deliberate leftward drags; leaves taps and vertical scrolling to children. */
public final class SwipeDismissLayout extends LinearLayout {
    private float startX,startY,dx;
    private boolean dragging,vertical,eligible;
    private View gestureRegion;
    private VelocityTracker velocity;
    public void setGestureRegion(View region){gestureRegion=region;}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            startX=e.getRawX();startY=e.getRawY();dx=0;dragging=false;vertical=false;eligible=true;
            if(gestureRegion!=null){int[] xy=new int[2];gestureRegion.getLocationOnScreen(xy);eligible=startY>=xy[1]&&startY<xy[1]+gestureRegion.getHeight();}
            if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();
            if(!finishing){animate().cancel();setTranslationX(0);setAlpha(1);}
            if(eligible)getParent().requestDisallowInterceptTouchEvent(true);
        }
        if(velocity!=null){MotionEvent raw=MotionEvent.obtain(e);raw.offsetLocation(e.getRawX()-e.getX(),e.getRawY()-e.getY());velocity.addMovement(raw);raw.recycle();}
        boolean handled=super.dispatchTouchEvent(e);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            getParent().requestDisallowInterceptTouchEvent(false);
            if(velocity!=null){velocity.recycle();velocity=null;}
        }
        return handled;
    }
    private final int slop;
    private final java.util.function.BooleanSupplier dismiss;
    private boolean finishing;
    private android.animation.ValueAnimator collapse;
    public SwipeDismissLayout(Context c,java.util.function.BooleanSupplier dismiss){super(c);setOrientation(VERTICAL);setClickable(true);this.dismiss=dismiss;slop=ViewConfiguration.get(c).getScaledTouchSlop();}
    public boolean onInterceptTouchEvent(MotionEvent e){
        if(finishing)return true;
        if(!eligible)return false;
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!vertical){
            float x=e.getRawX()-startX,y=e.getRawY()-startY;
            if(Math.abs(y)>slop&&Math.abs(y)>Math.abs(x)*1.2f){vertical=true;getParent().requestDisallowInterceptTouchEvent(false);return false;}
            if(x<-slop&&Math.abs(x)>Math.abs(y)*1.15f){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
        }
        return dragging;
    }
    public boolean onTouchEvent(MotionEvent e){
        if(finishing)return true;
        if(!dragging&&e.getActionMasked()==MotionEvent.ACTION_MOVE)onInterceptTouchEvent(e);
        if(!dragging)return super.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){dx=Math.min(0,e.getRawX()-startX);setTranslationX(dx);setAlpha(Math.max(.35f,1-Math.abs(dx)/Math.max(1,getWidth())));return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            dx=Math.min(0,e.getRawX()-startX);
            float density=getResources().getDisplayMetrics().density;
            if(velocity!=null)velocity.computeCurrentVelocity(1000);
            boolean fling=velocity!=null&&velocity.getXVelocity()<-700*density&&Math.abs(velocity.getXVelocity())>Math.abs(velocity.getYVelocity())*1.2f&&-dx>=32*density;
            boolean remove=e.getActionMasked()==MotionEvent.ACTION_UP&&(-dx>=Math.max(56*density,getWidth()*.24f)||fling);
            dragging=false;getParent().requestDisallowInterceptTouchEvent(false);
            if(remove){
                finishing=true;performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                final int height=getHeight();
                animate().translationX(-getWidth()).alpha(0).setDuration(200)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->{
                        if(!isAttachedToWindow())return;
                        collapse=android.animation.ValueAnimator.ofInt(height,0);collapse.setDuration(180);
                        collapse.setInterpolator(new android.view.animation.DecelerateInterpolator());
                        collapse.addUpdateListener(a->{getLayoutParams().height=(Integer)a.getAnimatedValue();requestLayout();});
                        collapse.addListener(new android.animation.AnimatorListenerAdapter(){
                            private boolean cancelled;
                            public void onAnimationCancel(android.animation.Animator a){cancelled=true;}
                            public void onAnimationEnd(android.animation.Animator a){
                                if(cancelled||!isAttachedToWindow())return;
                                if(!dismiss.getAsBoolean()){getLayoutParams().height=android.view.ViewGroup.LayoutParams.WRAP_CONTENT;setTranslationX(0);setAlpha(1);finishing=false;requestLayout();}
                            }
                        });collapse.start();
                    }).start();
            }else animate().translationX(0).alpha(1).setDuration(240).setInterpolator(new android.view.animation.OvershootInterpolator(.65f)).start();
            return true;
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){animate().cancel();if(collapse!=null)collapse.cancel();super.onDetachedFromWindow();}
}


