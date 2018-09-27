package cn.fizzo.watch.entity;

/**
 * Created by Raul.Fan on 2017/2/8.
 * 心率数据模型
 */
public class HrEntity {

    public int hr;   //心率
    public int stepCount; //当前步数
    public int cadence;  //当前步频
    public int speed;  //速度

    public HrEntity(int hr, int stepCount, int cadence, int speed) {
        this.hr = hr;
        this.stepCount = stepCount;
        this.cadence = cadence;
        this.speed = speed;
    }

    public int getHr() {
        return hr;
    }

    public void setHr(int hr) {
        this.hr = hr;
    }

    public int getStepCount() {
        return stepCount;
    }

    public void setStepCount(int stepCount) {
        this.stepCount = stepCount;
    }

    public int getCadence() {
        return cadence;
    }

    public void setCadence(int cadence) {
        this.cadence = cadence;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }
}
