package com.hwl.media.widget;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.ustc.base.view.ViewUtils;

public class ResizeableView extends View {

    private static int[][] mDragCorners = new int[][] {
            new int[] {1, 1, 0, 0},
            new int[] {0, 1, 1, 0},
            new int[] {0, 0, 1, 1},
            new int[] {1, 0, 0, 1},
            new int[] {1, 0, 0, 0},
            new int[] {0, 1, 0, 0},
            new int[] {0, 0, 1, 0},
            new int[] {0, 0, 0, 1},
            new int[] {1, 1, 1, 1},
    };

    private Rect mRect = new Rect();
    private int mDragCorner = -1;
    private Point mDragOrigin = new Point();
    private Rect mDragRect0 = new Rect();
    private Rect mDragRect = new Rect();

    public ResizeableView(Context context) {
        super(context);
    }

    public ResizeableView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResizeableView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResizeableView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void onLayoutChanged(Rect rect) {
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            ViewUtils.getPositionOnScreen(this, mRect);
            onLayoutChanged(mRect);
        }
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
    }

    private static float distance(float x1, float x2) {
        return x2 > x1 ? x2 - x1 : x1 - x2;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float x = event.getRawX();
        float y = event.getRawY();
        if (action == MotionEvent.ACTION_DOWN) {
            mDragOrigin.set((int) x, (int) y);
            ViewUtils.getPosition(this, mDragRect0);
            if (distance(mRect.left, mRect.top, x, y) < 1024)
                mDragCorner = 0;
            else if (distance(mRect.right, mRect.top, x, y) < 1024)
                mDragCorner = 1;
            else if (distance(mRect.right, mRect.bottom, x, y) < 1024)
                mDragCorner = 2;
            else if (distance(mRect.left, mRect.bottom, x, y) < 1024)
                mDragCorner = 3;
            else if (distance(mRect.left, x) < 32)
                mDragCorner = 4;
            else if (distance(mRect.top, y) < 32)
                mDragCorner = 5;
            else if (distance(mRect.right, x) < 32)
                mDragCorner = 6;
            else if (distance(mRect.bottom, y) < 32)
                mDragCorner = 7;
            else
                mDragCorner = 8;
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mDragCorner >= 0) {
                int dx = (int) x - mDragOrigin.x;
                int dy = (int) y - mDragOrigin.y;
                int[] cn = mDragCorners[mDragCorner];
                mDragRect.set(mDragRect0.left + cn[0] * dx,
                        mDragRect0.top + cn[1] * dy,
                        mDragRect0.right + cn[2] * dx,
                        mDragRect0.bottom + cn[3] * dy);
                ViewUtils.setPosition(this, mDragRect);
                return true;
            }
        } else {
            if (mDragCorner >= 0) {
                mDragCorner = -1;
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

}
