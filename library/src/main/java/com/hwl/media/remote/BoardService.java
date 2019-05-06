package com.hwl.media.remote;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;

import com.ustc.base.debug.Log;
import com.ustc.base.network.UrlFetcher;
import com.ustc.base.network.UrlFetcher.CallBack;

import java.util.HashMap;
import java.util.Map;

public class BoardService implements ServiceDiscovery.IServiceListener {

    public interface IServiceListener {
        void onServiceFound(String name);
        void onServiceLost();
    }

    private final static String TAG = "BoardService";

    private ServiceDiscovery mServiceDiscovery;
    private NsdServiceInfo mService;
    private UrlFetcher mHttpClient = new UrlFetcher();
    private final IServiceListener mServiceListener;
    private Handler mHandler = new Handler();

    public BoardService(Context context, IServiceListener listener) {
        mServiceDiscovery = new ServiceDiscovery(context, this);
        mServiceListener = listener;
        mServiceDiscovery.startDiscover();
    }

    public void remoteDisplay(final String videoUrl) {
        request("/misc/remote_display", new HashMap<String, String>() {
            {
                put("video", videoUrl);
            }
        }, new UrlFetcher.CallBack<Void>() {

            @Override
            public void onFailure(Exception e) {
                Log.w(TAG, "onFailure", e);
            }
            @Override
            public void onSuccess(Void result) {
            }
        });
    }

    @Override
    public void onServiceFound(final NsdServiceInfo serviceInfo) {
        if (mService == null) {
            mService = serviceInfo;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mServiceListener.onServiceFound(serviceInfo.getServiceName());
                }
            });
        }
    }

    @Override
    public void onServiceLost(NsdServiceInfo serviceInfo) {
        if (mService == serviceInfo) {
            mService = null;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mServiceListener.onServiceLost();
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private <T> void request(final String path, Map<String, String> args, CallBack<T> callback) {
        if (mService == null) {
            if (callback != null) {
                callback.onFailure(new java.net.UnknownServiceException());
            }
            return;
        }
        String url = "http://" + mService.getHost().getHostAddress() + ":"
                + mService.getPort() + path;
//        if (callback == null) {
//            callback = new CallBack<T>() {
//                @Override
//                public void onFailure(Exception exception) {
//                    Log.w(TAG, "request failed at " + path);
//                }
//                @Override
//                public void onSuccess(T result) {
//                }
//            };
//        }
        mHttpClient.asyncPost(url, args, callback);
    }
}
