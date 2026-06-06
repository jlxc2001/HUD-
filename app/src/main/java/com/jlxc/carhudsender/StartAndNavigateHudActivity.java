package com.jlxc.carhudsender;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class StartAndNavigateHudActivity extends Activity {
    private static final int REQ_CAPTURE = 9101;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("settings", MODE_PRIVATE);
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent svc = new Intent(this, CaptureService.class);
                svc.putExtra("resultCode", resultCode);
                svc.putExtra("data", data);
                svc.putExtra("ip", sp.getString("ip", "192.168.1.36"));
                svc.putExtra("port", parseInt(sp.getString("port", "45678"), 45678));
                svc.putExtra("cropX", parseInt(sp.getString("x", "150"), 150));
                svc.putExtra("cropY", parseInt(sp.getString("y", "45"), 45));
                svc.putExtra("cropW", parseInt(sp.getString("w", "760"), 760));
                svc.putExtra("cropH", parseInt(sp.getString("h", "270"), 270));
                svc.putExtra("interval", parseInt(sp.getString("interval", "1000"), 1000));
                svc.putExtra("quality", parseInt(sp.getString("quality", "70"), 70));
                svc.putExtra("autoAmap", sp.getBoolean("autoAmap", false));
                svc.putExtra("targetPackage", sp.getString("targetPackage", "com.autonavi.amapauto"));
                startService(svc);
                Toast.makeText(this, "HUD服务已启动，正在打开导航软件", Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).postDelayed(this::launchSelectedNavigationApp, 350);
            } else {
                Toast.makeText(this, "未授权屏幕捕获，HUD服务未启动", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void launchSelectedNavigationApp() {
        String pkg = sp.getString("targetPackage", "com.autonavi.amapauto");
        if (pkg == null || pkg.trim().length() == 0) {
            finish();
            return;
        }
        pkg = pkg.trim();
        try {
            PackageManager pm = getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch);
            } else {
                Toast.makeText(this, "无法打开导航软件：" + pkg, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开导航软件失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private int parseInt(String value, int def) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return def; }
    }
}
