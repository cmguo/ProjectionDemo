package com.hwl.media.remote;

import android.content.Context;

import com.hwl.media.projection.NetworkMonitor;
import com.ustc.base.debug.Dumpable;
import com.ustc.base.debug.Dumpper;
import com.ustc.base.debug.Log;

import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.ServiceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.support.igd.callback.GetExternalIP;
import org.fourthline.cling.support.igd.callback.PortMappingAdd;
import org.fourthline.cling.support.igd.callback.PortMappingDelete;
import org.fourthline.cling.support.model.PortMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InternetGateway implements UpnpStack.IServiceListener, Dumpable {

    private static final String TAG = "InternetGateway";
    private static final String SERVICE_TYPE = "WANIPConnection";

    private final UpnpStack mUpnpStack;
    private RemoteService mService;
    private String mLocalIp;
    private String mExternalIp;
    private List<Short> mRequestPortMaps = new ArrayList<Short>();
    private List<Short> mPortMaps = new ArrayList<Short>();

    public InternetGateway(Context context) {
        mUpnpStack = UpnpStack.getInstance(context);
        mUpnpStack.addServiceType(SERVICE_TYPE, this);
        mExternalIp = mLocalIp = NetworkMonitor.getHostIP();
    }

    public synchronized void addMap(short port) {
        if (mRequestPortMaps.contains(port))
            return;
        mRequestPortMaps.add(port);
        if (!mPortMaps.contains(port) && mService != null)
            doMap(port);
    }

    public synchronized void removeMap(short port) {
        if (!mRequestPortMaps.contains(port))
            return;
        mRequestPortMaps.remove(Short.valueOf(port));
        if (mPortMaps.contains(port)) {
            mPortMaps.remove(Short.valueOf(port));
            doUnmap(port);
        }
    }

    public synchronized String getRealIp(short port) {
        if (mPortMaps.contains(port)) {
            return mExternalIp;
        } else {
            return mLocalIp;
        }
    }

    @Override
    public synchronized void onServiceFound(RemoteService service) {
        if (mService != null)
            return;
        mService = service;
        getExtIp();
        for (short port : mRequestPortMaps) {
            doMap(port);
        }
    }

    @Override
    public synchronized void onServiceLost(RemoteService service) {
        if (mService != service)
            return;
        mPortMaps.clear();
        mExternalIp = mLocalIp;
        mService = null;
    }

    private static final DeviceType IGD_DEVICE_TYPE = new UDADeviceType("InternetGatewayDevice", 1);
    private static final DeviceType CONNECTION_DEVICE_TYPE = new UDADeviceType("WANConnectionDevice", 1);
    private static final ServiceType IP_SERVICE_TYPE = new UDAServiceType("WANIPConnection", 1);
    private static final ServiceType PPP_SERVICE_TYPE = new UDAServiceType("WANPPPConnection", 1);

    public RemoteService discoverService(RemoteDevice device) {
        if (!device.getType().equals(IGD_DEVICE_TYPE)) {
            return null;
        } else {
            RemoteDevice[] connectionDevices = device.findDevices(CONNECTION_DEVICE_TYPE);
            if (connectionDevices.length == 0) {
                Log.d(TAG, "IGD doesn't support '" + CONNECTION_DEVICE_TYPE + "': " + device);
                return null;
            } else {
                RemoteDevice connectionDevice = connectionDevices[0];
                Log.d(TAG, "Using first discovered WAN connection device: " + connectionDevice);
                RemoteService ipConnectionService = connectionDevice.findService(IP_SERVICE_TYPE);
                RemoteService pppConnectionService = connectionDevice.findService(PPP_SERVICE_TYPE);
                if (ipConnectionService == null && pppConnectionService == null) {
                    Log.d(TAG, "IGD doesn't support IP or PPP WAN connection service: " + device);
                }
                return ipConnectionService != null ? ipConnectionService : pppConnectionService;
            }
        }
    }

    private void doMap(final short port) {
        PortMapping desiredMapping = new PortMapping(port, mLocalIp, PortMapping.Protocol.TCP);
        mUpnpStack.execute(
                new PortMappingAdd(mService, desiredMapping) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        synchronized (InternetGateway.this) {
                            mPortMaps.add(port);
                        }
                    }
                    @Override
                    public void failure(ActionInvocation invocation,
                                        UpnpResponse operation,
                                        String defaultMsg) {
                        Log.w(TAG, "map" + defaultMsg);
                    }
                }
        );
    }

    public void doUnmap(short port) {
        PortMapping desiredMapping = new PortMapping(port, mLocalIp, PortMapping.Protocol.TCP);
        mUpnpStack.execute(
                new PortMappingDelete(mService, desiredMapping) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        // All OK
                    }
                    @Override
                    public void failure(ActionInvocation invocation,
                                        UpnpResponse operation,
                                        String defaultMsg) {
                        Log.w(TAG, "unmap" + defaultMsg);
                    }
                }
        );
    }

    private void getExtIp() {
        mUpnpStack.execute(
                new GetExternalIP(mService) {
                    @Override
                    protected void success(String externalIPAddress) {
                        synchronized (InternetGateway.this) {
                            mExternalIp = externalIPAddress;
                        }
                    }
                    @Override
                    public void failure(ActionInvocation invocation,
                                        UpnpResponse operation,
                                        String defaultMsg) {
                        Log.w(TAG, "getExtIp" + defaultMsg);
                    }
                }
        );
    }

    @Override
    public void dump(Dumpper dumpper) {
        dumpper.dump("mService", mService);
        dumpper.dump("mLocalIp", mLocalIp);
        dumpper.dump("mExternalIp", mExternalIp);
        dumpper.dump("mRequestPortMaps", mRequestPortMaps);
        dumpper.dump("mPortMaps", mPortMaps);
    }
}
