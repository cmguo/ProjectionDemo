package com.hwl.media.projection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.ustc.base.debug.Log;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkMonitor {

    private static final String TAG = "NetworkMonitor";

    private static final String UNKNOWN = new String();

    private Context mContext;
    private ConnectivityManager mService;
    private String mNetworkType = UNKNOWN;

    NetworkMonitor(Context context) {
        mContext = context;
        mService = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "onReceive");
                onConnectivityChange();
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(broadcastReceiver, intentFilter);
        mNetworkType = getNetworkType();
    }

    private void onConnectivityChange() {
        String type = mNetworkType;
        mNetworkType = UNKNOWN;
        getNetworkType();
    }

    public String getNetworkType() {
        if (mNetworkType != UNKNOWN)
            return mNetworkType;
        if (mService == null)
            return null;
        NetworkInfo ni = mService.getActiveNetworkInfo();
        mNetworkType = (ni != null && ni.isConnected()) ? ni.getTypeName() : null;
        return mNetworkType;
    }

    public static String getHostIP() {

        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.i("yao", "SocketException");
            e.printStackTrace();
        }
        return hostIp;

    }
}