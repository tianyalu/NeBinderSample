package com.sty.ne.bindersample;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends Activity {
    private boolean isStartRemote; //是否开启跨进程通讯
    private ILoginInterface iLoginInterface; //AIDL定义接口

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); //隐藏标题
        //设置全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        //startActivity(new Intent(this, MainActivity.class));

        initBindService();
    }

    //绑定服务
    private void initBindService() {
        Intent intent = new Intent();
        //设置Server应用Action(服务的唯一标识）
        intent.setAction("Binder_Server_Action");

        //设置Server应用包名
        intent.setPackage("com.sty.ne.binder.sample.server");

        //开启绑定服务
        bindService(intent, conn, BIND_AUTO_CREATE);

        //标识跨进程绑定
        isStartRemote = true;
    }

    //点击事件
    public void startQQLoginAction(View view) {
        if(iLoginInterface != null) {
            //调用Server提供的功能、方法
            try {
                iLoginInterface.login();
            } catch (DeadObjectException e1) {  //System.err: android.os.DeadObjectException
                //远端进程挂掉了，重新绑定
                rebindService();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }else {
            Toast.makeText(this, "请先安装QQ应用", Toast.LENGTH_SHORT).show();
        }
    }

    //服务连接
    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //使用Server提供的功能（方法）
            iLoginInterface = ILoginInterface.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //解绑服务，一定要写，否则可能出现服务连接资源异常
        if(isStartRemote) {
            unbindService(conn);
        }
    }

    private void rebindService() {
        unbindService(conn);
        initBindService();
    }
}
