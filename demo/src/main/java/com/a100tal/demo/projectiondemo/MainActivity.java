package com.a100tal.demo.projectiondemo;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatTextView;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.hwl.media.projection.ProjectionManager;

import com.hwl.media.projection.ProjectionManager;
import com.hwl.media.remote.BoardService;
import com.hwl.media.widget.MirrorView;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.ustc.base.debug.Console;
import com.ustc.base.debug.Log;
import com.ustc.base.debug.PluginDebug;
import com.ustc.base.debug.tools.Plugin;
import com.ustc.base.plugin.PluginManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Timer;

public class MainActivity extends AppCompatActivity implements BoardService.IServiceListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1000;

    private BoardService mBoardService;
    private AppCompatTextView mTextView;
    private Timer mTimer;
    private ProjectionManager mProjectionManager;
    private AppCompatTextView mServiceTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBoardService = new BoardService(this, this);

        PluginManager.registerPlugins(Plugin.class);
        PluginManager.getInstance(this).importPlugins(null,null);
        PluginManager.getInstance(this).loadPlugins(PluginDebug.class);
        // detect Encoder
        startScreenCaptureIntent();

        mProjectionManager = new ProjectionManager(MainActivity.this);

        findViewById(R.id.show_presentation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setProjectionMode(ProjectionManager.ProjectionMode.PRESENTATION);
            }
        });
        findViewById(R.id.show_mirror).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setProjectionMode(ProjectionManager.ProjectionMode.MIRROR);
            }
        });
        findViewById(R.id.show_glcompound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setProjectionMode(ProjectionManager.ProjectionMode.GLCOMPOUIND);
                findViewById(R.id.view_mirror).setVisibility(View.VISIBLE);
            }
        });
        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setProjectionMode(ProjectionManager.ProjectionMode.NONE);
            }
        });
        findViewById(R.id.pause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimerPause = !mTimerPause;
                ((AppCompatButton)(v)).setText(mTimerPause ? "resume" : "pause");
            }
        });
        findViewById(R.id.chk_port).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mProjectionManager.usePortMapping(((AppCompatCheckBox) v).isChecked());
            }
        });
        ((MirrorView) findViewById(R.id.view_mirror)).setProjection(mProjectionManager);

        mTextView = findViewById(R.id.text);
        mServiceTextView = findViewById(R.id.service);
        mServiceTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "mServiceTextView onClick");
                IntentIntegrator intentIntegrator = new IntentIntegrator(MainActivity.this);
                intentIntegrator.setCaptureActivity(CaptureActivity.class);
                intentIntegrator.setBeepEnabled(false);
                intentIntegrator.initiateScan();
            }
        });
        mTimeRun.run();
        onServiceLost();
    }

    private void setProjectionMode(ProjectionManager.ProjectionMode mode)
    {
        mProjectionManager.setProjectionMode(mode);
        if (mode == ProjectionManager.ProjectionMode.NONE) {
            mTimerPause = true;
        } else {
            if (mode == ProjectionManager.ProjectionMode.GLCOMPOUIND
                    || mode == ProjectionManager.ProjectionMode.PRESENTATION) {
                Presentation mPresentation = new Presentation(this,
                        mProjectionManager.getPresentationDisplay());
                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.dialog, null);
                ButtonView buttonView = dialogView.findViewById(R.id.dialog_animation);
                // adjust dialog location
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                Window window = mPresentation.getWindow();
                lp.copyFrom(window.getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;
                // set dialog content
                mPresentation.setContentView(dialogView);
                mPresentation.show();
                // dialog展示后才能生效
                window.setAttributes(lp);
                buttonView.startAnimations();
            }
            boolean push = ((AppCompatCheckBox) findViewById(R.id.chk_push)).isChecked();
            if (push)
                mBoardService.remoteDisplay(mProjectionManager.startProjection());
            else
                mBoardService.remoteDisplay(mProjectionManager.getPulishUrl());
            mStartTime = SystemClock.uptimeMillis();
            mTimerPause = false;
        }
    }

    @Override
    public void onServiceFound(String name) {
        mServiceTextView.setText(name);
    }

    @Override
    public void onServiceLost() {
        mServiceTextView.setText("未发现设备");
    }

    private void startScreenCaptureIntent() {
        MediaProjectionManager mediaManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION, null);
    }

    private long mStartTime = SystemClock.uptimeMillis();
    private boolean mTimerPause;

    private Runnable mTimeRun = new Runnable() {

        private Handler mHandler = new Handler();

        @Override
        public void run() {
            mHandler.postDelayed(this, 1000);
            long now = SystemClock.uptimeMillis();
            long diff = now - mStartTime;
            if (!mTimerPause)
                mTextView.setText("Time: " + diff / 1000 + ", FPS: "
                        + String.format("%02.2f", mProjectionManager.FPS(now)));
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            mProjectionManager.setMirrorProjection(resultCode, data);
        } else {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null) {
                if (result.getContents() != null) {
                    mBoardService.setService(result.getContents());
                }
            }
        }
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args.length > 0) {
            Console.getInstance().execute(fd, args);
            return;
        }
        super.dump(prefix, fd, writer, args);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.exit(-1);
    }
}
