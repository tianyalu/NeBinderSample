# `Binder`核心原理与架构设计+第三方登录客户端实现

[TOC]

## 一、`Binder`核心原理

### 1.1 初识`Binder`

#### 1.1.1 什么是`Binder`?

`Binder`可以实现进行与进程之间的通信。它是`Android`底层系统的一个特色，很好地解决了进程间通信的问题。

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/binder.png)

`Binder`的例子:

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/binder_sample.png)

#### 1.1.2 `Binder`的由来

`Linux`内核进程间通信尝试方案（未采取）：

> 1. 管道：半双工，消耗性能；
> 2. 共享内存：使多个进行可以访问同一块内存空间，管理混乱；
> 3. `Socket`：适合用于网络通讯，但对于进程间通讯，显然不适用。

多进程的共享内存通讯方案如下图所示，其中涉及到两次内存拷贝：

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/memory_share_process.png)

#### 1.1.3 `Binder`四个重要角色

> 1. `Server`
> 2. `Client`
> 3. `ServiceManager`
> 4. `Binder`驱动

其中`Server`和`Client`的身份是相对的。一个进程的`Binder`线程数默认最大值为**16**，超过该数量时会被阻塞，进入等待状态。

`Binder`驱动：

> * 定义：一种虚拟设备驱动；
> * 作用：连接`Server`进程、`Client`进程和`Service Manager`的桥梁；
> * 具体实现原理：内存映射，即内部调用了`mmap()`函数；
> * 实际用途：创建数据接收的缓存空间；地址映射。

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/binder_4roles_relationship.png)

#### 1.1.4 `Binder`四个重要对象

> 1. `IBinder`：一个接口，代表了一种跨进程通讯的能力、功能，实现该接口即可；
> 2. `IInterface`：代表`Server`进程所具有的能力，能提供什么样的方法；
> 3. `Binder`：`Java`层的类，代表`Binder`的本地对象`BinderProxy`；
> 4. `stub`：`Binder`的本地对象，实现了`IInterface`接口，表明其具备服务端承诺给客户端的能力。

#### 1.1.5 `Binder`通讯机制流程

`Binder`通讯机制流程如下图所示：

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/binder_communication_process.png)

架构设计如下图所示：

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/binder_communication_structure.png)

#### 1.1.6 `Binder`优点：

> 1. **高效**：`Binder`数据拷贝只需一次，而管道、消息队列、`Socket`都需要两次；通过驱动在内核空间拷贝数据，不需要额外同步处理；
> 2. **安全性高**：`Binder`机制为每个进程分配了`UID/PID`来作为鉴别身份的标识，在`Binder`通信时会根据`UID/PID`进行有效性检测（传统的进程通信方式对于通信双方的身份并没有做出严格的验证，如`Socket`通信`ip`地址是客户端手动输入，容易出现伪造）；
> 3. **使用简单**：采用`Client/Server`架构，实现面向对象的调用方式，即在使用`Binder`时，就和调用一个本地对象实例一样。

### 1.2 `Binder`深入了解

#### 1.2.1 `Binder`通讯机制核心原理

`Binder`通讯时，首先`Client`端发送消息后，通过`BinderProxy`拷贝数据到内核的缓存区，该缓存区与`Binder`创建的接收缓存区有一定的映射关系，而`Binder`创建的接收缓存区与`Server`端内存是直接的映射关系，所以整个操作流程只需要一次内存拷贝就可以了。 `Binder`通讯核心原理如下图所示：

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/binder_communication_core_theory.png)

`Binder`通讯机制核心流程如下图所示：

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/binder_communication_whole_process.png)

#### 1.2.2 内核层分析

以`Android 9.0`源码分析。

##### 1.2.2.1 打开`Binder`设备

[https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/cmds/servicemanager/service_manager.c](https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/cmds/servicemanager/service_manager.c)

```c
//... 400行   
if (argc > 1) { 
        driver = argv[1];
    } else {
        driver = "/dev/binder"; //打开Binder设备文件，返回设备文件描述符
    }

    bs = binder_open(driver, 128*1024); //打开Binder，并且开启一个128K的内存映射
//...
```

##### 1.2.2.2 `Buffer`创建（用于进程间数据传递）

参考：1.2.2.1

##### 1.2.2.3  开辟内存映射（128K）

参考：1.2.2.1

[https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/cmds/servicemanager/binder.c](https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/cmds/servicemanager/binder.c)

```c
//... 113行
bs->fd = open(driver, O_RDWR | O_CLOEXEC);  //打开Binder设备驱动
if (bs->fd < 0) {
  fprintf(stderr,"binder: cannot open %s (%s)\n",
          driver, strerror(errno));
  goto fail_open;
}

if ((ioctl(bs->fd, BINDER_VERSION, &vers) == -1) ||
    (vers.protocol_version != BINDER_CURRENT_PROTOCOL_VERSION)) {
  fprintf(stderr,
          "binder: kernel driver version (%d) differs from user space version (%d)\n",
          vers.protocol_version, BINDER_CURRENT_PROTOCOL_VERSION);
  goto fail_open;
}
bs->mapsize = mapsize; 
bs->mapped = mmap(NULL, mapsize, PROT_READ, MAP_PRIVATE, bs->fd, 0); //内存映射
if (bs->mapped == MAP_FAILED) {
  fprintf(stderr,"binder: cannot map device (%s)\n",
          strerror(errno));
  goto fail_map;
}
```

[https://www.androidos.net.cn/android/9.0.0_r8/xref/device/google/cuttlefish_kernel/4.4-x86_64/System.map](https://www.androidos.net.cn/android/9.0.0_r8/xref/device/google/cuttlefish_kernel/4.4-x86_64/System.map)

```bash
#... 25305行
ffffffff815dbec0 t binder_release
ffffffff815dbf50 t binder_mmap  # 系统在启动时开启的内存映射
ffffffff815dc060 t binder_vma_close
```

##### 1.2.2.4 `ServiceManager`启动

[https://www.androidos.net.cn/android/9.0.0_r8/xref/system/core/rootdir/init.rc](https://www.androidos.net.cn/android/9.0.0_r8/xref/system/core/rootdir/init.rc)

```bash
#... 334行
# start essential services
start logd
start servicemanager # 启动servcieManager 服务
start hwservicemanager
start vndservicemanager
```

##### 1.2.2.5 打包`Parcel`中，数据写入`binder`设备，`copy_from_user`.

打包`Parcel`:

[https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IServiceManager.cpp](https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IServiceManager.cpp)

```c++
//... 196行  数据打包的过程
virtual status_t addService(const String16& name, const sp<IBinder>& service,
                            bool allowIsolated, int dumpsysPriority) {
  Parcel data, reply;
  data.writeInterfaceToken(IServiceManager::getInterfaceDescriptor());
  data.writeString16(name);
  data.writeStrongBinder(service);
  data.writeInt32(allowIsolated ? 1 : 0);
  data.writeInt32(dumpsysPriority);
  status_t err = remote()->transact(ADD_SERVICE_TRANSACTION, data, &reply);
  return err == NO_ERROR ? reply.readExceptionCode() : err;
}
```

数据写入`binder`设备：

[https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IPCThreadState.cpp](https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IPCThreadState.cpp)

```c++
//... 643行
//将Parcel中的数据封装成结构体TransactionData并且写入到mOut中
err = writeTransactionData(BC_TRANSACTION, flags, handle, code, data, NULL); 

if (err != NO_ERROR) {
  if (reply) reply->setError(err);
  return (mLastError = err);
}

if ((flags & TF_ONE_WAY) == 0) {
	//...
  if (reply) {
    err = waitForResponse(reply); //将数据写入到Binder的设备中并且等待返回结果
  } else {
    Parcel fakeReply;
    err = waitForResponse(&fakeReply);
  }
	//...
} else {
  err = waitForResponse(NULL, NULL);
}
```

##### 1.2.2.6 服务注册，添加到链表`svclist`中

[https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/cmds/servicemanager/service_manager.c](https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/cmds/servicemanager/service_manager.c)

```c++
//... 223行
if (!svc_can_register(s, len, spid, uid)) { //权限的检查，看是否有注册过service的权限
  ALOGE("add_service('%s',%x) uid=%d - PERMISSION DENIED\n",
        str8(s, len), handle, uid);
  return -1;
}

si = find_svc(s, len); //在svc_list链表上查找看服务是否已经注册过
if (si) {
  if (si->handle) {
    ALOGE("add_service('%s',%x) uid=%d - ALREADY REGISTERED, OVERRIDE\n",
          str8(s, len), handle, uid);
    svcinfo_death(bs, si);
  }
  si->handle = handle;
} else { //如果没有注册，则分配一个服务管理的数据结构 svcinfo,并将其添加到链表中
  si = malloc(sizeof(*si) + (len + 1) * sizeof(uint16_t));
  if (!si) {
    ALOGE("add_service('%s',%x) uid=%d - OUT OF MEMORY\n",
          str8(s, len), handle, uid);
    return -1;
  }
  si->handle = handle;
  si->len = len;
  memcpy(si->name, s, (len + 1) * sizeof(uint16_t));
  si->name[len] = '\0';
  si->death.func = (void*) svcinfo_death;
  si->death.ptr = si;
  si->allow_isolated = allow_isolated;
  si->dumpsys_priority = dumpsys_priority;
  si->next = svclist; //将代表该服务的结构插入到链表中
  svclist = si;
}

binder_acquire(bs, handle); //增加Binder的应用计数
binder_link_to_death(bs, handle, &si->death); //若该服务退出，则通知ServiceManager
return 0;
```

##### 1.2.2.7 定义主线程中的线程池

[https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IPCThreadState.cpp](https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IPCThreadState.cpp)

```c++
//... 555行
void IPCThreadState::joinThreadPool(bool isMain)
{
  LOG_THREADPOOL("**** THREAD %p (PID %d) IS JOINING THE THREAD POOL\n", (void*)pthread_self(), getpid());

  mOut.writeInt32(isMain ? BC_ENTER_LOOPER : BC_REGISTER_LOOPER);

  status_t result;
  do {
    processPendingDerefs();
    // now get the next command to be processed, waiting if necessary
    result = getAndExecuteCommand();

    if (result < NO_ERROR && result != TIMED_OUT && result != -ECONNREFUSED && result != -EBADF) {
      ALOGE("getAndExecuteCommand(fd=%d) returned unexpected error %d, aborting",
            mProcess->mDriverFD, result);
      abort();
    }

    // Let this thread exit the thread pool if it is no longer
    // needed and it is not the main process thread.
    if(result == TIMED_OUT && !isMain) {
      break;
    }
  } while (result != -ECONNREFUSED && result != -EBADF);

  LOG_THREADPOOL("**** THREAD %p (PID %d) IS LEAVING THE THREAD POOL err=%d\n",
                 (void*)pthread_self(), getpid(), result);

  mOut.writeInt32(BC_EXIT_LOOPER);
  talkWithDriver(false);
}
```

##### 1.2.2.8 循环从`mIn`和`mOut`中取出读写请求，发到`Binder`设备中

[https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IPCThreadState.cpp](https://www.androidos.net.cn/android/9.0.0_r8/xref/frameworks/native/libs/binder/IPCThreadState.cpp)

```c++
//... 774行
IPCThreadState::IPCThreadState()
    : mProcess(ProcessState::self()),
      mStrictModePolicy(0),
      mLastTransactionBinderFlags(0)
{
    pthread_setspecific(gTLS, this);
    clearCaller();
    mIn.setDataCapacity(256); //输入分配256的buffer字节空间
    mOut.setDataCapacity(256); //输入分配256的buffer字节空间
}

//... 905行
// Is the read buffer empty?
const bool needRead = mIn.dataPosition() >= mIn.dataSize(); //检查是否有读数据的请求
// We don't want to write anything if we are still reading
// from data left in the input buffer and the caller
// has requested to read the next data.
const size_t outAvail = (!doReceive || needRead) ? mOut.dataSize() : 0; //检查是否有写的请求
//... 951行
#if defined(__ANDROID__)
        if (ioctl(mProcess->mDriverFD, BINDER_WRITE_READ, &bwr) >= 0) //将读写的请求发送到Binder中
            err = NO_ERROR;
        else
            err = -errno;
#else
        err = INVALID_OPERATION;
#endif
```

## 三、实践

本文是`Binder`样例客户端程序，结合`AIDL`通过服务的**双向绑定**实现第三方登录调用服务端功能。服务端程序参考：[NeBinderSampleServer](https://github.com/tianyalu/NeBinderSampleServer)

### 3.1 实现效果

实现效果如下图所示：

![image](https://github.com/tianyalu/NeBinderSample/raw/master/show/show.gif)

### 3.2 客户端实现步骤

#### 3.2.1 生成`AIDL`

```java
//服务端和客户端的aidl包名必须一致！！！
package com.sty.ne.bindersample;
interface ILoginInterface {
    //登录
    void login();
    //登录返回
    void loginCallback(boolean loginStatus, String loginUser);
}
```

#### 3.2.2 返回结果的服务的定义与注册

定义：

```java
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
```

在清单文件中注册：

```xml
<application>
  <!-- 是否能被系统实例化   是否能被其它应用隐式调用 应用程序需要使用该服务的话需要自动创建名字叫remote_server的进程-->
  <service
           android:name=".service.ResultService"
           android:enabled="true"
           android:exported="true"
           android:process=":remote">
    <intent-filter>
      <action android:name="Binder_Client_Action"/>
    </intent-filter>
  </service>
</application>
```

#### 3.2.3 绑定服务

注意有绑定也必须有解绑：

```java
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

@Override
protected void onDestroy() {
  super.onDestroy();
  //解绑服务，一定要写，否则可能出现服务连接资源异常
  if(isStartRemote) {
    unbindService(conn);
  }
}
```

#### 3.2.4 调起第三方登录服务

```java
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

private void rebindService() {
  unbindService(conn);
  initBindService();
}
```

### 3.3 服务端实现步骤

#### 3.3.1 生成`AIDL`

```java
//服务端和客户端的aidl包名必须一致！！！
package com.sty.ne.bindersample;
interface ILoginInterface {
    //登录
    void login();
    //登录返回
    void loginCallback(boolean loginStatus, String loginUser);
}
```

#### 3.3.2 登录服务的定义与注册

定义：

```java
public class LoginService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new ILoginInterface.Stub() {
            @Override
            public void login() throws RemoteException {
                Log.e("sty --> ", "Binder_Server_Login_Service");
                //单向通信，真实的项目中跨进程都是双向通信，双向服务绑定的
                serviceStartActivity();
            }

            @Override
            public void loginCallback(boolean loginStatus, String loginUser) throws RemoteException {
            }
        };
    }

    //启动界面
    private void serviceStartActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
```

在清单文件中注册：

```xml
<application>
<!-- ... -->
  <!--是否能被系统实例化 是否能被其它应用隐式调用 应用程序需要使用该服务的话需要自动创建名字叫remote_server的进程-->
  <service android:name=".service.LoginService"
           android:enabled="true"
           android:exported="true"
           android:process=":remote_server">
    <intent-filter>
      <action android:name="Binder_Server_Action"/>
    </intent-filter>
  </service>
</application>
```

#### 3.3.3 绑定服务

注意有绑定也必须有解绑：

```java
private ServiceConnection conn = new ServiceConnection() {
  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    iLoginInterface = ILoginInterface.Stub.asInterface(service);
  }

  @Override
  public void onServiceDisconnected(ComponentName name) {
  }
};

private void initBindService() {
  Intent intent = new Intent();
  intent.setAction("Binder_Client_Action");
  intent.setPackage("com.sty.ne.bindersample");
  bindService(intent, conn, BIND_AUTO_CREATE);
  isStartRemote = true;
}

@Override
protected void onDestroy() {
  super.onDestroy();
  if(isStartRemote) {
    unbindService(conn);
  }
}
```

#### 3.3.4 模拟登录

```java
public void startLogin(View view) {
  final String name = etName.getText().toString().trim();
  final String pwd = etPwd.getText().toString().trim();

  //... 判空与对话框展示
 
  new Thread(new Runnable() {
    @Override
    public void run() {
      SystemClock.sleep(2000);
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          try {
            boolean loginStatus = false;
            if(NAME.equals(name) && PWD.equals(pwd)) {
              Toast.makeText(MainActivity.this, "QQ登录成功", Toast.LENGTH_SHORT).show();
              loginStatus = true;
              //登录成功，销毁界面
              finish();
            }else {
              Toast.makeText(MainActivity.this, "QQ登录失败", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
            //告知Client，登录结果返回
            iLoginInterface.loginCallback(loginStatus, name);
          } catch (RemoteException e) {
            e.printStackTrace();
          }
        }
      });
    }
  }).start();
}
```











