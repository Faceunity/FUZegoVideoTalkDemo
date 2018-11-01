package com.zego.videotalk.ui.widgets;

import android.graphics.SurfaceTexture;
import android.util.Log;

import com.faceunity.FURenderer;
import com.zego.zegoavkit2.videofilter.ZegoVideoFilter;
import com.zego.zegoavkit2.videofilter.ZegoVideoFilterFactory;

import java.nio.ByteBuffer;

/**
 * Created by hyj on 2018/10/30.
 */

public class FUVideoFilterFactory extends ZegoVideoFilterFactory {

    private static final String TAG = "FUVideoFilterFactory";
    private FUZegoVideoFilter mFUZegoVideoFilter;
    private FURenderer mFURenderer;

    public FUVideoFilterFactory(FURenderer mFURenderer) {
        this.mFURenderer = mFURenderer;
    }

    @Override
    protected ZegoVideoFilter create() {
        Log.d(TAG, "create ZegoVideoFilter: ");
        mFUZegoVideoFilter = new FUZegoVideoFilter(mFURenderer);
        return mFUZegoVideoFilter;
    }

    @Override
    protected void destroy(ZegoVideoFilter zegoVideoFilter) {
        Log.d(TAG, "destroy ZegoVideoFilter: ");
        mFUZegoVideoFilter = null;
    }

    private class FUZegoVideoFilter extends ZegoVideoFilter {
        private FURenderer mFURenderer;
        private Client mClient;

        FUZegoVideoFilter(FURenderer fuRenderer) {
            mFURenderer = fuRenderer;
        }

        @Override
        protected void allocateAndStart(Client client) {
            Log.d(TAG, "allocateAndStart: ");
            mClient = client;
            mFURenderer.onSurfaceCreated();
        }

        @Override
        protected void stopAndDeAllocate() {
            Log.d(TAG, "stopAndDeAllocate: ");
            mClient.destroy();
            mClient = null;
            mFURenderer.onSurfaceDestroyed();
        }

        @Override
        protected int supportBufferType() {
            return BUFFER_TYPE_SYNC_GL_TEXTURE_2D;
        }

        @Override
        protected int dequeueInputBuffer(int i, int i1, int i2) {
            return 0;
        }

        @Override
        protected ByteBuffer getInputBuffer(int i) {
            return null;
        }

        @Override
        protected void queueInputBuffer(int i, int i1, int i2, int i3, long l) {

        }

        @Override
        protected SurfaceTexture getSurfaceTexture() {
            return null;
        }

        @Override
        protected void onProcessCallback(int i, int i1, int i2, long l) {
            Log.d(TAG, "onProcessCallback: tex:" + i + ", width:" + i1 + ", height:" + i2);
            int textureId = mFURenderer.onDrawFrame(i, i1, i2);
            mClient.onProcessCallback(textureId, i1, i2, l);
        }
    }
}
