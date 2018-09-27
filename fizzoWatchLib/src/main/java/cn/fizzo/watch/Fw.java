package cn.fizzo.watch;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.util.UUID;

import cn.fizzo.watch.array.GattUUIDs;
import cn.fizzo.watch.entity.ConnectEntity;
import cn.fizzo.watch.entity.HrEntity;
import cn.fizzo.watch.observer.NotifyActiveListener;
import cn.fizzo.watch.observer.ConnectListener;
import cn.fizzo.watch.observer.NotifyHrListener;
import cn.fizzo.watch.subject.NotifyActiveSubjectImp;
import cn.fizzo.watch.subject.ConnectSubjectImp;
import cn.fizzo.watch.subject.NotifyHrSubjectImp;
import cn.fizzo.watch.utils.ExceptionU;
import cn.fizzo.watch.utils.Log;

/**
 * Created by Raul.Fan on 2017/3/28.
 */

public class Fw {

    private static final String TAG = "Fw";

    private static Fw instance;//唯一实例

    private Context mContext;

    private boolean init = false;
    private static boolean debug;//是否打印日志

    private String mConnectMac;//连接的地址
    private ConnectEntity mConnectE;//连接对象

    //蓝牙适配器
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    private ConnectSubjectImp mConnectSub;//连接状态变化的订阅管理
    private NotifyHrSubjectImp mNotifyHrSub;//心率变化的订阅管理
    private NotifyActiveSubjectImp mNotifyActiveSub;//激活状态变化订阅管理

    private Fw() {
    }

    /**
     * 获取堆栈管理的单一实例
     */
    public static Fw getManager() {
        if (instance == null) {
            instance = new Fw();
        }
        return instance;
    }

    /**
     * 初始化
     *
     * @param context
     * @return
     */
    public boolean init(Context context) {
        if (context == null) {
            ExceptionU.ThrowInitNullPointException();
        }
        mContext = context;
        initBleAdapter();
        initLeScanCallBack();
        mConnectMac = "";
        mConnectE = null;
        init = true;
        mConnectSub = new ConnectSubjectImp();
        mNotifyHrSub = new NotifyHrSubjectImp();
        mNotifyActiveSub = new NotifyActiveSubjectImp();
        return true;
    }

    /**
     * 销毁自身对象
     */
    public void disConnectDevice() {
        if (mConnectE!= null) {
            mConnectE.disConnect();
        }
        mConnectE = null;
    }

    /**
     * 恢复
     */
    public void recovery(){
        disConnectDevice();
        initBleAdapter();
        if (!mConnectMac.equals("")){
            mBluetoothAdapter.startLeScan(new UUID[]{GattUUIDs.UUID_HEART_RATE_SERVICE}, mLeScanCallback);
        }
    }
    /**
     * 增加一个新的连接
     *
     * @param address
     */
    public void addNewConnect(final String address) {
        if (mConnectE == null) {
            mConnectE = new ConnectEntity(mContext, address, mBluetoothAdapter);
        } else {
            if (!mConnectE.mAddress.equals(address)) {
                mConnectE.disConnect();
                mConnectE = new ConnectEntity(mContext, address, mBluetoothAdapter);
            }
        }
        mConnectMac = address;
    }

    /**
     * 控制光管
     */
    public void controlLight(boolean open){
        if (mConnectE != null && mConnectE.mIsConnected){
            mConnectE.controlLight(open);
        }
    }

    /**
     * 振动
     */
    public void vibrate(){
        if (mConnectE != null && mConnectE.mIsConnected){
            mConnectE.vibrate();
        }
    }

    /**
     * 重新建立连接
     *
     * @param address
     */
    public void replaceConnect(final String address) {
        mConnectMac = address;
        if (!mConnectMac.equals("")) {
            mBluetoothAdapter.startLeScan(new UUID[]{GattUUIDs.UUID_HEART_RATE_SERVICE}, mLeScanCallback);
        }
    }

    /**
     * 向订阅者发送连接状态变化通知
     * @param connectState
     */
    public void notifyStateChange(final int connectState){
        mConnectSub.notify(connectState);
    }

    /**
     * 通知心率变化
     * @param hrEntity
     */
    public void notifyHr(final HrEntity hrEntity){
        mNotifyHrSub.notify(hrEntity);
    }

    public void notifyActive(){
        mNotifyActiveSub.notifyActive();
    }

    /**
     * 订阅者注册连接状态变化通知
     * @param observer
     */
    public void registerConnectListener(ConnectListener observer){
        mConnectSub.attach(observer);
    }

    /**
     * 订阅者注销连接状态变化通知
     * @param observer
     */
    public void unRegisterConnectListener(ConnectListener observer){
        mConnectSub.detach(observer);
    }

    /**
     * 订阅者注册心率状态变化通知
     * @param observer
     */
    public void registerNotifyHrListener(NotifyHrListener observer){
        mNotifyHrSub.attach(observer);
    }

    /**
     * 订阅者注销心率状态变化通知
     * @param observer
     */
    public void unRegisterNotifyHrListener(NotifyHrListener observer){
        mNotifyHrSub.detach(observer);
    }

    /**
     * 订阅者注册激活状态变化通知
     * @param observer
     */
    public void registerNotifyActiveListener(NotifyActiveListener observer){
        mNotifyActiveSub.attach(observer);
    }

    /**
     * 订阅者注销激活状态变化通知
     * @param observer
     */
    public void unRegisterNotifyActiveListener(NotifyActiveListener observer){
        mNotifyActiveSub.detach(observer);
    }

    /**
     * 查看是否打印debug信息
     * @return
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * 设置是否打印debug信息
     * @param debug
     */
    public void setDebug(boolean debug) {
        Fw.debug = debug;
    }


    /**
     * 开始扫描
     */
    @SuppressLint("NewApi")
    public void startScan(){
        mBluetoothAdapter.startLeScan(new UUID[]{GattUUIDs.UUID_HEART_RATE_SERVICE}, mLeScanCallback);
    }

    /**
     * 停止扫描
     */
    @SuppressLint("NewApi")
    public void stopScan(){
        if (mBluetoothAdapter != null && mLeScanCallback!=null){
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }


    /**
     * 获取连接对象
     * @return
     */
    public ConnectEntity getConnectDevice(){
        return mConnectE;
    }

    /**********************************************************************************************
     * /*                                                                                            *
     * /*                                       私有方法                                             *
     * /*                                                                                            *
     * /**********************************************************************************************
     * <p>
     * /**
     * 初始化蓝牙适配器
     */
    @SuppressLint("NewApi")
    private void initBleAdapter() {
        bluetoothManager = (BluetoothManager) mContext.getSystemService(mContext.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            ExceptionU.ThrowInitNullPointException();
        }
    }

    /**
     * 初始化蓝牙扫描回调
     */
    @SuppressLint("NewApi")
    private void initLeScanCallBack(){
        mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                Log.v(TAG, "onLeScan:<" + device.getAddress() + ">");
                if (device.getAddress().equals(mConnectMac)){
                    addNewConnect(device.getAddress());
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }
        };
    }
}
