package com.jlxc.carhudsender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptureService extends Service {
    private static final String CHANNEL_ID = "hud_sender";
    private HandlerThread captureThread;
    private Handler handler;
    private ExecutorService networkExecutor;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private AtomicBoolean sending = new AtomicBoolean(false);

    private String ip;
    private int port, cropX, cropY, cropW, cropH, interval, quality;
    private int screenW, screenH, densityDpi;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(1, buildNotification("HUD发射端正在运行"));
        captureThread = new HandlerThread("hud-capture");
        captureThread.start();
        handler = new Handler(captureThread.getLooper());
        networkExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        readConfig(intent);
        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");
        if (resultCode == 0 || data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startProjection(resultCode, data);
        return START_STICKY;
    }

    private void readConfig(Intent intent) {
        ip = intent.getStringExtra("ip");
        port = intent.getIntExtra("port", 45678);
        cropX = intent.getIntExtra("cropX", 150);
        cropY = intent.getIntExtra("cropY", 45);
        cropW = intent.getIntExtra("cropW", 760);
        cropH = intent.getIntExtra("cropH", 270);
        interval = Math.max(300, intent.getIntExtra("interval", 1000));
        quality = Math.max(30, Math.min(95, intent.getIntExtra("quality", 70)));

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        densityDpi = dm.densityDpi;
    }

    private void startProjection(int resultCode, Intent data) {
        stopProjectionOnly();
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) {
            stopSelf();
            return;
        }
        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay(
                "HUDSender",
                screenW,
                screenH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler
        );
        handler.postDelayed(captureRunnable, 500);
    }

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                captureOnce();
            } catch (Throwable ignored) {
            }
            if (handler != null) handler.postDelayed(this, interval);
        }
    };

    private void captureOnce() {
        if (imageReader == null || ip == null || ip.length() == 0) return;
        Image image = imageReader.acquireLatestImage();
        if (image == null) return;
        Bitmap full = null;
        Bitmap cropped = null;
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenW;
            int bitmapW = screenW + rowPadding / pixelStride;
            full = Bitmap.createBitmap(bitmapW, screenH, Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(buffer);

            int x = clamp(cropX, 0, screenW - 1);
            int y = clamp(cropY, 0, screenH - 1);
            int w = clamp(cropW, 1, screenW - x);
            int h = clamp(cropH, 1, screenH - y);
            cropped = Bitmap.createBitmap(full, x, y, w, h);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            byte[] jpg = baos.toByteArray();
            postJpeg(jpg);
        } finally {
            image.close();
            if (cropped != null) cropped.recycle();
            if (full != null) full.recycle();
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void postJpeg(final byte[] jpg) {
        if (!sending.compareAndSet(false, true)) return;
        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://" + ip + ":" + port + "/frame");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(800);
                conn.setReadTimeout(800);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "image/jpeg");
                conn.setRequestProperty("Content-Length", String.valueOf(jpg.length));
                OutputStream os = conn.getOutputStream();
                os.write(jpg);
                os.flush();
                os.close();
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
                sending.set(false);
            }
        });
    }

    private Notification buildNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle("HUD发射端")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setOngoing(true);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "HUD发射端", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private void stopProjectionOnly() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (projection != null) { projection.stop(); projection = null; }
    }

    @Override
    public void onDestroy() {
        stopProjectionOnly();
        if (networkExecutor != null) networkExecutor.shutdownNow();
        if (captureThread != null) captureThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
