package cn.fizzo.watch.subject;

import java.util.ArrayList;
import java.util.List;

import cn.fizzo.watch.observer.NotifyActiveListener;

/**
 * Created by Raul.Fan on 2017/3/29.
 */
public class NotifyActiveSubjectImp implements NotifyActiveSubject {


    private List<NotifyActiveListener> mActiveObservers = new ArrayList<NotifyActiveListener>();


    @Override
    public void attach(NotifyActiveListener observer) {
        mActiveObservers.add(observer);
    }

    @Override
    public void detach(NotifyActiveListener observer) {
        mActiveObservers.remove(observer);
    }

    @Override
    public void notifyActive() {
        for (NotifyActiveListener observer : mActiveObservers) {
            observer.notifyActive();
        }
    }
}
