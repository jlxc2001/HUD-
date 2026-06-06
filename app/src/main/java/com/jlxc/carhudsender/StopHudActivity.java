package com.jlxc.carhudsender;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class StopHudActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        String ip = sp.getString("ip", "");
        int port = parseInt(sp.getString("port", "45678"), 45678);
        stopService(new Intent(this, CaptureService.class));
        sendStopCommand(ip, port);
        Toast.makeText(this, "HUD服务已关闭", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void sendStopCommand(final String ip, final int port) {
        if (ip == null || ip.length() == 0) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://" + ip + ":" + port + "/control");
                byte[] body = "STOP".getBytes("UTF-8");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(800);
                conn.setReadTimeout(800);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                conn.setRequestProperty("Content-Length", String.valueOf(body.length));
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.flush();
                os.close();
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private int parseInt(String value, int def) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return def; }
    }
}
