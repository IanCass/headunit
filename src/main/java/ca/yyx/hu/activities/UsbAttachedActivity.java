package ca.yyx.hu.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Toast;

import ca.yyx.hu.App;
import ca.yyx.hu.aap.AapService;
import ca.yyx.hu.connection.UsbDeviceCompat;
import ca.yyx.hu.connection.UsbModeSwitch;
import ca.yyx.hu.utils.AppLog;
import ca.yyx.hu.utils.LocalIntent;
import ca.yyx.hu.utils.Settings;

/**
 * @author algavris
 * @date 30/05/2016.
 */
public class UsbAttachedActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppLog.d("USB Intent: " + getIntent());

        UsbDevice device = LocalIntent.deviceFromIntent(getIntent());
        if (device == null) {
            AppLog.e("No USB device");
            finish();
            return;
        }

        if (App.get(this).transport().isAlive()) {
            AppLog.e("Thread already running");
            finish();
            return;
        }

        if (UsbDeviceCompat.isInAccessoryMode(device)) {
            AppLog.e("Usb in accessory mode");
            startService(AapService.createIntent(device, this));
            finish();
            return;
        }

        UsbDeviceCompat deviceCompat = new UsbDeviceCompat(device);
        Settings settings = new Settings(this);
        if (!settings.isConnectingDevice(deviceCompat)) {
            AppLog.d("Skipping device " + deviceCompat.getUniqueName());
            finish();
            return;
        }

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbModeSwitch usbMode = new UsbModeSwitch(usbManager);
        AppLog.d("Switching USB device to accessory mode " + deviceCompat.getUniqueName());
        if (usbMode.switchMode(device)) {
            AppLog.d("Successfully switched mode " + deviceCompat.getUniqueName());
        } else {
            AppLog.d("Failed to switch mode " + deviceCompat.getUniqueName());
        }


        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        UsbDevice device = LocalIntent.deviceFromIntent(getIntent());
        if (device == null) {
            AppLog.e("No USB device");
            finish();
            return;
        }

        AppLog.d(UsbDeviceCompat.getUniqueName(device));

        if (!App.get(this).transport().isAlive()) {
            if (UsbDeviceCompat.isInAccessoryMode(device)) {
                AppLog.e("Usb in accessory mode");
                startService(AapService.createIntent(device, this));
            }
        } else {
            AppLog.e("Thread already running");
        }

        finish();
    }
}
