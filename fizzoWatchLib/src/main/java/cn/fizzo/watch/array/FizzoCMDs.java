package cn.fizzo.watch.array;

/**
 * Created by Raul.Fan on 2017/4/1.
 * Fizzo 自定义协议
 */
public class FizzoCMDs {


    //* 设置相关
    public static final byte ACTION_TAG_SETTING = (byte) 0xdd;//TAG
    public static final byte CMD_SETTING_UTC = (byte) 0xfa;//CMD  设置手环UTC
    public static final byte CMD_SETTING_HR_RANGE = (byte) 0xfb;//CMD  设置用户个人心率区间
    public static final byte CMD_SETTING_LIGHT_CONTROL = (byte) 0xfc;//CMD 光管控制
    public static final byte DATA_SETTING_LIGHT_OFF = (byte)0x00;//data 关光管
    public static final byte DATA_SETTING_LIGHT_ON = (byte)0x01;//data 开光管

    //* 激活
    public static final byte ACTION_TAG_ACTIVE = (byte) 0xaa;//TAG
    public static final byte CMD_SETTING_VIBRATE = (byte) 0xfe;//发送振动请求

    public final static byte STATUS_SETTING_SUCCESS = 0;
}
