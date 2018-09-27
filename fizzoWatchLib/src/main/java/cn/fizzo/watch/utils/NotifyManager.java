package cn.fizzo.watch.utils;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;

import cn.fizzo.watch.Fw;
import cn.fizzo.watch.array.NotifyActions;
import cn.fizzo.watch.entity.HrEntity;

/**
 * Created by Raul.Fan on 2017/3/31.
 */

public class NotifyManager {


    private static NotifyManager instance;//唯一实例

    private NotifyManager() {

    }

    /**
     * 获取堆栈管理的单一实例
     */
    public static NotifyManager getManager() {
        if (instance == null) {
            instance = new NotifyManager();
        }
        return instance;
    }

    @SuppressLint("HandlerLeak")
    private
    Handler mNotifyHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                //发布连接状态变化
                case NotifyActions.CONNECT_STATE:
                    Fw.getManager().notifyStateChange((Integer) msg.obj);
                    break;
                //心率变化
                case NotifyActions.NOTIFY_HR:
                    Fw.getManager().notifyHr((HrEntity) msg.obj);
                    break;
                //接收到激活状态
                case NotifyActions.NOTIFY_ACTIVE:
                    Fw.getManager().notifyActive();
                    break;
            }
        }
    };

    /**
     * 发布连接状态变化
     *
     * @param state
     */
    public synchronized void notifyStateChange(final int state) {
        Message msg = mNotifyHandler.obtainMessage(NotifyActions.CONNECT_STATE);
        msg.obj = state;
        mNotifyHandler.sendMessage(msg);
    }

    /**
     * 发布实时心率数据
     */
    public synchronized void notifyRealTimeHr(final HrEntity hrEntity){
        Message msg = mNotifyHandler.obtainMessage(NotifyActions.NOTIFY_HR);
        msg.obj = hrEntity;
        mNotifyHandler.sendMessage(msg);
    }

    /**
     * 接收到激活按键
     */
    public synchronized void notifyActive(){
        Message msg = mNotifyHandler.obtainMessage(NotifyActions.NOTIFY_ACTIVE);
        mNotifyHandler.sendMessage(msg);
    }
}
