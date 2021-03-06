package com.zego.videotalk.ui.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.faceunity.nama.FURenderer;
import com.faceunity.nama.ui.BeautyControlView;
import com.zego.videotalk.R;
import com.zego.videotalk.VideoTalkApplication;
import com.zego.videotalk.ZegoAppHelper;
import com.zego.videotalk.adapter.VideoLiveViewAdapter;
import com.zego.videotalk.ui.widgets.VideoLiveView;
import com.zego.videotalk.utils.AppLogger;
import com.zego.videotalk.utils.EntityConversion;
import com.zego.videotalk.utils.PrefUtil;
import com.zego.videotalk.utils.SystemUtil;
import com.zego.videotalk.utils.TimeUtil;
import com.zego.zegoliveroom.ZegoLiveRoom;
import com.zego.zegoliveroom.callback.IZegoLivePlayerCallback;
import com.zego.zegoliveroom.callback.IZegoLivePublisherCallback;
import com.zego.zegoliveroom.callback.IZegoLoginCompletionCallback;
import com.zego.zegoliveroom.callback.IZegoRoomCallback;
import com.zego.zegoliveroom.constants.ZegoAvConfig;
import com.zego.zegoliveroom.constants.ZegoConstants;
import com.zego.zegoliveroom.constants.ZegoVideoViewMode;
import com.zego.zegoliveroom.entity.AuxData;
import com.zego.zegoliveroom.entity.ZegoPlayStreamQuality;
import com.zego.zegoliveroom.entity.ZegoPublishStreamQuality;
import com.zego.zegoliveroom.entity.ZegoStreamInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VideoTalkActivity extends AppCompatActivity implements FURenderer.OnTrackingStatusChangedListener {
    private static final String TAG = "VideoTalkActivity";
    private VideoLiveView mBigVideoLiveView;
    private RecyclerView mVideoLiveViewGrid;
    private VideoLiveViewAdapter videoLiveViewAdapter;
    private String mPublishStreamId = "";
    private boolean mIsLoginRoom;   // 是否正在登录房间
    private boolean mHasLoginRoom;  // 是否已成功登录房间
    private int mPosition; //当前切换的视频下标记录
    private boolean mIsFrontCamera = true;

    private FURenderer mFURenderer;
    private TextView mTextView;
    private BeautyControlView beautyControlView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_talk);
        beautyControlView = (BeautyControlView) findViewById(R.id.faceunity_control);
        mTextView = (TextView) findViewById(R.id.tv_track_text);
        // 禁止手机休眠
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int orientation = getIntent().getIntExtra("orientation", 0);
        if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
            setRequestedOrientation(orientation == Surface.ROTATION_0 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        } else {
            setRequestedOrientation(orientation == Surface.ROTATION_90 ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        }
        // 获取屏幕比例
        boolean proportion = SystemUtil.getResolutionProportion(this);
        ZegoAvConfig config = VideoTalkApplication.getAppContext().config;
        if ((proportion && config.getVideoCaptureResolutionWidth() < config.getVideoCaptureResolutionHeight()) ||
                (!proportion && config.getVideoCaptureResolutionWidth() > config.getVideoCaptureResolutionHeight())) {
            // 如果不为横屏比例, 则切换分辨率
            int height = config.getVideoCaptureResolutionWidth();
            int width = config.getVideoCaptureResolutionHeight();
            config.setVideoEncodeResolution(width, height);
            config.setVideoCaptureResolution(width, height);
            ZegoAppHelper.getLiveRoom().setAVConfig(config);
        }
        initCtrls();

        if (savedInstanceState != null) {
            // create from saved Instance
            // TODO
        } else {
            setupCallback();
            loginRoom();
        }

        initFURender();
        initPhoneCallingListener();
    }

    private void initFURender() {
        mFURenderer = VideoTalkApplication.getAppContext().getFURenderer();
        if (mFURenderer == null) {
            beautyControlView.setVisibility(View.GONE);
        } else {
            beautyControlView.setOnFaceUnityControlListener(mFURenderer);
        }
    }

    protected PhoneStateListener mPhoneStateListener = null;
    protected boolean mHostHasBeenCalled = false;

    /**
     * 电话状态监听.
     */
    protected void initPhoneCallingListener() {
        mPhoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                switch (state) {
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (mHostHasBeenCalled) {
                            mHostHasBeenCalled = false;

                            //收到PhoneStateManager回调的 TelephonyManager.CALL_STATE_IDLE 事件时，可能系统的设备还未释放完毕，需要延迟2s再调用mZegoLiveRoom.resumeModule(ZegoConstants.ModuleType.AUDIO)
                            getWindow().getDecorView().postDelayed(new Runnable() {
                                public void run() {
                                    ZegoAppHelper.getLiveRoom().resumeModule(ZegoConstants.ModuleType.AUDIO);
                                }
                            }, 2000);
                        }

                        break;
                    case TelephonyManager.CALL_STATE_RINGING:

                        mHostHasBeenCalled = true;
                        // 来电，暂停音频模块
                        ZegoAppHelper.getLiveRoom().pauseModule(ZegoConstants.ModuleType.AUDIO);
                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        break;
                }
            }
        };

        TelephonyManager tm = (TelephonyManager) getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * Take care of popping the fragment back stack or finishing the activity
     * as appropriate.
     */
    @Override
    public void onBackPressed() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("")   //R.string.vt_dialog_logout_title
                .setMessage(R.string.vt_dialog_logout_message)
                .setPositiveButton(R.string.vt_dialog_btn_positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        VideoTalkActivity.super.onBackPressed();

                        logoutRoom();
                    }
                })
                .setNegativeButton(R.string.vt_dialog_btn_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Nothing to do
                    }
                })
                .create();
        dialog.show();
    }

    @TargetApi(16)
    private void initCtrls() {
        ButtonClickListener clickListener = new ButtonClickListener();

        ImageButton cameraBtn = (ImageButton) findViewById(R.id.vt_btn_camera);
        cameraBtn.setOnClickListener(clickListener);

        ImageButton cameraFrontBtn = (ImageButton) findViewById(R.id.vt_btn_front_camera);
        cameraFrontBtn.setOnClickListener(clickListener);


        ImageButton micBtn = (ImageButton) findViewById(R.id.vt_btn_mic);
        micBtn.setOnClickListener(clickListener);

        ImageButton speakerBtn = (ImageButton) findViewById(R.id.vt_btn_speaker);
        speakerBtn.setOnClickListener(clickListener);

        Button logBtn = (Button) findViewById(R.id.vt_btn_show_log);
        logBtn.setOnClickListener(clickListener);

        ImageButton closeBtn = (ImageButton) findViewById(R.id.vt_btn_close);
        closeBtn.setOnClickListener(clickListener);

        mBigVideoLiveView = (VideoLiveView) findViewById(R.id.vt_big_video_window);
        mVideoLiveViewGrid = (RecyclerView) findViewById(R.id.vt_normal_video_window_set);
        videoLiveViewAdapter = new VideoLiveViewAdapter(this);
        //设置Item增加、移除动画
        mVideoLiveViewGrid.setItemAnimator(new DefaultItemAnimator());

        ((SimpleItemAnimator) mVideoLiveViewGrid.getItemAnimator()).setSupportsChangeAnimations(false);
        mVideoLiveViewGrid.getItemAnimator().setChangeDuration(0);
        videoLiveViewAdapter.setHasStableIds(true);
        mVideoLiveViewGrid.setAdapter(videoLiveViewAdapter);

        videoLiveViewAdapter.setOnItemClickListener(new VideoLiveViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
                exchangeViewToFullScreen(position);
            }
        });
        //设置布局管理器
        mVideoLiveViewGrid.setLayoutManager(new GridLayoutManager(this, 4));

    }


    private void exchangeViewToFullScreen(int position) {
        VideoLiveViewAdapter adapter = videoLiveViewAdapter;

        ZegoStreamInfo streamInfo = (ZegoStreamInfo) adapter.getItem(position);
        if (TextUtils.isEmpty(streamInfo.streamID)) return;     // is stub view

        ZegoStreamInfo bigStreamInfo = (ZegoStreamInfo) mBigVideoLiveView.getTag();
        if (bigStreamInfo != null) {
            ZegoAppHelper.getLiveRoom().updatePlayView(bigStreamInfo.streamID, null);
            ZegoAppHelper.getLiveRoom().updatePlayView(streamInfo.streamID, null);
            ZegoAppHelper.getLiveRoom().setPreviewView(null);
            mPosition = position;
            adapter.replace(bigStreamInfo, position);
            mBigVideoLiveView.setTag(streamInfo);
            mBigVideoLiveView.setStreamID(streamInfo.streamID);

        }


        changeViewLocation(streamInfo);

    }


    private void changeViewLocation(ZegoStreamInfo streamInfo) {
        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();
        if (TextUtils.isEmpty(streamInfo.userID) || TextUtils.equals(streamInfo.userID, PrefUtil.getInstance().getUserId())) {
            // preview
            liveRoom.setPreviewView(mBigVideoLiveView.getTextureView());
            liveRoom.setPreviewViewMode(ZegoVideoViewMode.ScaleAspectFit);
        } else {
            // play
            liveRoom.updatePlayView(streamInfo.streamID, mBigVideoLiveView.getTextureView());
            liveRoom.setViewMode(ZegoVideoViewMode.ScaleAspectFit, streamInfo.streamID);
        }
    }

    private void setupCallback() {
        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();
        liveRoom.setZegoLivePublisherCallback(new ZegoLivePublisherCallback());
        liveRoom.setZegoLivePlayerCallback(new ZegoLivePlayerCallback());
        liveRoom.setZegoRoomCallback(new ZegoRoomCallback());
    }

    private void loginRoom() {
        String sessionId = getIntent().getStringExtra("sessionId");
        String roomName = String.format("From_%s", PrefUtil.getInstance().getUserName());
        boolean success = ZegoAppHelper.getLiveRoom().loginRoom(sessionId, roomName, ZegoConstants.RoomRole.Audience, new ZegoLgoinCompleteCallback());
        if (success) {
            mIsLoginRoom = true;
            startPreview();
        }

        AppLogger.getInstance().writeLog("LoginRoom : %s, success ? %s", sessionId, success);
    }

    private void startPreview() {
        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();

        liveRoom.enableMic(true);
        liveRoom.enableCamera(true);
        liveRoom.enableSpeaker(true);
        liveRoom.setPreviewView(mBigVideoLiveView.getTextureView());
        liveRoom.setPreviewViewMode(ZegoVideoViewMode.ScaleAspectFit);
        liveRoom.startPreview();

        AppLogger.getInstance().writeLog("Start preview");
    }

    private void startPublishStream() {
        String streamId = String.format("s-%s-%s", PrefUtil.getInstance().getUserId(), TimeUtil.getNowTimeStr());
        String title = String.format("%s is comming", PrefUtil.getInstance().getUserId());

        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();
        // 推流前判断屏幕朝向
        int currentOrientation = getWindowManager().getDefaultDisplay().getRotation();
        liveRoom.setAppOrientation(currentOrientation);
        // 开启自动流量监控
        int properties = ZegoConstants.ZegoTrafficControlProperty.ZEGOAPI_TRAFFIC_FPS
                | ZegoConstants.ZegoTrafficControlProperty.ZEGOAPI_TRAFFIC_RESOLUTION;
        liveRoom.enableTrafficControl(properties, true);
        int layeredCoding = PrefUtil.getInstance().getLayeredCoding();
        if (layeredCoding == ZegoConstants.ZegoVideoCodecAvc.VIDEO_CODEC_DEFAULT) {
            liveRoom.setVideoCodecId(ZegoConstants.ZegoVideoCodecAvc.VIDEO_CODEC_DEFAULT, ZegoConstants.PublishChannelIndex.MAIN);
        } else if (layeredCoding == ZegoConstants.ZegoVideoCodecAvc.VIDEO_CODEC_MULTILAYER) {
            liveRoom.setVideoCodecId(ZegoConstants.ZegoVideoCodecAvc.VIDEO_CODEC_MULTILAYER, ZegoConstants.PublishChannelIndex.MAIN);
        }

        // 开始推流
        mPublishStreamId = streamId;
        boolean success = liveRoom.startPublishing(streamId, title, ZegoConstants.PublishFlag.JoinPublish);

        AppLogger.getInstance().writeLog("Publish stream: %s, success ? %s", streamId, success);

        ZegoStreamInfo publishStream = new ZegoStreamInfo();
        publishStream.streamID = streamId;
        publishStream.userID = PrefUtil.getInstance().getUserId();
        publishStream.userName = PrefUtil.getInstance().getUserName();
        mBigVideoLiveView.setTag(publishStream);
        mBigVideoLiveView.setStreamID(publishStream.streamID);

    }

    private void startPlayStreams(ZegoStreamInfo[] streamList) {
        for (int i = 0; streamList != null && i < streamList.length; i++) {
            ZegoStreamInfo streamInfo = streamList[i];
            doPlayStream(streamInfo);
        }
        exchangeOthersViewToFullScreen();
    }

    private void doPlayStream(ZegoStreamInfo streamInfo) {
        String streamId = streamInfo.streamID;
        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();


        liveRoom.startPlayingStream(streamId, null);
        VideoLiveViewAdapter adapter = (VideoLiveViewAdapter) mVideoLiveViewGrid.getAdapter();
        adapter.addStream(streamInfo);
        liveRoom.activateVideoPlayStream(streamId, true, ZegoConstants.VideoStreamLayer.VideoStreamLayer_Auto);
        AppLogger.getInstance().writeLog("Start play stream: %s", streamId);
    }

    private void stopPlayStreams(ZegoStreamInfo[] streamList) {
        for (int i = 0; streamList != null && i < streamList.length; i++) {
            ZegoStreamInfo streamInfo = streamList[i];
            doStopPlayStream(streamInfo);
        }
    }

    private void doStopPlayStream(ZegoStreamInfo streamInfo) {
        String streamId = streamInfo.streamID;
        if (streamId == null) {
            return;
        }

        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();
        liveRoom.stopPlayingStream(streamId);
        VideoLiveViewAdapter adapter = (VideoLiveViewAdapter) mVideoLiveViewGrid.getAdapter();

        ZegoStreamInfo bigStreamInfo = (ZegoStreamInfo) mBigVideoLiveView.getTag();
        if (bigStreamInfo.streamID.equals(streamInfo.streamID)) {
            ZegoStreamInfo zegoStreamInfo = adapter.getStream(mPosition);
            if (bigStreamInfo != null && zegoStreamInfo != null) {
                adapter.replace(bigStreamInfo, mPosition);
                mBigVideoLiveView.setTag(zegoStreamInfo);
                mBigVideoLiveView.setStreamID(zegoStreamInfo.streamID);
            }
            changeViewLocation(zegoStreamInfo);
        }
        adapter.removeStream(streamId);

        AppLogger.getInstance().writeLog("Stop play stream: %s", streamId);
    }

    private void logoutRoom() {
        AppLogger.getInstance().writeLog("Logout room");

        ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();
        if (!TextUtils.isEmpty(mPublishStreamId)) {
            liveRoom.stopPublishing();
            liveRoom.stopPreview();

            AppLogger.getInstance().writeLog("Stop publish stream and stop preview");
        }

        VideoLiveViewAdapter adapter = (VideoLiveViewAdapter) mVideoLiveViewGrid.getAdapter();
        ArrayList<ZegoStreamInfo> playingStreamList = adapter.getCurrentList();
        for (ZegoStreamInfo streamInfo : playingStreamList) {
            liveRoom.stopPlayingStream(streamInfo.streamID);

            AppLogger.getInstance().writeLog("Stop play stream: %s", streamInfo.streamID);
        }

        if (mIsLoginRoom || mHasLoginRoom) {
            liveRoom.logoutRoom();

            AppLogger.getInstance().writeLog("Do logout room");
        }

        liveRoom.setZegoLivePublisherCallback(null);
        liveRoom.setZegoLivePlayerCallback(null);
        liveRoom.setZegoRoomCallback(null);
    }

    public void exchangeOthersViewToFullScreen() {
        //有多条流的情况下 ,大视图是我自己的情况下, 替换第一条流为大视图mPublishStreamId

        VideoLiveViewAdapter adapter = videoLiveViewAdapter;
        List<ZegoStreamInfo> zegoStreamInfoList = adapter.getCurrentList();
        if (zegoStreamInfoList.size() >= 1 && mPublishStreamId != null && mPublishStreamId.equals(mBigVideoLiveView.getStreamID())) {
            exchangeViewToFullScreen(0);
        }
    }

    @Override
    public void onTrackingStatusChanged(final int status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    private class ButtonClickListener implements View.OnClickListener {
        /**
         * Called when a view has been clicked.
         *
         * @param v The view that was clicked.
         */
        @Override
        public void onClick(View v) {
            ZegoLiveRoom liveRoom = ZegoAppHelper.getLiveRoom();
            switch (v.getId()) {
                case R.id.vt_btn_camera: {
                    v.setSelected(!v.isSelected());
                    boolean disableCamera = v.isSelected();
                    liveRoom.enableCamera(!disableCamera);

                    AppLogger.getInstance().writeLog("%s camera", disableCamera ? "Disable" : "Enable");
                }
                break;

                case R.id.vt_btn_mic: {
                    v.setSelected(!v.isSelected());
                    boolean disableMic = v.isSelected();
                    liveRoom.enableMic(!disableMic);

                    AppLogger.getInstance().writeLog("%s mic", disableMic ? "Disable" : "Enable");
                }
                break;

                case R.id.vt_btn_speaker: {
                    v.setSelected(!v.isSelected());
                    boolean disableSpeaker = v.isSelected();
                    liveRoom.enableSpeaker(!disableSpeaker);

                    AppLogger.getInstance().writeLog("%s speaker", disableSpeaker ? "Disable" : "Enable");
                }
                break;

                case R.id.vt_btn_show_log: {
                    startActivity(new Intent(VideoTalkActivity.this, LogActivity.class));
                }
                break;

                case R.id.vt_btn_close: {
                    onBackPressed();
                }
                break;

                case R.id.vt_btn_front_camera: {
                    mIsFrontCamera = !mIsFrontCamera;
                    liveRoom.setFrontCam(mIsFrontCamera);
                    if (mFURenderer != null) {
                        int cameraType = mIsFrontCamera ? Camera.CameraInfo.CAMERA_FACING_FRONT :
                                Camera.CameraInfo.CAMERA_FACING_BACK;
                        mFURenderer.onCameraChanged(cameraType, FURenderer.getCameraOrientation(cameraType));
                    }
                }
                break;
                default:
            }
        }
    }

    private class ZegoLgoinCompleteCallback implements IZegoLoginCompletionCallback {
        @Override
        public void onLoginCompletion(int errorCode, ZegoStreamInfo[] streamList) {
            mIsLoginRoom = false;

            AppLogger.getInstance().writeLog("onLoginCompletion, code: %d, has stream ? %s", errorCode, (streamList != null && streamList.length > 0));

            if (isFinishing()) return;

            if (errorCode != 0) {
                Toast.makeText(VideoTalkActivity.this, getString(R.string.vt_toast_login_failed, errorCode), Toast.LENGTH_LONG).show();
                return;
            }

            mHasLoginRoom = true;

            startPublishStream();

            startPlayStreams(streamList);
        }
    }

    private class ZegoRoomCallback implements IZegoRoomCallback {
        /**
         * 因为登陆抢占原因等被挤出房间
         */
        @Override
        public void onKickOut(int reason, String roomId) {
            AppLogger.getInstance().writeLog("onKickOut, reason: %d, room Id: %s", reason, roomId);
        }

        /**
         * 与 server 断开
         */
        @Override
        public void onDisconnect(int errorCode, String roomId) {
            AppLogger.getInstance().writeLog("onDisconnect, reason: %d, room Id: %s", errorCode, roomId);
        }

        /**
         * 中断后重连
         */
        @Override
        public void onReconnect(int errorCode, String roomId) {
            AppLogger.getInstance().writeLog("onReconnect, errorCode: %d, room Id: %s", errorCode, roomId);
        }

        /**
         * 临时中断
         */
        @Override
        public void onTempBroken(int errorCode, String roomId) {
            AppLogger.getInstance().writeLog("onTempBroken, errorCode: %d, room Id: %s", errorCode, roomId);
        }

        /**
         * 房间流列表更新
         */
        @Override
        public void onStreamUpdated(int type, ZegoStreamInfo[] streamList, String roomId) {
            AppLogger.getInstance().writeLog("onStreamUpdated, type: %d", type);

            if (type == ZegoConstants.StreamUpdateType.Added) {
                startPlayStreams(streamList);
            } else if (type == ZegoConstants.StreamUpdateType.Deleted) {
                stopPlayStreams(streamList);
            } else {
                Toast.makeText(VideoTalkActivity.this, "Unknown stream update type " + type, Toast.LENGTH_LONG).show();
            }
        }

        /**
         * 更新流的额外信息
         */
        @Override
        public void onStreamExtraInfoUpdated(ZegoStreamInfo[] streamList, String roomId) {

        }

        /**
         * 收到自定义消息
         */
        @Override
        public void onRecvCustomCommand(String fromUserId, String fromUserName, String content, String roomId) {

        }
    }

    private class ZegoLivePlayerCallback implements IZegoLivePlayerCallback {
        /**
         * 拉流状态更新
         */
        @Override
        public void onPlayStateUpdate(int stateCode, String streamId) {
            AppLogger.getInstance().writeLog("onPlayStateUpdate, stateCode: %d, stream Id: %s", stateCode, streamId);
        }

        /**
         * 拉流质量更新
         */
        @SuppressLint("StringFormatMatches")
        @Override
        public void onPlayQualityUpdate(String streamId, ZegoPlayStreamQuality zegoPlayStreamQuality) {

            VideoLiveViewAdapter.CommonStreamQuality commonStreamQuality = EntityConversion.playQualityToCommonStreamQuality(zegoPlayStreamQuality);

            //TODO
            videoLiveViewAdapter.onPlayQualityUpdate(streamId, commonStreamQuality);
            mBigVideoLiveView.setDecoderFormat(zegoPlayStreamQuality.isHardwareVdec);
            if (streamId != null && streamId.equals(mBigVideoLiveView.getStreamID())) {
                mBigVideoLiveView.setLiveQuality(zegoPlayStreamQuality.quality, commonStreamQuality.videoFps, commonStreamQuality.vkbps, commonStreamQuality.rtt, commonStreamQuality.pktLostRate);
            }
            Log.e("setLiveQuality", getString(R.string.vt_live_quality_fps_and_bitrate, commonStreamQuality.videoFps, commonStreamQuality.vkbps, commonStreamQuality.rtt, commonStreamQuality.pktLostRate));

        }

        /**
         * 观众收到主播的连麦邀请
         */
        @Override
        public void onInviteJoinLiveRequest(int seq, String fromUserId, String fromUserName, String roomId) {

        }

        /**
         * 连麦观众收到主播的结束连麦信令
         */
        @Override
        public void onRecvEndJoinLiveCommand(String fromUserId, String fromUserName, String roomId) {

        }

        /**
         * 视频宽高变化通知
         */
        @Override
        public void onVideoSizeChangedTo(String streamId, int width, int height) {
            AppLogger.getInstance().writeLog("onVideoSizeChangedTo, stream Id: %s, width: %d, height: %d", streamId, width, height);
        }
    }

    private class ZegoLivePublisherCallback implements IZegoLivePublisherCallback {
        /**
         * 推流状态更新
         */
        @Override
        public void onPublishStateUpdate(int stateCode, String streamId, HashMap<String, Object> streamInfo) {
            AppLogger.getInstance().writeLog("onPublishStateUpdate, stateCode: %d, stream Id: %s", stateCode, streamId);
        }

        /**
         * 收到观众的连麦请求
         */
        @Override
        public void onJoinLiveRequest(int seq, String fromUserId, String fromUserName, String roomId) {

        }

        /**
         * 推流质量更新
         */
        @SuppressLint("StringFormatMatches")
        @Override
        public void onPublishQualityUpdate(String streamID, ZegoPublishStreamQuality streamQuality) {

            VideoLiveViewAdapter.CommonStreamQuality commonStreamQuality = EntityConversion.publishQualityToCommonStreamQuality(streamQuality);
            if (streamID != null) {
                videoLiveViewAdapter.onPlayQualityUpdate(streamID, commonStreamQuality);
                mBigVideoLiveView.setEncoderFormat(streamQuality.isHardwareVenc);

                if (streamID.equals(mBigVideoLiveView.getStreamID())) {
                    mBigVideoLiveView.setLiveQuality(commonStreamQuality.quality, commonStreamQuality.videoFps, commonStreamQuality.vkbps, commonStreamQuality.rtt, commonStreamQuality.pktLostRate);
                }
            }
            Log.e("PublishSetLiveQuality", getString(R.string.vt_live_quality_fps_and_bitrate, commonStreamQuality.videoFps, commonStreamQuality.vkbps, commonStreamQuality.rtt, commonStreamQuality.pktLostRate));

        }

        /**
         * 音乐伴奏回调, 每次取20毫秒的数据.
         */
        @Override
        public AuxData onAuxCallback(int exceptedDataLen) {
            return null;
        }

        /**
         * 采集视频的宽度和高度变化通知
         */
        @Override
        public void onCaptureVideoSizeChangedTo(int width, int height) {

        }

        /**
         * 混流配置更新
         */
        @Override
        public void onMixStreamConfigUpdate(int stateCode, String
                mixStreamId, HashMap<String, Object> streamInfo) {

        }

        @Override
        public void onCaptureVideoFirstFrame() {

        }
    }
}
