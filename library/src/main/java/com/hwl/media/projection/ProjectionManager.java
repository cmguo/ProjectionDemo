package com.hwl.media.projection;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.hwl.media.remote.ScreenRecord;
import com.ustc.base.debug.Console;
import com.ustc.base.debug.Log;
import com.ustc.base.debug.tools.HttpManager;
import com.ustc.base.util.stream.Streams;
import com.ustc.opengl.egl.GLThread;

import java.io.IOException;


public class ProjectionManager {

    public final static int REQUEST_DISPLAY_WIDTH = 1920;
    public final static int REQUEST_DISPLAY_HEIGHT = 1220;

    private final static String TAG = "ProjectionScreenManager";

    private Context mContext;

    private VirtualDisplay mPresentationDisplay;
    private VirtualDisplay mMirrorDisplay;
    private DisplayMetrics mMetrics;
    private MediaProjectionManager mMediaManager;
    private BindingHolder mBindingHolder;
    private MediaProjection mMediaProjection;
    private DisplayManager mDisplayManager;
    private GLDisplayRenderer nRenderer;
    private EncodeStream mEncodeStream;
    private Thread mEncodeThread;
    private String mPublishUrl;
    private GLThread mGLThread;

    public enum ProjectionMode {
        NONE,
        MIRROR,
        PRESENTATION,
        GLCOMPOUIND
    }

    private ProjectionMode mProjectionMode = ProjectionMode.NONE;

    public ProjectionManager(Context context) {
        this.mContext = context;
        // init global variable
        init();
    }

    public Display getPresentationDisplay() {
        return mPresentationDisplay == null ? null : mPresentationDisplay.getDisplay();
    }

    public String getPulishUrl() {
        return mPublishUrl;
    }

    private long mLastTime = 0;
    private long mLastFrame = 0;

    public double FPS(long now)
    {
        if (nRenderer == null || mLastTime == 0) {
            mLastTime = now;
            return 0.0;
        }
        long frame = nRenderer.getTotalFrame();
        double fps = (double)(frame - mLastFrame) * 1000 / (now - mLastTime);
        mLastFrame = frame;
        mLastTime = now;
        return fps;
    }

    public void setBound(float[] bound) {
        if (nRenderer != null) {
            nRenderer.setBound(bound);
        }
    }

    private void init() {

        EncoderDebugger.asyncDebug(mContext, ProjectionManager.REQUEST_DISPLAY_WIDTH,
                ProjectionManager.REQUEST_DISPLAY_HEIGHT);

        mMetrics = Resources.getSystem().getDisplayMetrics();
        // media Prejection service for mirror
        mMediaManager = (MediaProjectionManager) mContext.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        // create presentation virtual display
        mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);

        //
        //MainActivity.mBoardService = new BoardService(mContext);

        // register encoder
        // after http connection create
        Console.getInstance().registerCommand("screen_record",
                new ScreenRecord.Command(mContext, this));
        mPublishUrl = "http://" + NetworkMonitor.getHostIP() + ":"
                + HttpManager.getInstance(mContext).getPort() + "/screen_record.h264";
        Log.d(TAG, "init mPublishUrl=" + mPublishUrl);
    }

    public void setProjectionMode(ProjectionMode mode) {
        switch (mode) {
            case NONE:
                stopProjection();
                break;
            case MIRROR:
                startMirrorProjectionScreen();
                break;
            case GLCOMPOUIND:
                startCompoundProjectionScreen();
                break;
            case PRESENTATION:
                startPresentation();
                break;
        }
    }

    public void setMirrorProjection(int resultCode, @Nullable Intent data) {
        mMediaProjection = mMediaManager.getMediaProjection(resultCode, data);
    }

    public void startProjection(final Console.StreamTunnel tunnel) {
        mEncodeStream = new EncodeStream(mContext);
        mEncodeStream.init(REQUEST_DISPLAY_WIDTH, REQUEST_DISPLAY_HEIGHT);
        setInputSurface(mEncodeStream.getInputSurface());
        mEncodeStream.start();
        mEncodeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mEncodeStream.bumpSamples(0, tunnel.getOut());
                } catch (IOException e) {
                    Log.w(TAG, "bumpSamples", e);
                }
                Streams.closeQuietly(tunnel);
                mEncodeStream.term();
                mEncodeStream = null;
            }
        });
        mEncodeThread.start();
    }

    public void stopProjection() {
        if (mEncodeThread != null) {
            mEncodeThread.interrupt();
            try {
                mEncodeThread.join();
            } catch (InterruptedException e) {
                Log.w(TAG, "stopProjection", e);
            }
            mEncodeThread = null;
        }
        if (mMirrorDisplay != null) {
            mMirrorDisplay.release();
            mMirrorDisplay = null;
        }
        if (mPresentationDisplay != null) {
            mPresentationDisplay.release();
            mPresentationDisplay = null;
        }
        if (mGLThread != null) {
            mGLThread.pause();
            mGLThread = null;
        }
        mProjectionMode = ProjectionMode.NONE;
    }

    // 选择不同的方式实现方法不同

    public void setInputSurface(Surface surface) {

        switch (mProjectionMode) {
            case PRESENTATION:
                mPresentationDisplay.setSurface(surface);
                break;
            case MIRROR:
                mMirrorDisplay.setSurface(surface);
                break;
            case GLCOMPOUIND:
                mBindingHolder.onSurfaceDestroyed();
                mBindingHolder.transferFrom(surface);
                mBindingHolder.onSurfaceCreated();
                mBindingHolder.onSurfaceChanged(ImageFormat.UNKNOWN,
                        REQUEST_DISPLAY_WIDTH, REQUEST_DISPLAY_HEIGHT);
                break;

            default:
                mPresentationDisplay.setSurface(surface);
                break;
        }
    }


    // after request projection screen show presentation
    public void startPresentation() {
        if (mProjectionMode != ProjectionMode.NONE)
            return;
        mProjectionMode = ProjectionMode.PRESENTATION;
        mPresentationDisplay = mDisplayManager.createVirtualDisplay("presentation",
                REQUEST_DISPLAY_WIDTH,
                REQUEST_DISPLAY_HEIGHT,
                mMetrics.densityDpi,
                null,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                null, null);
    }

    public void startMirrorProjectionScreen() {
        if (mProjectionMode != ProjectionMode.NONE)
            return;
        mProjectionMode = ProjectionMode.MIRROR;
        mMirrorDisplay = mMediaProjection.createVirtualDisplay("Mirror",
                REQUEST_DISPLAY_WIDTH,
                REQUEST_DISPLAY_HEIGHT,
                mMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, null);
    }

    public void startCompoundProjectionScreen() {
        if (mProjectionMode != ProjectionMode.NONE)
            return;
        startPresentation();
        mProjectionMode = ProjectionMode.NONE;
        startMirrorProjectionScreen();
        mProjectionMode = ProjectionMode.GLCOMPOUIND;
        mBindingHolder = new BindingHolder();
        mGLThread = new GLThread(mContext, mBindingHolder);
        nRenderer = new GLDisplayRenderer() {
            @Override
            protected void onTextureReady(SurfaceTexture mirrorTexture, SurfaceTexture presentTexture) {
                mirrorTexture.setDefaultBufferSize(REQUEST_DISPLAY_WIDTH, REQUEST_DISPLAY_HEIGHT);
                presentTexture.setDefaultBufferSize(REQUEST_DISPLAY_WIDTH, REQUEST_DISPLAY_HEIGHT);
                mMirrorDisplay.setSurface(new Surface(mirrorTexture));
                mPresentationDisplay.setSurface(new Surface(presentTexture));
            }
        };
        mGLThread.setRenderer(nRenderer, false);
    }
}
