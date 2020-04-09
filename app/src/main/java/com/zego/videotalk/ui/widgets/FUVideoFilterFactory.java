package com.zego.videotalk.ui.widgets;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.faceunity.nama.FURenderer;
import com.zego.videotalk.filter.VideoFilterSurfaceTexture;
import com.zego.videotalk.gl.GlRectDrawer;
import com.zego.videotalk.gl.GlUtil;
import com.zego.zegoavkit2.videofilter.ZegoVideoFilter;
import com.zego.zegoavkit2.videofilter.ZegoVideoFilterFactory;

import java.nio.ByteBuffer;

/**
 * Created by hyj on 2018/10/30.
 */
public class FUVideoFilterFactory extends ZegoVideoFilterFactory {
    private static final String TAG = "FUVideoFilterFactory";
    private FURenderer mFURenderer;
    private VideoFilterSurfaceTexture mVideoFilterSurfaceTexture;

    public FUVideoFilterFactory(FURenderer fuRenderer) {
        mFURenderer = fuRenderer;
    }

    @Override
    protected ZegoVideoFilter create() {
        Log.d(TAG, "create ZegoVideoFilter: ");
        mVideoFilterSurfaceTexture = new VideoFilterSurfaceTexture(mFURenderer);
        return mVideoFilterSurfaceTexture;
    }

    @Override
    protected void destroy(ZegoVideoFilter zegoVideoFilter) {
        Log.d(TAG, "destroy ZegoVideoFilter: ");
        mVideoFilterSurfaceTexture = null;
    }

    // 不再推荐使用 BUFFER_TYPE_SYNC_GL_TEXTURE_2D，推荐使用 BUFFER_TYPE_SURFACE_TEXTURE
    @Deprecated
    private class FUZegoVideoFilter extends ZegoVideoFilter {
        private FURenderer mFURenderer;
        private Client mClient;
        private GlRectDrawer mDrawer;
        private int mTextureId;
        private int mFrameBufferId;
        private float[] mIdentityMatrix = new float[16];
        private int mWidth;
        private int mHeight;

        FUZegoVideoFilter(FURenderer fuRenderer) {
            mFURenderer = fuRenderer;
            Matrix.setIdentityM(mIdentityMatrix, 0);
        }

        @Override
        protected void allocateAndStart(Client client) {
            Log.d(TAG, "allocateAndStart: thread:" + Thread.currentThread().getName() + ", egl:" + EGL14.eglGetCurrentContext());
            mClient = client;
            mWidth = 0;
            mHeight = 0;
            if (mDrawer == null) {
                mDrawer = new GlRectDrawer();
            }
            mFURenderer.onSurfaceCreated();
        }

        @Override
        protected void stopAndDeAllocate() {
            Log.d(TAG, "stopAndDeAllocate: thread:" + Thread.currentThread().getName() + ", egl:" + EGL14.eglGetCurrentContext());
            deleteTexAndFbo();
            if (mDrawer != null) {
                mDrawer.release();
                mDrawer = null;
            }
            mFURenderer.onSurfaceDestroyed();
            mClient.destroy();
            mClient = null;
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
        protected void onProcessCallback(int texId, int width, int height, long timestamp) {
//            Log.v(TAG, "onProcessCallback: tex:" + texId + ", width:" + width + ", height:" + height + ", timestamp:" + timestamp);
            // tex:11, width:540, height:960

            if (mWidth != width || mHeight != height) {
                deleteTexAndFbo();
                mWidth = width;
                mHeight = height;
            }

            if (mFrameBufferId <= 0) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                mTextureId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                mFrameBufferId = GlUtil.generateFrameBuffer(mTextureId);
            } else {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferId);
            }
            int fuTexId = mFURenderer.onDrawFrameSingleInput(texId, width, height);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            mDrawer.drawRgb(fuTexId, mIdentityMatrix, width, height, 0, 0, width, height);

            mClient.onProcessCallback(mTextureId, width, height, timestamp);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        private void deleteTexAndFbo() {
            if (mTextureId > 0) {
                int[] textures = new int[]{mTextureId};
                GLES20.glDeleteTextures(1, textures, 0);
                mTextureId = 0;
            }
            if (mFrameBufferId > 0) {
                int[] frameBuffers = new int[]{mFrameBufferId};
                GLES20.glDeleteFramebuffers(1, frameBuffers, 0);
                mFrameBufferId = 0;
            }
        }
    }

}
