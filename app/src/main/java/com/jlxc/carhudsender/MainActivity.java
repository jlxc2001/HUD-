package com.jlxc.carhudsender;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_PICK_SCREENSHOT = 1002;

    private EditText ipEdit, portEdit, xEdit, yEdit, wEdit, hEdit, intervalEdit, qualityEdit;
    private SharedPreferences sp;
    private CropSelectView cropSelectView;
    private TextView cropInfoText;
    private String adjustMode = "all";
    private int nudgeStep = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(26));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("HUD发射端");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("车机端导航卡片截图发送器");
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = new TextView(this);
        tip.setText("步骤：填写接收端 IP → 设置裁剪区域 → 开始发送 → 授权录屏 → 切回导航界面。\n建议：1秒1帧，JPG质量70。裁剪区域可手动填写，也可以通过截图选择。\n如果接收端收不到画面，请确认两台设备在同一个 WiFi / 热点局域网内。建议给接收端设置静态 IP。");
        tip.setTextSize(14);
        tip.setPadding(0, dp(10), 0, dp(10));
        root.addView(tip, new LinearLayout.LayoutParams(-1, -2));

        ipEdit = addField(root, "接收端 IP", sp.getString("ip", "192.168.1.36"), InputType.TYPE_CLASS_TEXT);
        portEdit = addField(root, "端口", sp.getString("port", "45678"), InputType.TYPE_CLASS_NUMBER);
        xEdit = addField(root, "裁切 X", sp.getString("x", "150"), InputType.TYPE_CLASS_NUMBER);
        yEdit = addField(root, "裁切 Y", sp.getString("y", "45"), InputType.TYPE_CLASS_NUMBER);
        wEdit = addField(root, "裁切宽度", sp.getString("w", "760"), InputType.TYPE_CLASS_NUMBER);
        hEdit = addField(root, "裁切高度", sp.getString("h", "270"), InputType.TYPE_CLASS_NUMBER);
        intervalEdit = addField(root, "发送间隔毫秒", sp.getString("interval", "1000"), InputType.TYPE_CLASS_NUMBER);
        qualityEdit = addField(root, "JPG质量 1-100", sp.getString("quality", "70"), InputType.TYPE_CLASS_NUMBER);

        Button pick = new Button(this);
        pick.setText("通过截图选择裁剪区域");
        pick.setOnClickListener(v -> pickScreenshot());
        root.addView(pick, new LinearLayout.LayoutParams(-1, dp(56)));

        Button start = new Button(this);
        start.setText("开始发送 / 请求屏幕捕获权限");
        start.setOnClickListener(v -> startCaptureFlow());
        root.addView(start, new LinearLayout.LayoutParams(-1, dp(56)));

        Button stop = new Button(this);
        stop.setText("停止发送");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            Toast.makeText(this, "已发送停止命令", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop, new LinearLayout.LayoutParams(-1, dp(56)));

        Button about = new Button(this);
        about.setText("关于软件");
        about.setOnClickListener(v -> showAboutDialog());
        root.addView(about, new LinearLayout.LayoutParams(-1, dp(56)));

        setContentView(scrollView);
    }

    private EditText addField(LinearLayout root, String label, String value, int inputType) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setPadding(0, dp(8), 0, 0);
        root.addView(tv, new LinearLayout.LayoutParams(-1, -2));

        EditText et = new EditText(this);
        et.setText(value);
        et.setSingleLine(true);
        et.setInputType(inputType);
        root.addView(et, new LinearLayout.LayoutParams(-1, dp(46)));
        return et;
    }

    private void pickScreenshot() {
        saveSettings();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_SCREENSHOT);
    }

    private void showCropSelector(Bitmap bitmap) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(10));
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("通过截图选择裁剪区域");
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        cropInfoText = new TextView(this);
        cropInfoText.setTextColor(Color.rgb(0, 245, 212));
        cropInfoText.setTextSize(13);
        cropInfoText.setGravity(Gravity.CENTER);
        cropInfoText.setPadding(0, dp(4), 0, dp(4));
        root.addView(cropInfoText, new LinearLayout.LayoutParams(-1, -2));

        cropSelectView = new CropSelectView(this, bitmap);
        int oldX = parseInt(xEdit, 150);
        int oldY = parseInt(yEdit, 45);
        int oldW = parseInt(wEdit, 760);
        int oldH = parseInt(hEdit, 270);
        cropSelectView.setCropFromValues(oldX, oldY, oldW, oldH);
        cropSelectView.setOnCropChangeListener(this::updateCropInfoText);
        root.addView(cropSelectView, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setGravity(Gravity.CENTER);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.addView(makeSmallButton("整体", () -> setAdjustMode("all")));
        modeRow.addView(makeSmallButton("左边", () -> setAdjustMode("left")));
        modeRow.addView(makeSmallButton("右边", () -> setAdjustMode("right")));
        modeRow.addView(makeSmallButton("上边", () -> setAdjustMode("top")));
        modeRow.addView(makeSmallButton("下边", () -> setAdjustMode("bottom")));
        root.addView(modeRow, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout nudgeRow = new LinearLayout(this);
        nudgeRow.setGravity(Gravity.CENTER);
        nudgeRow.setOrientation(LinearLayout.HORIZONTAL);
        nudgeRow.addView(makeSmallButton("←", () -> cropSelectView.nudge(adjustMode, -nudgeStep, 0)));
        nudgeRow.addView(makeSmallButton("↑", () -> cropSelectView.nudge(adjustMode, 0, -nudgeStep)));
        nudgeRow.addView(makeSmallButton("↓", () -> cropSelectView.nudge(adjustMode, 0, nudgeStep)));
        nudgeRow.addView(makeSmallButton("→", () -> cropSelectView.nudge(adjustMode, nudgeStep, 0)));
        nudgeRow.addView(makeSmallButton("步长1", () -> { nudgeStep = 1; Toast.makeText(this, "微调步长：1px", Toast.LENGTH_SHORT).show(); }));
        nudgeRow.addView(makeSmallButton("步长10", () -> { nudgeStep = 10; Toast.makeText(this, "微调步长：10px", Toast.LENGTH_SHORT).show(); }));
        root.addView(nudgeRow, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setOnClickListener(v -> buildUi());
        actionRow.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button apply = new Button(this);
        apply.setText("确认使用此区域");
        apply.setOnClickListener(v -> applyCropFromSelector());
        actionRow.addView(apply, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(actionRow, new LinearLayout.LayoutParams(-1, dp(58)));

        setContentView(root);
        updateCropInfoText();
    }

    private Button makeSmallButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(v -> {
            action.run();
            updateCropInfoText();
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        return b;
    }

    private void setAdjustMode(String mode) {
        adjustMode = mode;
        String name = "整体";
        if ("left".equals(mode)) name = "左边";
        if ("right".equals(mode)) name = "右边";
        if ("top".equals(mode)) name = "上边";
        if ("bottom".equals(mode)) name = "下边";
        Toast.makeText(this, "当前调整：" + name, Toast.LENGTH_SHORT).show();
    }

    private void updateCropInfoText() {
        if (cropSelectView == null || cropInfoText == null) return;
        int[] values = cropSelectView.getCropValues();
        cropInfoText.setText("X=" + values[0] + "  Y=" + values[1] + "  宽=" + values[2] + "  高=" + values[3] + "  当前调整=" + modeName() + "  步长=" + nudgeStep + "px");
    }

    private String modeName() {
        if ("left".equals(adjustMode)) return "左边";
        if ("right".equals(adjustMode)) return "右边";
        if ("top".equals(adjustMode)) return "上边";
        if ("bottom".equals(adjustMode)) return "下边";
        return "整体";
    }

    private void applyCropFromSelector() {
        int[] values = cropSelectView.getCropValues();
        xEdit.setText(String.valueOf(values[0]));
        yEdit.setText(String.valueOf(values[1]));
        wEdit.setText(String.valueOf(values[2]));
        hEdit.setText(String.valueOf(values[3]));
        saveSettings();
        Toast.makeText(this, "已写入裁切参数", Toast.LENGTH_SHORT).show();
        buildUi();
    }

    private void showAboutDialog() {
        String message = "HUD发射端\n\n" +
                "用途：在车机端截取导航软件左上角导航信息卡片，并通过 WiFi 局域网发送到 HUD接收端显示。\n\n" +
                "作者：江灵夏草\n" +
                "B站主页：https://space.bilibili.com/130914376\n" +
                "抖音：JLXC2001\n" +
                "X（原推特）：jlxc2001\n\n" +
                "使用建议：\n" +
                "1. 建议车机端和接收端连接同一个 WiFi 或手机热点。\n" +
                "2. 建议给接收端设置静态 IP，避免每次上车 IP 变化。\n" +
                "3. 如果导航卡片显示不完整，可以手动修改 X/Y/宽度/高度，或使用“通过截图选择裁剪区域”。\n" +
                "4. 第一版推荐 1 秒 1 帧、JPG质量70，稳定后再提高刷新率。\n" +
                "5. 本软件只发送裁剪后的导航画面，不上传任何数据到互联网。";
        new AlertDialog.Builder(this)
                .setTitle("关于 HUD发射端")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void startCaptureFlow() {
        saveSettings();
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    private void saveSettings() {
        sp.edit()
                .putString("ip", ipEdit.getText().toString().trim())
                .putString("port", portEdit.getText().toString().trim())
                .putString("x", xEdit.getText().toString().trim())
                .putString("y", yEdit.getText().toString().trim())
                .putString("w", wEdit.getText().toString().trim())
                .putString("h", hEdit.getText().toString().trim())
                .putString("interval", intervalEdit.getText().toString().trim())
                .putString("quality", qualityEdit.getText().toString().trim())
                .apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent svc = new Intent(this, CaptureService.class);
                svc.putExtra("resultCode", resultCode);
                svc.putExtra("data", data);
                svc.putExtra("ip", ipEdit.getText().toString().trim());
                svc.putExtra("port", parseInt(portEdit, 45678));
                svc.putExtra("cropX", parseInt(xEdit, 150));
                svc.putExtra("cropY", parseInt(yEdit, 45));
                svc.putExtra("cropW", parseInt(wEdit, 760));
                svc.putExtra("cropH", parseInt(hEdit, 270));
                svc.putExtra("interval", parseInt(intervalEdit, 1000));
                svc.putExtra("quality", parseInt(qualityEdit, 70));
                startService(svc);
                Toast.makeText(this, "已开始发送。现在可以切回导航软件。", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "没有授权屏幕捕获", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_PICK_SCREENSHOT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (is != null) is.close();
                    if (bitmap == null) {
                        Toast.makeText(this, "无法读取截图", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showCropSelector(bitmap);
                } catch (Exception e) {
                    Toast.makeText(this, "读取截图失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private int parseInt(EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString().trim()); } catch (Exception e) { return def; }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    public static class CropSelectView extends View {
        private final Bitmap bitmap;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadePaint = new Paint();
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final RectF crop = new RectF();
        private OnCropChangeListener listener;
        private String dragMode = "none";
        private float lastX, lastY;
        private final float minSize = 20f;

        public CropSelectView(Context context, Bitmap bitmap) {
            super(context);
            this.bitmap = bitmap;
            setBackgroundColor(Color.BLACK);
            shadePaint.setColor(Color.argb(130, 0, 0, 0));
            borderPaint.setColor(Color.rgb(0, 245, 212));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4f);
            handlePaint.setColor(Color.WHITE);
            handlePaint.setStyle(Paint.Style.FILL);
        }

        public void setOnCropChangeListener(OnCropChangeListener listener) {
            this.listener = listener;
        }

        public void setCropFromValues(int x, int y, int w, int h) {
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            float left = clamp(x, 0, bw - 1);
            float top = clamp(y, 0, bh - 1);
            float right = clamp(x + w, left + minSize, bw);
            float bottom = clamp(y + h, top + minSize, bh);
            crop.set(left, top, right, bottom);
            invalidate();
            notifyChange();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            computeImageRect();
            canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);

            RectF viewCrop = bitmapToView(crop);
            canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, viewCrop.top, shadePaint);
            canvas.drawRect(imageRect.left, viewCrop.bottom, imageRect.right, imageRect.bottom, shadePaint);
            canvas.drawRect(imageRect.left, viewCrop.top, viewCrop.left, viewCrop.bottom, shadePaint);
            canvas.drawRect(viewCrop.right, viewCrop.top, imageRect.right, viewCrop.bottom, shadePaint);
            canvas.drawRect(viewCrop, borderPaint);

            drawHandle(canvas, viewCrop.left, viewCrop.top);
            drawHandle(canvas, viewCrop.right, viewCrop.top);
            drawHandle(canvas, viewCrop.left, viewCrop.bottom);
            drawHandle(canvas, viewCrop.right, viewCrop.bottom);
            drawHandle(canvas, viewCrop.centerX(), viewCrop.top);
            drawHandle(canvas, viewCrop.centerX(), viewCrop.bottom);
            drawHandle(canvas, viewCrop.left, viewCrop.centerY());
            drawHandle(canvas, viewCrop.right, viewCrop.centerY());
        }

        private void drawHandle(Canvas canvas, float x, float y) {
            canvas.drawCircle(x, y, 8f, handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            computeImageRect();
            float bx = viewToBitmapX(event.getX());
            float by = viewToBitmapY(event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                dragMode = hitMode(bx, by);
                lastX = bx;
                lastY = by;
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = bx - lastX;
                float dy = by - lastY;
                applyDrag(dx, dy);
                lastX = bx;
                lastY = by;
                invalidate();
                notifyChange();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                dragMode = "none";
                return true;
            }
            return true;
        }

        private String hitMode(float x, float y) {
            float threshold = Math.max(20f, Math.min(bitmap.getWidth(), bitmap.getHeight()) * 0.035f);
            boolean nearLeft = Math.abs(x - crop.left) < threshold;
            boolean nearRight = Math.abs(x - crop.right) < threshold;
            boolean nearTop = Math.abs(y - crop.top) < threshold;
            boolean nearBottom = Math.abs(y - crop.bottom) < threshold;
            if (nearLeft) return "left";
            if (nearRight) return "right";
            if (nearTop) return "top";
            if (nearBottom) return "bottom";
            if (crop.contains(x, y)) return "all";
            return "all";
        }

        private void applyDrag(float dx, float dy) {
            nudge(dragMode, Math.round(dx), Math.round(dy));
        }

        public void nudge(String mode, int dx, int dy) {
            if ("left".equals(mode)) {
                crop.left = clamp(crop.left + dx, 0, crop.right - minSize);
            } else if ("right".equals(mode)) {
                crop.right = clamp(crop.right + dx, crop.left + minSize, bitmap.getWidth());
            } else if ("top".equals(mode)) {
                crop.top = clamp(crop.top + dy, 0, crop.bottom - minSize);
            } else if ("bottom".equals(mode)) {
                crop.bottom = clamp(crop.bottom + dy, crop.top + minSize, bitmap.getHeight());
            } else {
                float width = crop.width();
                float height = crop.height();
                float left = clamp(crop.left + dx, 0, bitmap.getWidth() - width);
                float top = clamp(crop.top + dy, 0, bitmap.getHeight() - height);
                crop.set(left, top, left + width, top + height);
            }
            invalidate();
            notifyChange();
        }

        public int[] getCropValues() {
            int x = Math.round(crop.left);
            int y = Math.round(crop.top);
            int w = Math.round(crop.width());
            int h = Math.round(crop.height());
            return new int[]{x, y, w, h};
        }

        private void notifyChange() {
            if (listener != null) listener.onCropChanged();
        }

        private void computeImageRect() {
            float vw = getWidth();
            float vh = getHeight();
            float bw = bitmap.getWidth();
            float bh = bitmap.getHeight();
            float scale = Math.min(vw / bw, vh / bh);
            float drawW = bw * scale;
            float drawH = bh * scale;
            float left = (vw - drawW) / 2f;
            float top = (vh - drawH) / 2f;
            imageRect.set(left, top, left + drawW, top + drawH);
        }

        private RectF bitmapToView(RectF src) {
            float sx = imageRect.width() / bitmap.getWidth();
            float sy = imageRect.height() / bitmap.getHeight();
            return new RectF(
                    imageRect.left + src.left * sx,
                    imageRect.top + src.top * sy,
                    imageRect.left + src.right * sx,
                    imageRect.top + src.bottom * sy
            );
        }

        private float viewToBitmapX(float x) {
            return clamp((x - imageRect.left) * bitmap.getWidth() / imageRect.width(), 0, bitmap.getWidth());
        }

        private float viewToBitmapY(float y) {
            return clamp((y - imageRect.top) * bitmap.getHeight() / imageRect.height(), 0, bitmap.getHeight());
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public interface OnCropChangeListener {
        void onCropChanged();
    }
}
