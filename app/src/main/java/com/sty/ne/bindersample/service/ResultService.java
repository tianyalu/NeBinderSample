package com.sty.ne.bindersample.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.sty.ne.bindersample.ILoginInterface;

import androidx.annotation.Nullable;

/**
 * @Author: tian
 * @UpdateDate: 2020-08-19 22:14
 */
public class ResultService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new ILoginInterface.Stub() {
            @Override
            public void login() throws RemoteException {

            }

            @Override
            public void loginCallback(boolean loginStatus, String loginUser) throws RemoteException {
                //不用挂起等到暗无天日
                Log.e("sty ---> ", "loginStatus: " + loginStatus + " / loginUser: " + loginUser);
            }
        };
    }
}
