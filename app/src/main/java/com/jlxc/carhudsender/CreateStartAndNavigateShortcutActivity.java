package com.jlxc.carhudsender;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class CreateStartAndNavigateShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent shortcutIntent = new Intent(this, StartAndNavigateHudActivity.class);
        shortcutIntent.setAction("com.jlxc.carhudsender.START_HUD_SERVICE_AND_NAVIGATE");
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent result = new Intent();
        result.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        result.putExtra(Intent.EXTRA_SHORTCUT_NAME, "打开HUD服务并开启导航");
        result.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, android.R.drawable.ic_dialog_map));
        setResult(RESULT_OK, result);
        finish();
    }
}
