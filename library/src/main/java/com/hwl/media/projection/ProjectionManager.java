package com.hwl.media.projection;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

import com.hwl.media.remote.BoardService;
import com.hwl.media.remote.InternetGateway;
import com.hwl.media.remote.ScreenRecord;
import com.ustc.base.debug.Console;
import com.ustc.base.debug.Log;
import com.ustc.base.debug.tools.HttpManager;
import com.ustc.base.util.stream.Streams;
import com.ustc.opengl.egl.GLThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class ProjectionManager {

    public final static int REQUEST_DISPLAY_WIDTH = 1920;
    public final static int REQUEST_DISPLAY_HEIGHT = 1220;

    private final static String TAG = "ProjectionScreenManager";

    private Context mContext;

    private short mPublishPort;
    private InternetGateway mGateway;
    private VirtualDisplay mPresentationDisplay;
    private VirtualDisplay mMirrorDisplay;
    private DisplayMetrics mMetrics;
    private MediaProjectionManager mMediaManager;
    private BindingHolder mBindingHolder;
    private MediaProjection mMediaProjection;
    private DisplayManager mDisplayManager;
    private GLDisplayRenderer nRenderer;
    private MyWriter mWriter;
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
        return "http://" + mGateway.getRealIp(mPublishPort) + ":"
                + mPublishPort + "/screen_record.h264";
    }

    private long mLastTime = 0;
    private long mLastFrame = 0;

    public double FPS(long now) {
        if (nRenderer == null || mLastTime == 0) {
            mLastTime = now;
            return 0.0;
        }
        long frame = nRenderer.getTotalFrame();
        double fps = (double) (frame - mLastFrame) * 1000 / (now - mLastTime);
        mLastFrame = frame;
        mLastTime = now;
        return fps;
    }

    public void setBound(float[] bound) {
        if (nRenderer != null) {
            nRenderer.setBound(bound);
        }
    }

    public void usePortMapping(boolean use) {
        if (use) {
            mGateway.addMap(mPublishPort);
        } else {
            mGateway.removeMap(mPublishPort);
        }
    }

    private void init() {

        EncoderDebugger.asyncDebug(mContext, ProjectionManager.REQUEST_DISPLAY_WIDTH,
                ProjectionManager.REQUEST_DISPLAY_HEIGHT);

        mPublishPort = (short) HttpManager.getInstance(mContext).getPort();
        mGateway = new InternetGateway(mContext);

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

    public void setMirrorProjection(int resultCode, Intent data) {
        mMediaProjection = mMediaManager.getMediaProjection(resultCode, data);
    }

    public void startProjection(final Console.StreamTunnel tunnel) {
        final EncodeStream stream = createEncoder();
        mEncodeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stream.bumpSamples(0, tunnel.getOut());
                } catch (IOException e) {
                    Log.w(TAG, "bumpSamples", e);
                }
                Streams.closeQuietly(tunnel);
            }
        });
        mEncodeThread.start();
    }

    public BoardService.IWriter startProjection() {
        final EncodeStream stream = createEncoder();
        mWriter = new MyWriter(stream);
        return mWriter;
    }

    private EncodeStream createEncoder() {
        EncodeStream stream = new EncodeStream(mContext);
        stream.init(REQUEST_DISPLAY_WIDTH, REQUEST_DISPLAY_HEIGHT);
        setInputSurface(stream.getInputSurface());
        stream.start();
        return stream;
    }

    public void stopProjection() {
        if (mWriter != null) {
            mWriter.interrupt();
            mWriter = null;
        }
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

    private static class MyWriter implements BoardService.IWriter {
        private final EncodeStream stream;
        private Thread mThread;
        private boolean mInterrupted;

        public MyWriter(EncodeStream stream) {
            this.stream = stream;
        }

        @Override
        public void write(OutputStream os) throws IOException {
            synchronized (this) {
                if (mInterrupted)
                    return;
                mThread = Thread.currentThread();
            }
            try {
                stream.bumpSamples(0, os);
            } finally {
                synchronized (this) {
                    mThread = null;
                    notify();
                }
            }
        }

        public synchronized void interrupt() {
            mInterrupted = true;
            while (mThread != null) {
                mThread.interrupt();
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }
}
