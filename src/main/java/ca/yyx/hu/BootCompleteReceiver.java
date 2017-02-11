package ca.yyx.hu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import ca.yyx.hu.aap.AapService;
import ca.yyx.hu.connection.UsbDeviceCompat;
import ca.yyx.hu.connection.UsbModeSwitch;
import ca.yyx.hu.location.GpsLocationService;
import ca.yyx.hu.utils.AppLog;
import ca.yyx.hu.utils.Settings;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.HashMap;
import java.util.Iterator;

/**
 * @author algavris
 * @date 18/12/2016.
 */
public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {

        AppLog.d("Boot Complete!");
        AppLog.d("Launching GpsLocationService");
        Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                App.get(context).startService(GpsLocationService.intent(context));
            }
        }, 10000);

        // Enumerate USB Devices
        AppLog.d("Enumerating USB devices on boot");
        UsbManager manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();

            if (device == null) {
                AppLog.e("No USB device");
                continue;
            }

            if (App.get(context).transport().isAlive()) {
                AppLog.e("Thread already running");
                continue;
            }

            if (UsbDeviceCompat.isInAccessoryMode(device)) {
                AppLog.e("Usb in accessory mode");
                App.get(context).startService(AapService.createIntent(device, context));
                continue;
            }

            UsbDeviceCompat deviceCompat = new UsbDeviceCompat(device);
            Settings settings = new Settings(context);
            if (!settings.isConnectingDevice(deviceCompat)) {
                AppLog.d("Skipping device " + deviceCompat.getUniqueName());
                continue;
            }

            UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
            UsbModeSwitch usbMode = new UsbModeSwitch(usbManager);
            AppLog.d("Switching USB device to accessory mode " + deviceCompat.getUniqueName());
            if (usbMode.switchMode(device)) {
                AppLog.d("Successfully switched mode " + deviceCompat.getUniqueName());
            } else {
                AppLog.d("Failed to switch mode " + deviceCompat.getUniqueName());
            }

        }
    }
}
