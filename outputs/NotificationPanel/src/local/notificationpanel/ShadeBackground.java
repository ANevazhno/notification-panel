package local.notificationpanel;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Lightweight ambient surface; no wallpaper capture or bitmap allocation. */
public final class ShadeBackground extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    @Override public void draw(Canvas c){
        float w=getBounds().width(),h=getBounds().height();
        paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xff18283d,0xff101a2b,0xff172a32},null,Shader.TileMode.CLAMP));c.drawRect(getBounds(),paint);
        glow(c,w*.9f,h*.05f,w*.95f,0xff33577a);
        glow(c,-w*.05f,h*.62f,w*1.1f,0xff174c55);
        glow(c,w*1.05f,h*.96f,w*.85f,0xff343349);
        paint.setShader(null);
    }
    private void glow(Canvas c,float x,float y,float r,int color){paint.setShader(new RadialGradient(x,y,r,new int[]{color,color&0x00ffffff},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,paint);}
    @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
