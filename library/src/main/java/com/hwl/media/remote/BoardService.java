package com.hwl.media.remote;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;

import com.ustc.base.debug.Console;
import com.ustc.base.debug.Dumpable;
import com.ustc.base.debug.Dumpper;
import com.ustc.base.debug.Log;
import com.ustc.base.network.UrlConfig;
import com.ustc.base.network.UrlFetcher;
import com.ustc.base.network.UrlFetcher.CallBack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;

public class BoardService implements ServiceDiscovery.IServiceListener, Dumpable {

    public interface IServiceListener {
        void onServiceFound(String name);
        void onServiceLost();
    }

    public synchronized static BoardService getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BoardService(context);
            Console.getInstance(context).registerModule("board", sInstance);
        }
        return sInstance;
    }

    private final static String TAG = "BoardService";

    private static BoardService sInstance;

    private ServiceDiscovery mServiceDiscovery;
    private NsdServiceInfo mService;
    private String mUri;
    private UrlFetcher mHttpClient = new UrlFetcher();
    private IServiceListener mServiceListener;
    private Handler mHandler = new Handler();

    private BoardService(Context context) {
        mServiceDiscovery = new ServiceDiscovery(context, this);
        mServiceDiscovery.startDiscover();
    }

    public void setServiceListener(IServiceListener listener) {
        mServiceListener = listener;
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

    public interface IWriter {
        void write(OutputStream os) throws IOException;
    }
    /*
        Push mode
     */
    public void remoteDisplay(final IWriter writer) {
        RequestBody body = new RequestBody() {
            @Override public MediaType contentType() {
                return MediaType.parse("video/h264");
            }
            @Override public long contentLength() {
                return -1;
            }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                writer.write(sink.outputStream());
            }
        };
        request("/misc/remote_display", body, new UrlFetcher.CallBack<Void>() {
            @Override
            public void onFailure(Exception e) {
                Log.w(TAG, "onFailure", e);
            }
            @Override
            public void onSuccess(Void result) {
            }
        });
    }

    public void setService(String urlss) {
        final String[] urls = urlss.split("\\|");
        UrlFetcher.CallBack<String> callback = new UrlFetcher.CallBack<String>() {
            private int iUrl = -1;
            @Override
            public void onFailure(Exception exception) {
                if (exception != null) {
                    Log.w(TAG, "setService " + urls[iUrl], exception);
                }
                if (++iUrl < urls.length) {
                    mHttpClient.asyncGet(urls[iUrl] + "/misc/pair", this);
                }
            }
            @Override
            public void onSuccess(final String result) {
                Log.d(TAG, "setService " + result + ": " + urls[iUrl]);
                synchronized (BoardService.this) {
                    mUri = urls[iUrl];
                    mService = null;
                    BoardService.this.mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mServiceListener.onServiceFound(result);
                        }
                    });
                }
            }
        };
        // start
        callback.onFailure(null);
    }

    @Override
    public synchronized void onServiceFound(final NsdServiceInfo serviceInfo) {
        if (mService == null) {
            mService = serviceInfo;
            mUri = "http://" + mService.getHost().getHostAddress() + ":"
                    + mService.getPort();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mServiceListener.onServiceFound(serviceInfo.getServiceName());
                }
            });
        }
    }

    @Override
    public synchronized void onServiceLost(NsdServiceInfo serviceInfo) {
        if (mService == serviceInfo) {
            mService = null;
            mUri = null;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mServiceListener.onServiceLost();
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private synchronized  <T, D> void request(final String path, D data, CallBack<T> callback, UrlConfig ... configs) {
        if (mUri == null) {
            if (callback != null) {
                callback.onFailure(new java.net.UnknownServiceException());
            }
            return;
        }
        String url = mUri + path;
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
        mHttpClient.asyncPost(url, data, callback, configs);
    }

    @Override
    public void dump(Dumpper dumpper) {
        dumpper.dump("mService", mService);
        dumpper.dump("mUri", mUri);
        dumpper.dump("mServiceListener", mServiceListener);
    }

}
