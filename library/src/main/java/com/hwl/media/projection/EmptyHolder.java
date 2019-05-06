package com.hwl.media.projection;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceHolder;

public class EmptyHolder implements SurfaceHolder {
    
    public boolean isCreating() {
        return false;
    }

    public Surface getSurface() {
        return null;
    }
    
    @Override
    public void addCallback(Callback callback) {
    }
    
    @Override
    public void removeCallback(Callback callback) {
    }

    public void setKeepScreenOn(boolean screenOn) {
    }

    @Override
    public void setFixedSize(int width, int height) {
    }

    @Override
    public void setSizeFromLayout() {
    }

    @Override
    public void setType(int type) {
    }
    
    @Override
    public void setFormat(int format) {
    }

    @Override
    public Rect getSurfaceFrame() {
        return null;
    }
    
    @Override
    public Canvas lockCanvas() {
        return null;
    }
    
    @Override
    public Canvas lockCanvas(Rect rect) {
        return null;
    }
    
    @Override
    public void unlockCanvasAndPost(Canvas canvas) {
    }

}