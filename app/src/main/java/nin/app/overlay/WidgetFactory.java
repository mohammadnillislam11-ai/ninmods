package nin.app.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public final class WidgetFactory {

    public interface OnControlChangeListener {
        void onControlChanged(int id, int value);
    }

    private WidgetFactory() {}

    public static View createDetailSwitch(Context context, String label, final int id, final OnControlChangeListener listener) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        int pad = FloaterStyles.dp(context, 8);
        container.setPadding(0, pad, 0, pad);

        TextView title = new TextView(context);
        title.setTextColor(nin.app.core.AppColors.TEXT_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setText(label);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));
        container.addView(title);

        final Switch switchView = new Switch(context);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                FloaterStyles.dp(context, 52),
                FloaterStyles.dp(context, 30)
        );
        switchView.setLayoutParams(switchParams);
        switchView.setChecked(false);
        switchView.setShowText(false);
        switchView.setSwitchMinWidth(FloaterStyles.dp(context, 48));
        switchView.setSplitTrack(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{-android.R.attr.state_checked}
            };
            int[] trackColors = new int[]{nin.app.core.AppColors.SWITCH_TRACK_ON, nin.app.core.AppColors.SWITCH_TRACK_OFF};
            int[] thumbColors = new int[]{nin.app.core.AppColors.SWITCH_THUMB_ON, nin.app.core.AppColors.SWITCH_THUMB_OFF};
            switchView.setTrackTintList(new ColorStateList(states, trackColors));
            switchView.setThumbTintList(new ColorStateList(states, thumbColors));
        }

        switchView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (listener != null) {
                    listener.onControlChanged(id, isChecked ? 1 : 0);
                }
            }
        });

        container.addView(switchView);
        return container;
    }

    public static View createDetailSeekBar(Context context, final String label, final int id,
                                           final int minValue, int maxValue,
                                           final OnControlChangeListener listener) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = FloaterStyles.dp(context, 9);
        container.setPadding(0, pad, 0, pad);

        final TextView title = new TextView(context);
        title.setTextColor(nin.app.core.AppColors.TEXT_SUBTITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setText(formatSeekBarTitle(label, id, minValue));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        container.addView(title);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(maxValue - minValue);
        seekBar.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.getThumb().setColorFilter(FloaterStyles.PrimaryColor, PorterDuff.Mode.SRC_IN);
            seekBar.getProgressDrawable().setColorFilter(FloaterStyles.PrimaryColor, PorterDuff.Mode.SRC_IN);
        }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + minValue;
                title.setText(formatSeekBarTitle(label, id, value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = seekBar.getProgress() + minValue;
                if (listener != null) {
                    listener.onControlChanged(id, value);
                }
            }
        });
        container.addView(seekBar);
        return container;
    }

    public static String formatSeekBarTitle(String label, int id, int value) {
        if (id == 61) {
            return value == 0 ? "Silent Aim Target: Head" : "Silent Aim Target: Body";
        }
        if (id == 62) {
            return value == 0 ? "Target Priority: AimFov" : "Target Priority: AimDistance";
        }
        return label + ": " + value;
    }

    public static void updateSeekBarText(TextView textView, String name, int value, String type) {
        if (textView == null) return;
        if (type.equals("Color")) {
            switch(value) {
                case 0: textView.setText(name + ": White"); break;
                case 1: textView.setText(name + ": Green"); break;
                case 2: textView.setText(name + ": Blue"); break;
                case 3: textView.setText(name + ": Red"); break;
                case 4: textView.setText(name + ": Black"); break;
                case 5: textView.setText(name + ": Yellow"); break;
                case 6: textView.setText(name + ": Cyan"); break;
                case 7: textView.setText(name + ": Magenta"); break;
                case 8: textView.setText(name + ": Gray"); break;
                case 9: textView.setText(name + ": Purple"); break;
            }
        } else if (type.equals("BoxType")) {
            switch (value) {
                case 0: textView.setText(name + ": Stroke"); break;
                case 1: textView.setText(name + ": Filled"); break;
                case 2: textView.setText(name + ": Corner"); break;
            }
        } else if (type.equals("LineType")) {
            switch (value) {
                case 0: textView.setText(name + ": Top"); break;
                case 1: textView.setText(name + ": Center"); break;
                case 2: textView.setText(name + ": Bottom"); break;
            }
        } else if (type.equals("SilentAimTarget")) {
            textView.setText(value == 0 ? "Silent Aim Target: Head" : "Silent Aim Target: Body");
        } else if (type.equals("TargetPriority")) {
            textView.setText(value == 0 ? "Target Priority: AimFov" : "Target Priority: AimDistance");
        } else {
            textView.setText(name + ": " + value + type);
        }
    }
}
