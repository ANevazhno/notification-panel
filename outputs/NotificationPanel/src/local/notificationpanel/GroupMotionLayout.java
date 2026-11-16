package local.notificationpanel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;

/** A bounded reveal for a user-requested group change; never animates background updates. */
final class GroupMotionLayout extends FrameLayout {
    private int revealHeight, naturalHeight;
    private boolean pending = true;
    private ValueAnimator animator;
    private final boolean opening;
    private final Runnable begin = this::beginReveal;

    GroupMotionLayout(Context context, View content, int previousHeight, boolean opening) {
        super(context);
        this.opening = opening;
        revealHeight = previousHeight;
        setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        setClipChildren(true);
        addView(content, new FrameLayout.LayoutParams(-1, -2));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        // Measure content at its final size so text never reflows during the reveal.
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        naturalHeight = getMeasuredHeight();
        if (revealHeight >= 0) setMeasuredDimension(getMeasuredWidth(), revealHeight);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pending) post(begin);
    }

    private void beginReveal() {
        pending = false;
        if (!isAttachedToWindow() || naturalHeight <= 0 || !ValueAnimator.areAnimatorsEnabled()) {
            finishReveal();
            return;
        }
        animator = ValueAnimator.ofInt(revealHeight, naturalHeight);
        animator.setDuration(opening ? 260 : 190);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(a -> {
            revealHeight = (Integer) a.getAnimatedValue();
            requestLayout();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { finishReveal(); }
        });
        animator.start();
    }

    private void finishReveal() {
        revealHeight = -1;
        requestLayout();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(begin);
        pending = false;
        if (animator != null) animator.cancel();
        finishReveal();
        super.onDetachedFromWindow();
    }
}
