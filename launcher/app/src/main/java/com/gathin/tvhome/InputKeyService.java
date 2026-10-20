package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Maps the remote's Input button to an inputs menu, from any app. Remotes send KEYCODE_TV (Chromecast remote) or
 * KEYCODE_TV_INPUT (Xiaomi). The stock launcher normally handles the key, so with it disabled the button does
 * nothing. Opens the side panel (inputs_sidebar, default) or the home screen's dropdown. Other keys pass through.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_TV && code != KeyEvent.KEYCODE_TV_INPUT) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            Intent i = Config.load(this).inputsSidebar
                    ? new Intent(this, InputsPanelActivity.class)
                    : new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_SHOW_INPUTS);
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
