package com.heng.bluetoothlib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.fizzo.watch.Fw;
import cn.fizzo.watch.array.ConnectStates;
import cn.fizzo.watch.array.GattUUIDs;
import cn.fizzo.watch.entity.HrEntity;
import cn.fizzo.watch.observer.ConnectListener;
import cn.fizzo.watch.observer.NotifyActiveListener;
import cn.fizzo.watch.observer.NotifyHrListener;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener, ConnectListener, NotifyHrListener, NotifyActiveListener {

    private Button sacn;
    private ListView deviceList;
    //扫描的UUID过滤标准
    private static final UUID[] SCAN_UUID = new UUID[]{GattUUIDs.UUID_HEART_RATE_SERVICE};
//    private static final UUID[] SCAN_UUID = new UUID[]{GattUUIDs.UUID_DEVICE_INFO_SERVICE};


    private static final int MSG_REFRESH_LIST = 0x01;//刷新列表
    private static final int MSG_AUTO_STOP_SCAN = 0x02;//停止扫描


    private static final int INTERVAL_REFRESH_LIST = 1000;//刷新列表间隔
    private static final int INTERVAL_AUTO_STOP_SCAN = 15 * 1000;//自动停止扫描

    private List<BlueToothBean> mScanList = new ArrayList<>(); //扫描蓝牙列表
    private BlueToothDeviceAdapter adapter;
    private BluetoothAdapter bluetoothAdapter;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                //刷新列表
                case MSG_REFRESH_LIST:
//                    mScanList.clear();
                    adapter.notifyDataSetChanged();
                    mHandler.sendEmptyMessageDelayed(MSG_REFRESH_LIST, INTERVAL_REFRESH_LIST);
                    break;
                //停止扫描
                case MSG_AUTO_STOP_SCAN:
                    stopScan();
//                    Fw.getManager().stopScan();
                    break;
            }
        }
    };
    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Fw.getManager().init(this);
        Fw.getManager().setDebug(true);
        BluetoothManager blueManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        assert blueManager != null;
        bluetoothAdapter = blueManager.getAdapter();
        sacn = findViewById(R.id.scanBlueTooth);
        sacn.setOnClickListener(this);
        deviceList = findViewById(R.id.deviceList);
        adapter = new BlueToothDeviceAdapter();
        deviceList.setAdapter(adapter);
        deviceList.setOnItemClickListener(this);
        getBlueToothDevice();

        Fw.getManager().registerConnectListener(this);
        Fw.getManager().registerNotifyHrListener(this);
        Fw.getManager().registerNotifyActiveListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.scanBlueTooth:
                getBlueToothDevice();
                break;
        }
    }


    @SuppressLint("NewApi")
    private void getBlueToothDevice() {
        //判断蓝牙开关是否打开
        if (isBluetoothEnabled()) {
            startScan();
        } else {
            //强制打开蓝牙开关
            if (turnOnBluetooth()) {
                startScan();
            } else {
            }
        }
    }

    /**
     * 开始扫描
     */
    @SuppressLint({"MissingPermission", "NewApi"})
    private void startScan() {
        mScanList.clear();
        //开始BLE 扫描
        bluetoothAdapter.startLeScan(SCAN_UUID, mLeScanCallback);
        //1秒后刷新列表
        mHandler.sendEmptyMessageDelayed(MSG_REFRESH_LIST, INTERVAL_REFRESH_LIST);
        //15秒后停止扫描
        mHandler.sendEmptyMessageDelayed(MSG_AUTO_STOP_SCAN, INTERVAL_AUTO_STOP_SCAN);
    }

    /**
     * 停止扫描
     */
    private void stopScan() {
        mHandler.removeMessages(MSG_REFRESH_LIST);
    }
    /**
     * 当前 Android 设备的 bluetooth 是否已经开启
     *
     * @return true：Bluetooth 已经开启 false：Bluetooth 未开启
     */
    public boolean isBluetoothEnabled() {
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.isEnabled();
        }

        return false;
    }

    /**
     * 强制开启当前 Android 设备的 Bluetooth
     *
     * @return true：强制打开 Bluetooth　成功　false：强制打开 Bluetooth 失败
     */
    public boolean turnOnBluetooth() {

        if (bluetoothAdapter != null) {
            return bluetoothAdapter.enable();
        }

        return false;
    }
    /**
     * Device scan callback.
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            for (BlueToothBean le : mScanList) {
                if ((le.device.getAddress().equals(device.getAddress()))) {
                    le.rssi = rssi;
                    Log.e("blue", "onLeScan: " + device.getAddress());
                    Log.e("blue", "onLeScan: " + rssi);
                    return;
                }
            }
            BlueToothBean leBleScan = new BlueToothBean(device, rssi);
            mScanList.add(leBleScan);
        }
    };

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        BlueToothBean blueToothBean = mScanList.get(position);
        BluetoothDevice device = blueToothBean.device;
        Fw.getManager().addNewConnect(device.getAddress());
    }

    @Override
    public void connectStateChange(int state) {
        switch (state) {
            //连接中
            case ConnectStates.CONNECTING:
                Log.e("blue", "连接中");
                break;
            //失去连接
            case ConnectStates.DISCONNECT:
                Log.e("blue", "失去连接");
                break;
            //连接失败
            case ConnectStates.CONNECT_FAIL:
                Log.e("blue", "连接失败");

                break;
            //已连接
            case ConnectStates.CONNECTED:
                Log.e("blue", "已经连接");
                break;
            //验证制造商通过
            case ConnectStates.CHECK_MANUFACTURER_OK:
                break;
            //验证制造商失败
            case ConnectStates.CHECK_MANUFACTURER_FAIL:
                break;
            //写入UTC时间成功
            case ConnectStates.WRITE_UTC_OK:
                break;
            //监听心率变化成功
            case ConnectStates.NOTIFY_HR_OK:
                break;
            //获取手环版本信息成功
            case ConnectStates.READ_FIRMWARE_OK:
                Log.e("blue", "获取手环版本信息成功" + Fw.getManager().getConnectDevice().getFirmwareVersion());
                break;
            //监听私有服务是否成功
            case ConnectStates.NOTIFY_PRIVATE_SERVICE_OK:
                break;

        }
    }
    @Override
    public void notifyHr(HrEntity hrEntity) {
        Log.e("blue", "notifyHr: "+hrEntity.getHr() );
    }

    @Override
    public void notifyActive() {

    }

    private class BlueToothDeviceAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mScanList.size();
        }

        @Override
        public BlueToothBean getItem(int position) {
            return mScanList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = View.inflate(getBaseContext(), R.layout.bluetooth_item_layout, null);
                holder.itemView = convertView.findViewById(R.id.deviceView);
                holder.deviceID = (TextView) convertView.findViewById(R.id.blueTooth_device);
                holder.blueToothName = (TextView) convertView.findViewById(R.id.blueTooth_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.blueToothName.setText(mScanList.get(position).device.getName());
            holder.deviceID.setText(mScanList.get(position).device.getAddress());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Fw.getManager().addNewConnect(mScanList.get(position).device.getAddress());

                }
            });
            return convertView;
        }
    }

    private class ViewHolder {
        View itemView;
        TextView deviceID, blueToothName;
    }

}
