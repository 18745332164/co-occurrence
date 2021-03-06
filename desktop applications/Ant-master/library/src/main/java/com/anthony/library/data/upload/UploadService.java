package com.anthony.library.data.upload;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import com.anthony.library.R;
import com.anthony.library.data.RxBus;
import com.anthony.library.data.event.UploadEvent;
import com.anthony.library.data.event.UploadEventInternal;
import com.anthony.library.data.event.UploadFinishEvent;
import com.anthony.library.utils.ToastUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by Anthony on 2016/7/8.
 * Class Note:
 */
public class UploadService extends Service {
    public static String UPLOAD_URL = "UPLOAD_URL";
    public static String UPLOAD_FILES = "UPLOAD_FILES";
    public static String UPLOAD_PARAMS = "UPLOAD_PARAMS";
    private static String ACTION = "UPLOAD_ACTION";
    private static String UPLOAD_CANCEL_ID = "UPLOAD_CANCEL_ID";
    private static int ACTION_CANCEL = 1;
    private NotificationManager mNotificationManager;
    private HashMap<Integer, UploadTask> mTaskMap = new HashMap<>();


    ToastUtils toastUtils;

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        RxBus.getDefault().toObserverable(UploadEventInternal.class)
                .onBackpressureBuffer()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<UploadEventInternal>() {
                    @Override
                    public void call(UploadEventInternal uploadEvent) {
                        uploadEvent.task.progress += uploadEvent.byteCount;
                        float percent = (float) uploadEvent.task.progress / (float) uploadEvent.task.total * 100;
                        if (uploadEvent.task.current_percent != (int) percent) {
                            uploadEvent.task.current_percent = (int) percent;
                            uploadEvent.task.mNotification.contentView
                                    .setProgressBar(R.id.progressbar_upload, 100, (int) percent, false);

                            if (mNotificationManager != null) {
                                mNotificationManager.notify(uploadEvent.task.id, uploadEvent.task.mNotification);
                            }
                            Log.v("FileUpload", "Process: " + String.valueOf((int) percent));
                            RxBus.getDefault().post(new UploadEvent(uploadEvent.task.total,
                                    uploadEvent.task.progress, uploadEvent.task));

                            if ((int) percent == 100) {
                                RxBus.getDefault().post(new UploadFinishEvent(uploadEvent.task, true));
                            }
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });

        RxBus.getDefault().toObserverable(UploadFinishEvent.class)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<UploadFinishEvent>() {
                    @Override
                    public void call(UploadFinishEvent uploadFinishEvent) {
                        if (mNotificationManager != null) {
                            mNotificationManager.cancel(uploadFinishEvent.task.id);
                        }

                        if (!uploadFinishEvent.isSuccess) {
                            toastUtils.showToast("????????????");
                        }

                        mTaskMap.remove(uploadFinishEvent.task.id);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int action = intent.getIntExtra(ACTION, 0);

        if (action == ACTION_CANCEL) {
            int id = intent.getIntExtra(UPLOAD_CANCEL_ID, -1);
            UploadTask task = mTaskMap.get(id);
            if (task != null) {
                if (mNotificationManager != null) {
                    mNotificationManager.cancel(task.id);
                }
                task.cancel();
            }
        } else {
            String url = intent.getStringExtra(UPLOAD_URL);
            List<UploadParam> fileList = intent.getParcelableArrayListExtra(UPLOAD_FILES);
            List<UploadParam> paramList = intent.getParcelableArrayListExtra(UPLOAD_PARAMS);

            if (!TextUtils.isEmpty(url) && fileList != null && fileList.size() > 0) {
                long total = 0;

                HashMap<String, File> fileMap = new HashMap<>();
                for (UploadParam param : fileList) {
                    File file = new File(param.value);
                    total += file.length();
                    fileMap.put(param.key, file);
                }

                HashMap<String, String> paramMap = new HashMap<>();
                if (paramList != null) {
                    for (UploadParam param : paramList) {
                        paramMap.put(param.key, param.value);
                    }
                }

                UploadTask task = new UploadTask(mTaskMap.size(), url, fileMap, paramMap);
                task.total = total;
                mTaskMap.put(mTaskMap.size(), task);
                task.start();
                createNotification(task);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mNotificationManager = null;
        cancelAll();
        super.onDestroy();
    }

    private void cancelAll() {
        Iterator iterator = mTaskMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            UploadTask task = (UploadTask) entry.getValue();
            task.cancel();
        }
    }

    private void createNotification(UploadTask task) {
        RemoteViews remoteViews = new RemoteViews(this.getPackageName(),
                R.layout.lib_layout_upload_notification);
        remoteViews.setProgressBar(R.id.progressbar_upload, 100, 0, false);
        remoteViews.setTextViewText(R.id.tv_title, "????????????" + task.mFileMap.size() + "?????????");

        Intent cancelIntent = new Intent(this, UploadService.class);
        cancelIntent.putExtra(ACTION, ACTION_CANCEL);
        cancelIntent.putExtra(UPLOAD_CANCEL_ID, task.id);
        PendingIntent intent = PendingIntent.getService(this, task.id, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.layout_btn_cancel, intent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContent(remoteViews)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(false) //???AutoCancel??????true??????????????????????????????notification?????????????????????????????????
                .setPriority(NotificationCompat.PRIORITY_MAX) //???Android4.1?????????????????????notification??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                .setOngoing(true) //???Ongoing??????true ??????notification?????????????????????
                .setTicker("?????????????????????...");

        task.mNotification = builder.build();
        mNotificationManager.notify(task.id, task.mNotification);
    }
}
