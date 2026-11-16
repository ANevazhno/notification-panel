package local.notificationpanel;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.*;
import java.util.function.Consumer;

/** Compositor blur of the underlying window; no screenshots or bitmap copies. */
final class PanelBackdrop {
    private final Activity activity;
    private final View surface;
    private final Consumer<Boolean> listener=this::apply;
    private boolean registered;
    PanelBackdrop(Activity activity,View surface){this.activity=activity;this.surface=surface;}
    void attach(){
        if(Build.VERSION.SDK_INT<31){apply(false);return;}
        WindowManager manager=activity.getWindowManager();
        try{manager.addCrossWindowBlurEnabledListener(activity.getMainExecutor(),listener);registered=true;apply(manager.isCrossWindowBlurEnabled());}
        catch(RuntimeException unavailable){apply(false);}
    }
    private void apply(boolean enabled){
        if(activity.isDestroyed())return;
        Window window=activity.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if(Build.VERSION.SDK_INT>=31){
            WindowManager.LayoutParams params=window.getAttributes();
            if(enabled)params.flags|=WindowManager.LayoutParams.FLAG_BLUR_BEHIND;else params.flags&=~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            params.setBlurBehindRadius(enabled?120:0);window.setAttributes(params);
        }
        // Keep small header labels legible even above a white underlying app.
        surface.setBackground(enabled?new ColorDrawable(0xcc18283d):new ShadeBackground());
        window.setStatusBarColor(Color.TRANSPARENT);window.setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=29){window.setStatusBarContrastEnforced(false);window.setNavigationBarContrastEnforced(false);}
    }
    void detach(){if(Build.VERSION.SDK_INT>=31&&registered){activity.getWindowManager().removeCrossWindowBlurEnabledListener(listener);registered=false;}}
}
