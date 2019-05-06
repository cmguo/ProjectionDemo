package com.hwl.media.remote;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ServiceDiscovery {

    public interface IServiceListener {
        void onServiceFound(NsdServiceInfo serviceInfo);
        void onServiceLost(NsdServiceInfo serviceInfo);
    }

    private static final String TAG = "ServiceDiscovery";
    private final String mServerType = "_board._tcp.";

    private NsdManager mNsdManager = null;
    private NsdManager.DiscoveryListener mDiscoveryListener = null;
    private NsdManager.ResolveListener mResolveListener = null;
    private IServiceListener mServiceListener;

    ServiceDiscovery(Context context, IServiceListener mServiceListener) {
        this.mServiceListener = mServiceListener;
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        mDiscoveryListener = new MyDiscoveryListener();
        mResolveListener = new MyResolveListener();
    }

    public void startDiscover() {
        mNsdManager.discoverServices(mServerType,
                NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void resoleServer(NsdServiceInfo nsdServiceInfo){
        mNsdManager.resolveService(nsdServiceInfo, mResolveListener);
    }

    private class MyResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Log.i(TAG, "resolution : " + serviceInfo.getServiceName()
                    + " \n host_from_server: " + serviceInfo.getHost() +
                    "\n port from server: " + serviceInfo.getPort());
            String hostAddress = serviceInfo.getHost().getHostAddress();
            Log.i(TAG, "hostAddress ip--> " + hostAddress );
            mServiceListener.onServiceFound(serviceInfo);
        }
    }

    private class MyDiscoveryListener implements NsdManager.DiscoveryListener {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.i(TAG, "onStartDiscoveryFailed--> " + serviceType + ":" + errorCode);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.i(TAG, "onStopDiscoveryFailed--> " + serviceType + ":" + errorCode);
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Log.i(TAG, "onDiscoveryStarted--> " + serviceType );
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.i(TAG, "onDiscoveryStopped--> " + serviceType );
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {//关键的回调方法
            Log.i(TAG, "onServiceFound Info:  " + serviceInfo);
            resoleServer(serviceInfo);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Log.i(TAG, "onServiceLost--> " + serviceInfo);
            mServiceListener.onServiceLost(serviceInfo);
        }
    }
}
