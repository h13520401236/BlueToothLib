package com.heng.bluetoothlib;

import android.bluetooth.BluetoothDevice;

public class BlueToothBean {
    public BluetoothDevice device;
    public int rssi;

    public BlueToothBean(BluetoothDevice device, int rssi) {
        this.device = device;
        this.rssi = rssi;
    }

}
