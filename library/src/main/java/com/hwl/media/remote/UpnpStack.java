package com.hwl.media.remote;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.types.UDAServiceId;
import org.fourthline.cling.protocol.async.ReceivingNotification;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;

import android.content.Context;

import com.ustc.base.debug.Console;
import com.ustc.base.debug.Dumpable;
import com.ustc.base.debug.Dumpper;
import com.ustc.base.debug.Log;
import com.ustc.base.util.thread.WorkThreadPool;

import sun.misc.Service;

public class UpnpStack implements Dumpable {

    public interface IServiceListener {
        RemoteService discoverService(RemoteDevice device);
        void onServiceFound(RemoteService service);
        void onServiceLost(RemoteService service);
    }

    protected static final String TAG = "UpnpStack";
    
    private static UpnpStack sInstance;
    
    public synchronized static UpnpStack getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new UpnpStack(context);
            Console.getInstance(context).registerModule("upnp", sInstance);
        }
        return sInstance;
    }
    
    private UpnpService mUpnpService;
    private int mRejectCount;
    private Map<String, IServiceListener> mServiceTypes =
            new TreeMap<String, IServiceListener>();
    private Map<RemoteDevice, Map<String, RemoteService>> mRemoteDevices =
            new HashMap<RemoteDevice, Map<String, RemoteService>>();

    public UpnpStack(Context context) {
        if (context != null) {
            mUpnpService = new UpnpServiceImpl(
                    new AndroidUpnpServiceConfiguration(), mListener);
            mUpnpService.getControlPoint().search();
        } else {
            Log.d(TAG, "<init>", new Throwable());
        }
    }

    public void addServiceType(String type, IServiceListener listener) {
        synchronized (mServiceTypes) {
            if (mServiceTypes.containsKey(type))
                return;
            mServiceTypes.put(type, listener);
        }
        synchronized (mRemoteDevices) {
            for (Entry<RemoteDevice, Map<String, RemoteService>> entry : mRemoteDevices.entrySet()) {
                Map<String, RemoteService> services = entry.getValue();
                if (!services.containsKey(type)) {
                    RemoteService service = entry.getKey().findService(
                            new UDAServiceId(type));
                    if (service == null)
                        service = listener.discoverService(entry.getKey());
                    if (service != null) {
                        Log.d(TAG, "addServiceType with service " + type + " " + service);
                        services.put(type, service);
                        listener.onServiceFound(service);
                    }
                }
            }
        }
    }

    public void removeServiceType(String type) {
        synchronized (mServiceTypes) {
            if (!mServiceTypes.containsKey(type))
                return;
            mServiceTypes.remove(type);
        }
        synchronized (mRemoteDevices) {
            for (Entry<RemoteDevice, Map<String, RemoteService>> entry : mRemoteDevices.entrySet()) {
                Map<String, RemoteService> services = entry.getValue();
                RemoteService service = services.remove(type);
                if (service != null) {
                    Log.d(TAG, "removeServiceType with service " + type + " " + service);
                }
            }
        }
    }

    public List<RemoteService> getServicesWithType(String type) {
        List<RemoteService> services = new ArrayList<RemoteService>();
        synchronized (mRemoteDevices) {
            for (Map<String, RemoteService> entry : mRemoteDevices.values()) {
                RemoteService service = entry.get(type);
                if (service != null)
                    services.add(service);
            }
        }
        return services;
    }

    public RemoteService getServiceWithType(String type) {
        synchronized (mRemoteDevices) {
            for (Map<String, RemoteService> entry : mRemoteDevices.values()) {
                RemoteService service = entry.get(type);
                if (service != null)
                    return service;
            }
        }
        return null;
    }

    public void execute(ActionCallback run) {
        mUpnpService.getControlPoint().execute(run);
    }
    
    public void execute(SubscriptionCallback callback) {
        mUpnpService.getControlPoint().execute(callback);
    }

    public String getServiceTitle(RemoteService svc) {
        RemoteDevice device = (RemoteDevice) svc.getDevice();
        String title = device.getDetails().getFriendlyName();
        URL url = device.normalizeURI(svc.getControlURI());
        String host = url.getHost() + ":" + url.getPort();
        return title + "(" + host + ")";
    }

    private RemoteService getService(Map<RemoteDevice, RemoteService> svcs, String uuid) {
        synchronized (svcs) {
            for (Entry<RemoteDevice, RemoteService> s : svcs.entrySet()) {
                String id = s.getKey().getIdentity().getUdn().getIdentifierString();
                if (uuid == null || uuid.equals(id))
                    return s.getValue();
            }
        }
        return null;
    }

    private <E> Map<String, String> getServiceNames(Map<RemoteDevice, RemoteService> svcs) {
        Map<String, String> map = new TreeMap<String, String>();
        synchronized (svcs) {
            for (Entry<RemoteDevice, RemoteService> s : svcs.entrySet()) {
                RemoteDevice device = (RemoteDevice) s.getKey();
                String id = device.getIdentity().getUdn().getIdentifierString();
                map.put(id, getServiceTitle(s.getValue()));
            }
        }
        return map;
    }
    
    private void deviceAdded(RemoteDevice device) {
        Log.d(TAG, "deviceAdded " + device.getDisplayString());
        synchronized (mRemoteDevices) {
            Map<String, RemoteService> services = mRemoteDevices.get(device);
            if (services == null) {
                services = new TreeMap<String, RemoteService>();
                mRemoteDevices.put(device, services);
            }
            for (Entry<String, IServiceListener> entry : mServiceTypes.entrySet()) {
                RemoteService service = device.findService(
                        new UDAServiceId(entry.getKey()));
                if (service == null)
                    service = entry.getValue().discoverService(device);
                if (service != null) {
                    Log.d(TAG, "deviceAdded with service " + entry.getKey() + " " + service);
                    services.put(entry.getKey(), service);
                    entry.getValue().onServiceFound(service);
                }
            }
        }
    }
    
    private void deviceRemoved(RemoteDevice device) {
        Log.d(TAG, "deviceRemoved " + device.getDisplayString());
        synchronized (mRemoteDevices) {
            Map<String, RemoteService> services = mRemoteDevices.remove(device);
            if (services != null) {
                for (Entry<String, RemoteService> entry : services.entrySet()) {
                    Log.d(TAG, "deviceRemoved with service " + entry.getKey() + " " + entry.getValue());
                    IServiceListener listener = mServiceTypes.get(entry.getKey());
                    if (listener != null)
                        listener.onServiceLost(entry.getValue());
                }
            }
        }
    }
    
    private RegistryListener mListener = new RegistryListener() {
        
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            //Log.d(TAG, "remoteDeviceDiscoveryStarted " + device);
        }
        
        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device,
                Exception exception) {
            Log.w(TAG, "remoteDeviceDiscoveryFailed " + device, exception);
        }
        
        @Override
        public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
            //Log.d(TAG, "remoteDeviceUpdated " + device);
        }
        
        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            //Log.d(TAG, "remoteDeviceRemoved " + device);
            deviceRemoved(device);
        }
        
        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            //Log.d(TAG, "remoteDeviceAdded " + device);
            deviceAdded(device);
        }
        
        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            //Log.d(TAG, "localDeviceRemoved " + device\);
        }
        
        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            //Log.d(TAG, "localDeviceAdded " + device\);
        }
        
        @Override
        public void beforeShutdown(Registry registry) {
            Log.d(TAG, "beforeShutdown");
        }
        
        @Override
        public void afterShutdown() {
            Log.d(TAG, "afterShutdown");
        }
    };

    @Override
    public void dump(Dumpper dumpper) {
        dumpper.dump("mUpnpService", mUpnpService);
        dumpper.dump("mRejectCount", mRejectCount);
        dumpper.dump("mServiceTypes", mServiceTypes);
        dumpper.dump("mRemoteDevices", mRemoteDevices);
    }

}
