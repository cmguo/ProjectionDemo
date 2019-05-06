package com.hwl.media.projection;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;

import com.ustc.base.debug.Dumpper;
import com.ustc.base.debug.Log;
import com.ustc.base.util.thread.WorkThread;
import com.ustc.opengl.egl.GLRenderer;
import com.ustc.opengl.gl.GLTexture;

import javax.microedition.khronos.opengles.GL10;

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter;

public class GLDisplayRenderer extends GLRenderer
        implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "GLDisplayRenderer";
    
    private static WorkThread sWorkThread = new WorkThread(TAG);
    
    //private int mSTMMatrixHandle;

    private SurfaceTexture mMirrorSurfaceTexture;
    private SurfaceTexture mPresentSurfaceTexture;

    private int mMirrorInFrames;
    private int mMirrorOutFrames;
    private int mPresentInFrames;
    private int mPresentOutFrames;
    private long mRendered;
    //private float[] mSTMatrix = new float[16];

    private GLTexture mMirrorTexture;
    private GLTexture mPresentTexture;
    private GPUImageDisplayFilter mFilter;

    public GLDisplayRenderer() {
        GPUImageDisplayFilter.setVideoTexture(true);
        mFilter = new GPUImageDisplayFilter();
    }

    public void setBound(float[] bound) {
       mFilter.setBound(bound);
    }

    public long getTotalFrame() {
        return mRendered;
    }

    @Override
    @SuppressLint("InlinedApi")
    public boolean initDraw(GL10 gl) {
        Log.d(TAG, "initDraw");
        
        //mSTMMatrixHandle = mProgram.getUniform("uSTMatrix");

        if (mPresentTexture != null)
            return true;

        mPresentTexture = new GLTexture();
        mPresentTexture.setOESImage();

        mMirrorTexture = new GLTexture();
        mMirrorTexture.setOESImage();

        setFilter(mFilter);

        useLastTexture(mPresentTexture);
        mFilter.setTexture(mPresentTexture.id());

        // stand-alone work thread
        Runnable work = new Runnable() {
            @Override
            public synchronized void run() {
                mPresentSurfaceTexture = new SurfaceTexture(mPresentTexture.id());
                mMirrorSurfaceTexture = new SurfaceTexture(mMirrorTexture.id());
                notify();
            }
        };
        synchronized (work) {
            sWorkThread.post(work);
            try {
                work.wait();
            } catch (InterruptedException e) {
            }
        }
        mMirrorSurfaceTexture.setOnFrameAvailableListener(this);
        mPresentSurfaceTexture.setOnFrameAvailableListener(this);

        onTextureReady(mMirrorSurfaceTexture, mPresentSurfaceTexture);
        
        return true;
    }
    
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged width: " + width + " height: " + height);
        super.onSurfaceChanged(gl, width, height);
    }
    
    protected void onTextureReady(SurfaceTexture mirrorTexture, SurfaceTexture presentTexture) {
    }

    @Override
    protected boolean prepareDraw(GL10 gl) {
        if (mPresentInFrames == mPresentOutFrames && mMirrorInFrames == mMirrorOutFrames) {
            return true;
        }
        if (mPresentInFrames != mPresentOutFrames) {
            mPresentOutFrames = mPresentInFrames;
            mPresentSurfaceTexture.updateTexImage();
        }
        if (mMirrorInFrames != mMirrorOutFrames) {
            mMirrorOutFrames = mMirrorInFrames;
            mMirrorSurfaceTexture.updateTexImage();
        }
        return true;
    }
    
    @Override
    @SuppressLint("InlinedApi")
    protected void draw(GL10 gl) {
        //mSurfaceTexture.getTransformMatrix(mSTMatrix);
        //glUniformMatrix4fv(mSTMMatrixHandle, 1, false, mSTMatrix, 0);
        drawTexture(gl, mMirrorTexture);
        ++mRendered;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (surfaceTexture == mMirrorSurfaceTexture)
            ++mMirrorInFrames;
        else
            ++mPresentInFrames;
        invalidate();
    }
    
    @Override
    public void dump(Dumpper dumpper) {
        super.dump(dumpper);
        dumpper.dump("mMirrorInFrames", mMirrorInFrames);
        dumpper.dump("mPresentInFrames", mPresentInFrames);
        dumpper.dump("mMirrorOutFrames", mMirrorOutFrames);
        dumpper.dump("mPresentOutFrames", mPresentOutFrames);
        dumpper.dump("mMirrorTexture", mMirrorTexture);
        dumpper.dump("mPresentTexture", mPresentTexture);
        dumpper.dump("mMirrorSurfaceTexture", mMirrorSurfaceTexture);
        dumpper.dump("mPresentSurfaceTexture", mPresentSurfaceTexture);
    }

}
