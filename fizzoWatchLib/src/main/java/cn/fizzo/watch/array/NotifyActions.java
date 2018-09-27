package cn.fizzo.watch.array;

/**
 * Created by Raul.Fan on 2017/3/31.
 * 通知类型
 */
public class NotifyActions {

    //手环连接状态发生变化
    public static final int CONNECT_STATE = 0x01;

    //心率数据发生变化
    public static final int NOTIFY_HR = 0x02;

    //接收到激活信息
    public static final int NOTIFY_ACTIVE = 0x03;
}
