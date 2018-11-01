package com.zego.videotalk;

import android.app.Application;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.faceunity.FURenderer;
import com.faceunity.utils.EffectEnum;
import com.tencent.bugly.crashreport.CrashReport;
import com.zego.videotalk.ui.widgets.FUVideoFilterFactory;
import com.zego.videotalk.utils.PrefUtil;
import com.zego.videotalk.utils.TimeUtil;
import com.zego.zegoliveroom.ZegoLiveRoom;
import com.zego.zegoliveroom.constants.ZegoAvConfig;
import com.zego.zegoliveroom.constants.ZegoConstants;

/**
 * <p>Copyright © 2017 Zego. All rights reserved.</p>
 *
 * @author realuei on 24/10/2017.
 */

public class VideoTalkApplication extends Application {
    private static final String TAG = "VideoTalkApplication";
    final static private long DEFAULT_ZEGO_APP_ID = 1721677906;
    final static private String BUGLY_APP_KEY = "0xad,0xb8,0x22,0x75,0xf4,0x1f,0xb4,0x1b,0xd8,0x59,0x7c,0xc7,0x66,0xdf,0x52,0x7c,0xfb,0x6e,0xd4,0xe4,0xd6,0xd7,0xf3,0x64,0xbd,0xf8,0x15,0x92,0x07,0x61,0x60,0xfa";
    static private VideoTalkApplication sInstance;
    private FURenderer mFURenderer;

    static public VideoTalkApplication getAppContext() {
        return VideoTalkApplication.sInstance;
    }

    /**
     * Called when the application is starting, before any activity, service,
     * or receiver objects (excluding content providers) have been created.
     * Implementations should be as quick as possible (for example using
     * lazy initialization of state) since the time spent in this function
     * directly impacts the performance of starting the first activity,
     * service, or receiver in a process.
     * If you override this method, be sure to call super.onCreate().
     */
    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;

        initUserInfo(); // first

        initCrashReport();  // second


        FURenderer.initFURenderer(this);
        mFURenderer = new FURenderer
                .Builder(this)
                .inputTextureType(0)
                .defaultEffect(EffectEnum.Effect_fengya_ztt_fu.effect())
                .build();
        setupZegoSDK();  // last
    }

    private void initUserInfo() {
        String userId = PrefUtil.getInstance().getUserId();
        String userName = PrefUtil.getInstance().getUserName();
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(userName)) {
            userId = TimeUtil.getNowTimeStr();
            userName = String.format("VT_%s_%s", Build.MODEL.replaceAll(",", "."), userId);

            PrefUtil.getInstance().setUserId(userId);
            PrefUtil.getInstance().setUserName(userName);
        }
    }

    private void initCrashReport() {
        CrashReport.initCrashReport(this, BUGLY_APP_KEY, false);
        CrashReport.setUserId(PrefUtil.getInstance().getUserId());
    }

    private void setupZegoSDK() {

        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();

        liveRoom.setSDKContext(new ZegoLiveRoom.SDKContext() {
            @Override
            public String getSoFullPath() {
                return null;
            }

            @Override
            public String getLogPath() {
                return null;
            }

            @Override
            public Application getAppContext() {
                return VideoTalkApplication.this;
            }
        });


        initZegoSDK(liveRoom);


        ZegoAppHelper.saveLiveRoom(liveRoom);
    }

    private void initZegoSDK(ZegoLiveRoom liveRoom) {

        ZegoLiveRoom.setUser(PrefUtil.getInstance().getUserId(), PrefUtil.getInstance().getUserName());
        ZegoLiveRoom.requireHardwareEncoder(PrefUtil.getInstance().getHardwareEncode());
        ZegoLiveRoom.requireHardwareDecoder(PrefUtil.getInstance().getHardwareDecode());
        ZegoLiveRoom.setTestEnv(PrefUtil.getInstance().getTestEncode());
        //设置外部滤镜---必须在初始化ZegoSDK的时候设置，否则不会回调
        FUVideoFilterFactory fuVideoFilterFactory = new FUVideoFilterFactory(mFURenderer);
        ZegoLiveRoom.setVideoFilterFactory(fuVideoFilterFactory);
        byte[] signKey;
        long appId = 0;
        String strSignKey;
        int currentAppFlavor = PrefUtil.getInstance().getCurrentAppFlavor();
        if (currentAppFlavor == 2) {

            appId = PrefUtil.getInstance().getAppId();
            strSignKey = PrefUtil.getInstance().getAppSignKey();
        } else {
            switch (currentAppFlavor) {
                case 0:
                    appId = ZegoAppHelper.UDP_APP_ID;
                    break;
                case 1:
                    appId = ZegoAppHelper.INTERNATIONAL_APP_ID;
                    break;
                default:
            }
            signKey = ZegoAppHelper.requestSignKey(appId);
            strSignKey = ZegoAppHelper.convertSignKey2String(signKey);
        }

        if (appId == 0 || TextUtils.isEmpty(strSignKey)) {
            appId = DEFAULT_ZEGO_APP_ID;
            PrefUtil.getInstance().setAppId(DEFAULT_ZEGO_APP_ID);

            strSignKey = BUGLY_APP_KEY;
            signKey = ZegoAppHelper.parseSignKeyFromString(strSignKey);
            PrefUtil.getInstance().setAppSignKey(strSignKey);
            Log.d(TAG, "initZegoSDK: strSignKey:" + strSignKey + ", appId:" + appId);
        } else {
            signKey = ZegoAppHelper.parseSignKeyFromString(strSignKey);
        }

        boolean success = liveRoom.initSDK(appId, signKey);

        if (PrefUtil.getInstance().getAppWebRtc()) {
            liveRoom.setLatencyMode(ZegoConstants.LatencyMode.Low3);
        }

        if (!success) {
            Toast.makeText(this, R.string.vt_toast_init_sdk_failed, Toast.LENGTH_LONG).show();
        } else {
            ZegoAvConfig config;
            int level = PrefUtil.getInstance().getLiveQuality();
            if (level < 0 || level > ZegoAvConfig.Level.SuperHigh) {
                config = new ZegoAvConfig(ZegoAvConfig.Level.High);
                config.setVideoBitrate(PrefUtil.getInstance().getLiveQualityBitrate());
                config.setVideoFPS(PrefUtil.getInstance().getLiveQualityFps());
                int resolutionLevel = PrefUtil.getInstance().getLiveQualityResolution();

                String resolutionText = getResources().getStringArray(R.array.zg_resolutions)[resolutionLevel];
                String[] strWidthHeight = resolutionText.split("x");

                int height = Integer.parseInt(strWidthHeight[0].trim());
                int width = Integer.parseInt(strWidthHeight[1].trim());
                config.setVideoEncodeResolution(width, height);
                config.setVideoCaptureResolution(width, height);
            } else {
                config = new ZegoAvConfig(level);
            }
            liveRoom.setAVConfig(config);

        }


    }

    public void reInitZegoSDK() {
        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();
        liveRoom.unInitSDK();
        setupZegoSDK();

        Toast.makeText(this, R.string.zg_toast_reinit_sdk_success, Toast.LENGTH_LONG).show();
    }

    public FURenderer getFURenderer() {
        return mFURenderer;
    }
}
