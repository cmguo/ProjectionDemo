package com.hwl.media.projection;

import android.view.SurfaceHolder;

import com.ustc.base.debug.Dumpable;
import com.ustc.base.debug.Dumpper;
import com.ustc.base.debug.Log;

import java.util.ArrayList;
import java.util.List;

public class SimpleHolder extends EmptyHolder 
        implements SurfaceHolder.Callback, SurfaceHolder, Dumpable {
    
    private static final String TAG = "SimpleHolder";
    
    private List<Callback> mCallbacks = new ArrayList<Callback>();

    @Override
    public void addCallback(Callback callback) {
        synchronized (mCallbacks) {
            if (mCallbacks.contains(callback) == false) {
                mCallbacks.add(callback);
            }
        }
    }

    @Override
    public void removeCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }
    
    protected void clearCallbacks() {
        synchronized (mCallbacks) {
            mCallbacks.clear();
        }
    }
    
    protected Callback[] getSurfaceCallbacks() {
        Callback callbacks[];
        synchronized (mCallbacks) {
            callbacks = new Callback[mCallbacks.size()];
            mCallbacks.toArray(callbacks);
        }
        return callbacks;
    }

    public void onSurfaceCreated() {
        Callback callbacks[] = getSurfaceCallbacks();
        for (Callback c : callbacks) {
            try {
                c.surfaceCreated(this);
            } catch (Throwable e) {
                Log.w(TAG, "onSurfaceCreated", e);
            }
        }
    }

    public void onSurfaceChanged(int format, int width, int height) {
        Callback callbacks[] = getSurfaceCallbacks();
        for (Callback c : callbacks) {
            try {
                c.surfaceChanged(this, format, width, height);
            } catch (Throwable e) {
                Log.w(TAG, "onSurfaceChanged", e);
            }
        }
    }

    public void onSurfaceDestroyed() {
        Callback callbacks[] = getSurfaceCallbacks();
        for (Callback c : callbacks) {
            try {
                c.surfaceDestroyed(this);
            } catch (Throwable e) {
                Log.w(TAG, "onSurfaceDestroyed", e);
            }
        }
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        onSurfaceCreated();
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        onSurfaceChanged(format, width, height);
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        onSurfaceDestroyed();
    }

    @Override
    public void dump(Dumpper dumpper) {
        dumpper.dump("mCallbacks", mCallbacks);
    }
    
}
