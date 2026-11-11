package app.morphe.extension.claude;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

/** The Larpude screen. For now: a full screen that says Hello. Back closes it. */
final class LarpudeScreen {
    private LarpudeScreen() {}

    static void show(Activity activity) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));

        TextView hello = new TextView(activity);
        hello.setText("Hello");
        hello.setTextColor(Color.WHITE);
        hello.setTextSize(36);
        hello.setGravity(Gravity.CENTER);
        root.addView(hello, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dialog.setContentView(root);
        dialog.show();
    }
}
