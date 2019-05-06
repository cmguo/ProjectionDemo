package com.hwl.media.widget;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.WindowManager;

import com.hwl.media.projection.ProjectionManager;
import com.ustc.base.debug.Log;

public class MirrorView extends ResizeableView {

    private static final String TAG = "MirrorView";

    private float mScreenWidth;
    private float mScreenHeight;
    private ProjectionManager mProjection;

    public MirrorView(Context context) {
        super(context);
    }

    public MirrorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setProjection(ProjectionManager projectionManager) {
        mProjection = projectionManager;
    }

    @Override
    protected void onLayoutChanged(Rect rect) {
        if (mScreenWidth == 0f) {
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            Point size = new Point();
            wm.getDefaultDisplay().getSize(size);
            mScreenWidth = size.x;
            mScreenHeight = size.y;
            Log.d(TAG, "onLayoutChanged mScreenSize=" + mScreenWidth + ":" + mScreenHeight);
        }
        if (mProjection != null) {
            float[] bound = {rect.left / mScreenWidth, rect.top / mScreenHeight,
                    rect.width() / mScreenWidth, rect.height() / mScreenHeight};
            mProjection.setBound(bound);
        }
    }

}
