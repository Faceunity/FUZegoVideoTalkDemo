# FuZegoVideoTalkDemo(Android)

## 概述

FuZegoVideoTalkDemo 是集成了 Faceunity 面部跟踪和虚拟道具功能 和 Zego 1V1视频连麦的 Demo。

## 更新SDK

[**Nama SDK发布地址**](https://github.com/Faceunity/FULiveDemoDroid/releases),可查看Nama SDK的所有版本和发布说明。
更新方法为下载Faceunity*.zip解压后替换faceunity模块中的相应库文件。

## SDK使用介绍

 - Faceunity SDK的使用方法请参看 [**Faceunity/FULiveDemoDroid**][1]
 - ZEGO平台直播SDK开发文档

## 集成方法

首先添加相应的SDK库文件与数据文件，然后在连麦界面（实时视频demo中VideoTakeActivity）中完成相应Faceunity SDK界面部分代码（界面不作过多的赘述）。
Zego 提供了两种画面处理的方式，分别是外部采样以及外部滤镜，这里只需了解外部滤镜即可。

### 外部滤镜

#### 环境初始化

##### GL环境初始化

GL线程初始化

```
mGlThread = new HandlerThread("video-filter");
mGlThread.start();
mGlHandler = new Handler(mGlThread.getLooper());
```

GL Context 初始化

```
faceunity.fuCreateEGLContext();
```

##### Faceunity SDK环境初始化

加载Faceunity SDK所需要的数据文件（读取人脸数据文件、美颜数据文件）：

```
InputStream v3 = context.getAssets().open(BUNDLE_v3);
byte[] v3Data = new byte[v3.available()];
v3.read(v3Data);
v3.close();
faceunity.fuSetup(v3Data, null, authpack.A());

/**
 * 加载优化表情跟踪功能所需要加载的动画数据文件anim_model.bundle；
 * 启用该功能可以使表情系数及avatar驱动表情更加自然，减少异常表情、模型缺陷的出现。该功能对性能的影响较小。
 * 启用该功能时，通过 fuLoadAnimModel 加载动画模型数据，加载成功即可启动。该功能会影响通过fuGetFaceInfo获取的expression表情系数，以及通过表情驱动的avatar模型。
 * 适用于使用Animoji和avatar功能的用户，如果不是，可不加载
 */
InputStream animModel = context.getAssets().open(BUNDLE_anim_model);
byte[] animModelData = new byte[animModel.available()];
animModel.read(animModelData);
animModel.close();
faceunity.fuLoadAnimModel(animModelData);

/**
 * 加载高精度模式的三维张量数据文件ardata_ex.bundle。
 * 适用于换脸功能，如果没用该功能可不加载；如果使用了换脸功能，必须加载，否则会报错
 */
InputStream ar = context.getAssets().open(BUNDLE_ardata_ex);
byte[] arDate = new byte[ar.available()];
ar.read(arDate);
ar.close();
faceunity.fuLoadExtendedARData(arDate);
```

#### 实现父类ZegoVideoFilter的虚函数（备注：zego设置外部滤镜必须在初始化sdk的时候设置，在其他地方设置回调会无效）

Zego 是通过 ZegoVideoFilter 类来控制外部滤镜，因此实现父类 ZegoVideoFilter 的虚函数尤为重要。用于ZEGO不支持同时提供纹理以及byte[]数据，因此只能使用faceunity的单输入方式。采用ZEGO的BUFFER_TYPE_ASYNC_I420_MEM模式。

初始化方法的回调，并把Client类给传递过来。

```
@Override
protected void allocateAndStart(Client client) {
    mClient = client;
    mFURenderer.onSurfaceCreated();
}
```

销毁方法的回调

```
@Override
protected void stopAndDeAllocate() {
    mClient.destroy();
    mClient = null;
    mFURenderer.onSurfaceDestroyed();
}
```

#### 处理图像数据

使用faceunity.fuRenderToI420Image来处理画面数据。

```
int fuTex = faceunity.fuRenderToTexture(tex, w, h, mFrameId++, mItemsArray, flags);
```

#### 推流

视频流以纹理ID的形式传给mClient类

```
mZegoClient.queueInputBuffer(index, pixelBuffer.width, pixelBuffer.height, pixelBuffer.stride, pixelBuffer.timestamp_100n);
```