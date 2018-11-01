package com.zego.videotalk.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.util.Log;

import com.zego.videotalk.R;
import com.zego.videotalk.adapter.LogListAdapter;
import com.zego.videotalk.ui.activities.base.AbsBaseActivity;
import com.zego.videotalk.utils.ShareUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by zego on 2018/4/2.
 */

public class ZegoLogShareActivity extends AbsBaseActivity {

    @Bind(R.id.recyclerView)
    android.support.v7.widget.RecyclerView recyclerView;
    private LinkedList<String> mDatas;

    public static void actionStart(Activity activity) {
        Intent intent = new Intent(activity, ZegoLogShareActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected int getContentViewLayout() {
        return R.layout.activity_log_share_zego;
    }

    @Override
    protected void initExtraData(Bundle savedInstanceState) {
        String rootPath = com.zego.zegoavkit2.utils.ZegoLogUtil.getLogPath(ZegoLogShareActivity.this);
        File rootDir = new File(rootPath);
        String[] fileName = rootDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !TextUtils.isEmpty(name) && name.startsWith("zegoavlog") && name.endsWith(".txt");
            }
        });

        List<String> arrayList = Arrays.asList(fileName);
        mDatas = new LinkedList(arrayList);
    }

    @Override
    protected void initVariables(Bundle savedInstanceState) {

    }

    @Override
    protected void initViews(Bundle savedInstanceState) {
        LogListAdapter logListAdapter = new LogListAdapter(ZegoLogShareActivity.this);
        logListAdapter.setData(mDatas);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(logListAdapter);
        // 设置Item添加和移除的动画
        recyclerView.setItemAnimator(new DefaultItemAnimator());

    }

    @Override
    protected void loadData(Bundle savedInstanceState) {

    }

    @OnClick(R.id.tv_back)
    public void back() {
        finish();
    }

    @OnClick(R.id.share)
    public void share() {
        String rootPath = com.zego.zegoavkit2.utils.ZegoLogUtil.getLogPath(ZegoLogShareActivity.this);
        File rootDir = new File(rootPath);
        File[] logFiles = rootDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !TextUtils.isEmpty(name) && name.startsWith("zegoavlog") && name.endsWith(".txt");
            }
        });
        if (logFiles.length > 0) {
            ShareUtils.sendFiles(logFiles, ZegoLogShareActivity.this);
        } else {
            Log.w("ZegoLogShareActivity ", "not found any log files.");
        }

    }
}
