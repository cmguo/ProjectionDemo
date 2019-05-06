package com.hwl.media.projection;

import android.view.Surface;

import com.ustc.base.debug.Dumpable;
import com.ustc.base.debug.Dumpper;
import com.ustc.base.util.reflect.ClassWrapper;
import com.ustc.base.util.reflect.ObjectWrapper;

public  class BindingHolder extends SimpleHolder
        implements Dumpable {

    private ObjectWrapper<Surface> mSurface =
            ObjectWrapper.wrap(ClassWrapper.wrap(Surface.class).newInstance());

    
    public BindingHolder() {
    }

    public boolean isValid() {
        return mSurface.getObject().isValid();
    }

    public void transferFrom(Surface surface) {
        mSurface.invoke("transferFrom",surface);
    }

    public void releaseSurface() {
        mSurface.invoke("release");
    }

    @Override
    public Surface getSurface() {
        return mSurface.getObject();
    }
    
    @Override
    public void dump(Dumpper dumpper) {
        super.dump(dumpper);
        dumpper.dump("mSurface", mSurface);
    }
}
