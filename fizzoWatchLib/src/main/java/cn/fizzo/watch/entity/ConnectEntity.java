package cn.fizzo.watch.entity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import java.lang.reflect.Method;
import java.util.UUID;

import cn.fizzo.watch.Fw;
import cn.fizzo.watch.array.ConnectStates;
import cn.fizzo.watch.array.FizzoCMDs;
import cn.fizzo.watch.array.GattUUIDs;
import cn.fizzo.watch.utils.Log;
import cn.fizzo.watch.utils.NotifyManager;
import cn.fizzo.watch.utils.StringU;

/**
 * Created by Raul.Fan on 2017/3/28.
 */
@SuppressLint("NewApi")
public class ConnectEntity {

    /* contains */
    private static final String TAG = "ConnectEntity";


    private static final long DELAY_REPEAT_CONNECT = 1000;//尝试重新连接的延迟
    private static final long DELAY_REPEAT_DISCOVER = 7 * 1000;//发现服务的超时判断
    private static final long DELAY_REPEAT_READ = 200;//重复读取延迟
    private static final long DELAY_REPEAT_WRITE = 200;//重复写入延迟
    private static final long DELAY_REPEAT_NOTIFY = 500;//重复写入延迟

    private static final int MSG_REPEAT_CONNECT = 0x01;// 重新连接的消息
    private static final int MSG_REPEAT_DISCOVER = 0x02;//重新发现服务
    private static final int MSG_READ_MANUFACTURER = 0x03;//读取生产厂家信息
    private static final int MSG_READ_MODEL = 0x04;//读取产品MODEL信息
    private static final int MSG_NOTIFY_HR = 0x05;//同步心率
    private static final int MSG_WRITE_UTC = 0x06;//写入UTC
    private static final int MSG_READ_FIRMWARE = 0x07;//获取firmware版本
    private static final int MSG_NOTIFY_PRIVATE_SERVICE = 0x08;//监听私有服务成功
    private static final int MSG_WRITE_CONTROL_LIGHT = 0x09;//写入光管控制
    private static final int MSG_WRITE_VIBRATE = 0x10;//写入振动消息


    /* local data about system*/
    private Context mContext;//上下文
    private BluetoothAdapter mBluetoothAdapter;//蓝牙适配器

    /* local data about state */
    public boolean mIsConnected = false;//当前是否是连接状态
    public boolean mNeedConnect = true;//决定断开后是否重连

    /* local data about device*/
    public String mAddress;//连接的mac地址
    public BluetoothGatt mBluetoothGatt;//GATT实例
    private BluetoothGattCallback mGattCallback;//GATT回调

    private String mManufacturer;//制造厂商
    private String mModel;
    private String mFirmwareVer;//版本号

    /* local characteristic*/
    BluetoothGattCharacteristic mManufacturerC;//设备制造商地址
    BluetoothGattCharacteristic mFirmwareC;//设备版本
    BluetoothGattCharacteristic mModelC;//设备model号

    BluetoothGattService mHrGattS;//心率服务
    BluetoothGattCharacteristic mHrC;//心率特征

    BluetoothGattCharacteristic mFizzoPrivateC;//FIZZO 命令特征
    BluetoothGattCharacteristic mFizzoPrivateNotifyC;//FIZZO 获取结果特征

    @SuppressLint("HandlerLeak")
    private
    Handler mLocalHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                //再次尝试连接
                case MSG_REPEAT_CONNECT:
                    repeatConnect();
                    break;
                //再次尝试发现GATT
                case MSG_REPEAT_DISCOVER:
                    repeatDiscoverGatt();
                    break;
                //尝试读取制造厂商
                case MSG_READ_MANUFACTURER:
                    readManufacturer();
                    break;
                //尝试读取设备model
                case MSG_READ_MODEL:
                    readModel();
                    break;
                //同步心率数据
                case MSG_NOTIFY_HR:
                    notifyHr();
                    break;
                //写入UTC
                case MSG_WRITE_UTC:
                    writeUTC();
                    break;
                //获取firmware版本信息
                case MSG_READ_FIRMWARE:
                    readFirmwareVersion();
                    break;
                case MSG_NOTIFY_PRIVATE_SERVICE:
                    notifyPrivateService();
                    break;
                case MSG_WRITE_CONTROL_LIGHT:
                    writeControlLight((boolean) msg.obj);
                    break;
                case MSG_WRITE_VIBRATE:
                    writeVibrate();
                    break;
            }
        }
    };


    /**
     * 构造
     *
     * @param context
     * @param mac
     * @param mAdapter
     */
    public ConnectEntity(Context context, final String mac, final BluetoothAdapter mAdapter) {
        this.mAddress = mac;
        this.mBluetoothAdapter = mAdapter;
        this.mContext = context;

        initCallback();
        try {
            mBluetoothGatt = mBluetoothAdapter.getRemoteDevice(mac).connectGatt(context,
                    false, mGattCallback);
            NotifyManager.getManager().notifyStateChange(ConnectStates.CONNECTING);
        } catch (IllegalArgumentException ex) {
            sendRepeatConnectMsg(DELAY_REPEAT_CONNECT);
        }
    }

    /**
     * 初始化GATT回调
     */
    @SuppressLint("NewApi")
    private void initCallback() {
        mGattCallback = null;
        mGattCallback = new BluetoothGattCallback() {
            //连接状态发生改变
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                ConnectEntity.this.onConnectionStateChange(gatt, status, newState);
            }

            //发现服务
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                ConnectEntity.this.onServicesDiscovered(gatt, status);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int
                    status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onCharacteristicReadSuccess(characteristic);
                } else {
                    onCharacteristicReadFail(characteristic);
                }
            }

            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                ConnectEntity.this.onCharacteristicChanged(characteristic);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeCharacteristicSuccess(characteristic);
                } else {
                    writeCharacteristicFail(characteristic);
                }
            }
        };
    }


    //=====================================================================================
    //============================Call back的相关处理 ======================================
    //=====================================================================================

    /**
     * 连接状态发生变化
     */
    @SuppressLint("NewApi")
    private synchronized void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
        // 连接失败判断
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "<" + mAddress + ">" + "status !GATT_SUCCESS ,mNeedConnect:" + mNeedConnect);
            //若需要重连
            if (mNeedConnect) {
                mIsConnected = false;
                NotifyManager.getManager().notifyStateChange(ConnectStates.CONNECT_FAIL);
                sendRepeatConnectMsg(DELAY_REPEAT_CONNECT);
            }
            return;
        }
        //已连接
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i(TAG, "<" + mAddress + ">" + "Connected to GATT server.");
            Log.i(TAG, "<" + mAddress + ">" + "Attempting to start service discovery");
            destroyHandler();
            if (mBluetoothGatt != null) {
                mBluetoothGatt.discoverServices();
            }
            //若发现服务超时，重新发现服务
            sendRepeatDiscoverGatt(DELAY_REPEAT_DISCOVER);
            //连接断开
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "<" + mAddress + ">" + "Disconnected from GATT server.");
            if (!mNeedConnect) {
                mBluetoothGatt.close();
            }
            mIsConnected = false;
            NotifyManager.getManager().notifyStateChange(ConnectStates.DISCONNECT);
            sendRepeatConnectMsg(DELAY_REPEAT_CONNECT);
        }
    }

    /**
     * 发现GATT 服务结束
     *
     * @param gatt
     * @param status
     */
    @SuppressLint("NewApi")
    private synchronized void onServicesDiscovered(BluetoothGatt gatt, int status) {
        mLocalHandler.removeMessages(MSG_REPEAT_DISCOVER);
        mLocalHandler.removeMessages(MSG_REPEAT_CONNECT);
        Fw.getManager().stopScan();

        // 发现服务失败
        if (status != BluetoothGatt.GATT_SUCCESS) {
            sendRepeatConnectMsg(0);
            return;
        }
        //去除重复回调
        if (mIsConnected) {
            return;
        }
        Log.v(TAG, "real connected");
        mIsConnected = true;
        NotifyManager.getManager().notifyStateChange(ConnectStates.CONNECTED);

        //扫描服务
        for (BluetoothGattService gattService : gatt.getServices()) {
            Log.e(TAG, "<" + mAddress + ">" + "UUID " + gattService.getUuid().toString());

            //发现device info service
            if (gattService.getUuid().equals(GattUUIDs.UUID_DEVICE_INFO_SERVICE)) {
                mManufacturerC = gattService.getCharacteristic(GattUUIDs.UUID_MANUFACTURER_NAME);
                mFirmwareC = gattService.getCharacteristic(GattUUIDs.UUID_FIRMWARE_REVISION);
                mModelC = gattService.getCharacteristic(GattUUIDs.UUID_MODEL);
            }
            //发现心率服务
            if (gattService.getUuid().equals(GattUUIDs.UUID_HEART_RATE_SERVICE)) {
                mHrGattS = gattService;
                mHrC = mHrGattS.getCharacteristic(GattUUIDs.UUID_HEART_RATE_MEASUREMENT);
            }

            //发现Fizzo 专用服务
            if (gattService.getUuid().equals(GattUUIDs.UUID_FIZZO_SERVICE)) {
                mFizzoPrivateC = gattService.getCharacteristic(GattUUIDs.UUID_FIZZO_CMD_C);
                mFizzoPrivateNotifyC = gattService.getCharacteristic(GattUUIDs.UUID_FIZZO_NOTIFY_C);
            }
        }

        //若无法读取该设备的Manufacturer,Mode,心率 , 则无法判断设备后续动作，重新连接
        if (mManufacturerC == null || mHrC == null) {
            refreshDeviceCache();
            sendRepeatConnectMsg(DELAY_REPEAT_CONNECT);
        } else {
            mLocalHandler.sendEmptyMessageDelayed(MSG_READ_MANUFACTURER, DELAY_REPEAT_READ);
        }
    }

    /**
     * 读取数据成功
     *
     * @param characteristic
     */
    @SuppressLint("NewApi")
    private synchronized void onCharacteristicReadSuccess(final BluetoothGattCharacteristic characteristic) {
        Log.v(TAG, "onCharacteristicReadSuccess");
        //读取生产厂家
        if (GattUUIDs.UUID_MANUFACTURER_NAME.equals(characteristic.getUuid())) {

            mManufacturer = characteristic.getStringValue(0).trim();
            Log.v(TAG, "mManufacturer:" + mManufacturer);
            //若是FIZZO 设备
            if (mManufacturer.equals("FIZZO")) {
                NotifyManager.getManager().notifyStateChange(ConnectStates.CHECK_MANUFACTURER_OK);
                //写入UTC
                mLocalHandler.sendEmptyMessageDelayed(MSG_WRITE_UTC, DELAY_REPEAT_WRITE);
                //notify 心率
                sendMsg(MSG_NOTIFY_HR, true, DELAY_REPEAT_NOTIFY + DELAY_REPEAT_WRITE);

            } else if (mManufacturer.equals("YYX")) {
                NotifyManager.getManager().notifyStateChange(ConnectStates.CHECK_MANUFACTURER_OK);
                //notify 心率
                sendMsg(MSG_NOTIFY_HR, true, DELAY_REPEAT_NOTIFY + DELAY_REPEAT_WRITE);

            } else if (mManufacturer.equals("TBD")) {
                NotifyManager.getManager().notifyStateChange(ConnectStates.CHECK_MANUFACTURER_OK);
                //notify 心率
                sendMsg(MSG_NOTIFY_HR, true, DELAY_REPEAT_NOTIFY + DELAY_REPEAT_WRITE);
            } else {
                NotifyManager.getManager().notifyStateChange(ConnectStates.CHECK_MANUFACTURER_FAIL);
            }
            return;

        }
        //获取设备firmware信息
        if (GattUUIDs.UUID_FIRMWARE_REVISION.equals(characteristic.getUuid())) {
            mFirmwareVer = characteristic.getStringValue(0).trim();
            NotifyManager.getManager().notifyStateChange(ConnectStates.READ_FIRMWARE_OK);
            sendMsg(MSG_NOTIFY_PRIVATE_SERVICE, null, 0);
        }
    }

    /**
     * 读取数据失败
     *
     * @param characteristic
     */
    private synchronized void onCharacteristicReadFail(final BluetoothGattCharacteristic characteristic) {
        Log.v(TAG, "onCharacteristicReadFail");
    }

    /**
     * 读取notify数据的结果
     *
     * @param characteristic
     */
    private synchronized void onCharacteristicChanged(final BluetoothGattCharacteristic characteristic) {
//        Log.v(TAG, "onCharacteristicChanged");
        //心率数据
        if (GattUUIDs.UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            if (characteristic.getValue() != null) {
                byte[] data = characteristic.getValue();
                analysisHrData(data);
            }
            return;
        }
        if (GattUUIDs.UUID_FIZZO_NOTIFY_C.equals(characteristic.getUuid())) {
            if (characteristic.getValue() != null) {
                byte[] data = characteristic.getValue();
                analysisPrivateData(data);
            }
        }
    }

    /**
     * 解析心率数据
     *
     * @param data
     */
    private void analysisHrData(final byte[] data) {
        int hr = data[1] & 0xff;
        int stepCount = 0;
        int cadence = 0;
        int speed = 0;
        //若是FIzzo数据
        if (mManufacturer.equals("FIZZO")) {
            if (data.length > 3) {
                stepCount = ((data[2] & 0xff) | ((data[3] & 0xff) << 8));
            }
            if (data.length > 4) {
                cadence = data[4] & 0xff;
            }
            if (data.length > 5) {
                speed = data[5] & 0xff;
            }
        } else if (mManufacturer.equals("YYX")) {
            if (data.length > 3) {
                stepCount = ((data[2] & 0xff) | ((data[3] & 0xff) << 8));
            }
            if (data.length > 4) {
                cadence = data[4] & 0xff;
            }
            if (data.length > 5) {
                speed = data[5] & 0xff;
            }
        }else if (mManufacturer.equals("TBD")){
            if (data.length > 3) {
                stepCount = ((data[2] & 0xff) | ((data[3] & 0xff) << 8));
            }
            if (data.length > 4) {
                cadence = data[4] & 0xff;
            }
            if (data.length > 5) {
                speed = data[5] & 0xff;
            }
        }


        NotifyManager.getManager().notifyRealTimeHr(new HrEntity(hr, stepCount, cadence, speed));
    }


    /**
     * 解析从私有服务得到的数据
     *
     * @param data
     */
    private void analysisPrivateData(final byte[] data) {
        Log.v(TAG, "analysisPrivateData:" + StringU.bytesToHexString(data));
        byte action = data[0];
        byte cmd = data[1];
        byte status = data[2];
        switch (action) {
            //激活TAG
            case FizzoCMDs.ACTION_TAG_ACTIVE:
                switch (cmd) {
                    //振动
                    case FizzoCMDs.CMD_SETTING_VIBRATE:
                        if (status == FizzoCMDs.STATUS_SETTING_SUCCESS) {
                            NotifyManager.getManager().notifyActive();
                        }
                        break;
                }
                break;
        }

    }

    /**
     * 写入数据成功
     *
     * @param characteristic
     */
    private synchronized void writeCharacteristicSuccess(final BluetoothGattCharacteristic characteristic) {
        Log.v(TAG, "writeCharacteristicSuccess");
    }


    /**
     * 写入数据失败
     *
     * @param characteristic
     */
    private synchronized void writeCharacteristicFail(final BluetoothGattCharacteristic characteristic) {
        Log.v(TAG, "writeCharacteristicFail");

    }


    //==================================================================================
    //================================ 对外公用 =========================================
    //==================================================================================

    /**
     * 获取版本信息
     *
     * @return
     */
    public String getFirmwareVersion() {
        return mFirmwareVer;
    }

    /**
     * 控制光管
     *
     * @param open
     */
    public void controlLight(final boolean open) {
        sendMsg(MSG_WRITE_CONTROL_LIGHT, open, 0);
    }

    /**
     * 发送震动
     */
    public void vibrate() {
        sendMsg(MSG_WRITE_VIBRATE, null, 0);
    }

    //===================================================================================
    //============================== 私有方法（读）=======================================
    //===================================================================================

    /**
     * 读取生产厂家
     */
    public void readManufacturer() {
        boolean readResult = mBluetoothGatt.readCharacteristic(mManufacturerC);
        if (!readResult) {
            mLocalHandler.sendEmptyMessageDelayed(MSG_READ_MANUFACTURER, DELAY_REPEAT_READ);
        }
    }

    /**
     * 读取Model
     */
    public void readModel() {
        if (mModelC != null) {
            boolean readResult = mBluetoothGatt.readCharacteristic(mModelC);
            if (!readResult) {
                mLocalHandler.sendEmptyMessageDelayed(MSG_READ_MODEL, DELAY_REPEAT_READ);
            }
        }
    }

    /**
     * 读取手环版本
     */
    public void readFirmwareVersion() {
        boolean readResult = mBluetoothGatt.readCharacteristic(mFirmwareC);
        if (!readResult) {
            mLocalHandler.sendEmptyMessageDelayed(MSG_READ_FIRMWARE, DELAY_REPEAT_READ);
        }
    }

    //==================================================================================
    //=============================== 私有方法（写）=====================================
    //==================================================================================

    /**
     * 写入UTC
     */
    private void writeUTC() {
        mLocalHandler.removeMessages(MSG_WRITE_UTC);
        long time = System.currentTimeMillis() / 1000;
        byte[] timeB = new byte[6];
        timeB[0] = FizzoCMDs.ACTION_TAG_SETTING;
        timeB[1] = FizzoCMDs.CMD_SETTING_UTC;
        timeB[2] = (byte) (time & 0xFF);
        timeB[3] = (byte) ((time >> 8) & 0xFF);
        timeB[4] = (byte) ((time >> 16) & 0xFF);
        timeB[5] = (byte) ((time >> 24) & 0xFF);
        mFizzoPrivateC.setValue(timeB);
        boolean success = mBluetoothGatt.writeCharacteristic(mFizzoPrivateC);
        if (!success) {
            mLocalHandler.sendEmptyMessageDelayed(MSG_WRITE_UTC, DELAY_REPEAT_WRITE);
        } else {
            NotifyManager.getManager().notifyStateChange(ConnectStates.WRITE_UTC_OK);
        }
    }

    /**
     * 控制光管
     *
     * @param open
     */
    private void writeControlLight(final boolean open) {
        mLocalHandler.removeMessages(MSG_WRITE_CONTROL_LIGHT);

        byte[] control = new byte[3];
        control[0] = FizzoCMDs.ACTION_TAG_SETTING;
        control[1] = FizzoCMDs.CMD_SETTING_LIGHT_CONTROL;
        if (open) {
            control[2] = FizzoCMDs.DATA_SETTING_LIGHT_ON;//开光管
        } else {
            control[2] = FizzoCMDs.DATA_SETTING_LIGHT_OFF;//关光管
        }
        mFizzoPrivateC.setValue(control);
        boolean success = mBluetoothGatt.writeCharacteristic(mFizzoPrivateC);
        if (!success) {
            sendMsg(MSG_WRITE_CONTROL_LIGHT, open, DELAY_REPEAT_WRITE);
        }
    }

    /**
     * 让手环反馈震动
     */
    private void writeVibrate() {
        byte[] data = new byte[]{FizzoCMDs.ACTION_TAG_ACTIVE, FizzoCMDs.CMD_SETTING_VIBRATE};
        if (mFizzoPrivateC != null) {
            mFizzoPrivateC.setValue(data);
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(mFizzoPrivateC);
            if (!writeSuccess) {
                mLocalHandler.sendEmptyMessageDelayed(MSG_WRITE_VIBRATE, DELAY_REPEAT_WRITE);
            }
        }
    }

    //==================================================================================
    //=============================== 私有方法 notify ==================================
    //==================================================================================

    /**
     * 设置当指定characteristic值变化时，发出通知
     *
     * @param characteristic
     * @param enable
     */
    private boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        boolean notifySuccess = true;
        boolean writeDescriptor = true;
        notifySuccess = mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
        if (enable) {
            BluetoothGattDescriptor descriptor = characteristic
                    .getDescriptor(UUID.fromString(GattUUIDs.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            writeDescriptor = mBluetoothGatt.writeDescriptor(descriptor);
        }
        return (notifySuccess && writeDescriptor);
    }


    /**
     * 尝试同步心率
     */
    private void notifyHr() {
        if (!setCharacteristicNotification(mHrC, true)) {
            sendMsg(MSG_NOTIFY_HR, null, DELAY_REPEAT_NOTIFY);
        } else {
            NotifyManager.getManager().notifyStateChange(ConnectStates.NOTIFY_HR_OK);
            sendMsg(MSG_READ_FIRMWARE, null, 0);
        }
    }

    private void notifyPrivateService() {
        if (!setCharacteristicNotification(mFizzoPrivateNotifyC, true)) {
            sendMsg(MSG_NOTIFY_PRIVATE_SERVICE, null, DELAY_REPEAT_NOTIFY);
        } else {
            NotifyManager.getManager().notifyStateChange(ConnectStates.NOTIFY_PRIVATE_SERVICE_OK);
        }
    }

    //====================================================================================
    //============================== 私有方法(有关重新连接) ================================
    //====================================================================================

    /**
     * 发送重新尝试连接的命令
     *
     * @param delay 延迟时间
     */
    private void sendRepeatConnectMsg(long delay) {
        if (!mNeedConnect) {
            return;
        }
        mLocalHandler.removeMessages(MSG_REPEAT_CONNECT);
        mLocalHandler.sendEmptyMessageDelayed(MSG_REPEAT_CONNECT, delay);
    }

    /**
     * 尝试重新连接
     * 原因1. 连接错误
     * 原因2. 连接断开，尝试恢复
     * 原因3. 蓝牙开关导致的重新连接
     */
    private void repeatConnect() {
        //三星手机特殊处理
        if (Build.MANUFACTURER.equals("samsung")) {
            Fw.getManager().disConnectDevice();
            Fw.getManager().init(mContext);
            Fw.getManager().replaceConnect(mAddress);
        } else {
            try {
                if (mBluetoothGatt != null) {
                    disConnect();
                    mNeedConnect = true;
                    mBluetoothGatt.close();
                }
                initCallback();
                mBluetoothGatt = mBluetoothAdapter.getRemoteDevice(mAddress)
                        .connectGatt(mContext, false, mGattCallback);
            } catch (IllegalArgumentException ex) {
                sendRepeatConnectMsg(DELAY_REPEAT_CONNECT);
            }
        }
    }


    /**
     * 发送重复发现服务的命令
     *
     * @param delay
     */
    private void sendRepeatDiscoverGatt(long delay) {
        if (!mNeedConnect) {
            return;
        }
        mLocalHandler.removeMessages(MSG_REPEAT_DISCOVER);
        mLocalHandler.sendEmptyMessageDelayed(MSG_REPEAT_DISCOVER, delay);
    }


    /**
     * 再次尝试发现服务
     * 原因1. 请求了发现服务，但是没有响应
     */
    private void repeatDiscoverGatt() {
        mBluetoothGatt.discoverServices();
        sendRepeatDiscoverGatt(DELAY_REPEAT_DISCOVER);
    }

    /**
     * 主动断开连接
     */
    public void disConnect() {
        NotifyManager.getManager().notifyStateChange(ConnectStates.DISCONNECT);
        mNeedConnect = false;
        mIsConnected = false;
        destroyHandler();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }
    }

    /**
     * 销毁Handler
     */
    private void destroyHandler() {
        if (mLocalHandler != null) {
            mLocalHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Method to clear the device cache
     *
     * @return boolean
     */
    public boolean refreshDeviceCache() {
        Log.v(TAG, "refreshDeviceCache");
        if (mBluetoothGatt != null) {
            try {
                BluetoothGatt localBluetoothGatt = mBluetoothGatt;
                Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
                if (localMethod != null) {
                    boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
                    return bool;
                }
            } catch (Exception localException) {
                Log.i(TAG, "An exception occured while refreshing device");
            }
        }
        return false;
    }

    /**
     * 发送消息给Handler
     *
     * @param what
     * @param object
     * @param delay
     */
    private void sendMsg(final int what, final Object object, final long delay) {
        Message msg = new Message();
        msg.what = what;
        msg.obj = object;
        mLocalHandler.sendMessageDelayed(msg, delay);
    }


}
