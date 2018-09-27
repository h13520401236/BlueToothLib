package cn.fizzo.watch.utils;

/**
 * Created by Raul.Fan on 2017/5/4.
 */

public class StringU {

    /**
     * byte 数组转化成16进制的字符串
     * @param src
     * @return
     */
    public static String bytesToHexString(final byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }
}
