// ILoginInterface.aidl
//服务端和客户端的aidl包名必须一致！！！
package com.sty.ne.bindersample;

// Declare any non-default types here with import statements

interface ILoginInterface {

    //登录
    void login();

    //登录返回
    void loginCallback(boolean loginStatus, String loginUser);
}
