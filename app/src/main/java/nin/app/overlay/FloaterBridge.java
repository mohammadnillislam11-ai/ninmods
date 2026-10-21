package nin.app.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import nin.app.core.AppColors;

import com.topjohnwu.superuser.ipc.RootService;

import java.util.ArrayList;
import java.util.List;

import nin.app.core.RootServices;

public final class FloaterBridge {

    private FloaterBridge() {}

    public static void addCategory(final Floater floater, final String name, final int categoryHeightDp) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> addCategory(floater, name, categoryHeightDp));
            return;
        }

        try {
            String trimmedName = name != null ? name.trim() : "";
            if (floater == null || trimmedName.isEmpty()) return;

            String normalizedName = floater.findExistingCategoryKeyIgnoreCase(trimmedName);
            if (normalizedName == null) {
                normalizedName = trimmedName;
                floater.nativeControlsByCategory.put(normalizedName, new ArrayList<>());
            }
            if (!floater.categoryWidgetsByName.containsKey(normalizedName)) {
                floater.categoryWidgetsByName.put(normalizedName, new ArrayList<>());
            }

            floater.selectedCategoryName = normalizedName;
            floater.rebuildFirstSectionMenus();
            floater.selectCategory(normalizedName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addSwitch(final Floater floater, final Context context,
                                 final String name, final int ID, final int switchHeightDp) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> addSwitch(floater, context, name, ID, switchHeightDp));
            return;
        }

        try {
            if (floater == null || context == null) return;
            String categoryName = floater.selectedCategoryName;
            if (categoryName == null || categoryName.trim().isEmpty()) {
                categoryName = "Memory";
                floater.selectedCategoryName = categoryName;
            }

            List<NativeControlItem> controls = floater.nativeControlsByCategory.get(categoryName);
            if (controls == null) {
                controls = new ArrayList<>();
                floater.nativeControlsByCategory.put(categoryName, controls);
            }

            boolean exists = false;
            for (NativeControlItem item : controls) {
                if (item.id == ID) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                controls.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, name, ID, 0, 1));
            }

            floater.rebuildFirstSectionMenus();
            floater.selectCategory(categoryName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addCheckBox(final Floater floater, final Context context,
                                   final String name, final int ID, final int heightDp) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> addCheckBox(floater, context, name, ID, heightDp));
            return;
        }

        try {
            if (floater == null || context == null) return;

            LinearLayout container = new LinearLayout(context);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    FloaterStyles.dp(context, heightDp)
            );
            containerParams.setMargins(0, FloaterStyles.dp(context, 2), 0, FloaterStyles.dp(context, 2));
            container.setLayoutParams(containerParams);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setPadding(FloaterStyles.dp(context, 12), 0, FloaterStyles.dp(context, 12), 0);

            GradientDrawable bg = FloaterStyles.createSolidBg(context, FloaterStyles.CardBackground, 16);
            container.setBackground(bg);

            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            textView.setText(name);
            textView.setTextColor(AppColors.TEXT_WHITE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            textView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            textView.setSingleLine(true);
            container.addView(textView);

            final CheckBox checkBox = new CheckBox(context);
            checkBox.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 50), FloaterStyles.dp(context, 28)));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int[][] states = new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                };
                int[] colors = new int[]{FloaterStyles.PrimaryColor, Color.GRAY};
                ColorStateList csl = new ColorStateList(states, colors);
                checkBox.setButtonTintList(csl);
            }

            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Floater.ChangesID(ID, isChecked ? 1 : 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                }
            });

            container.addView(checkBox);
            floater.addWidgetToCurrentCategory(container);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addRadioGroup(final Floater floater, final Context context,
                                     final String name, final String[] options,
                                     final int defaultIndex, final int ID) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> addRadioGroup(floater, context, name, options, defaultIndex, ID));
            return;
        }

        try {
            if (floater == null || context == null) return;

            LinearLayout container = new LinearLayout(context);
            container.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(FloaterStyles.dp(context, 12), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 12), FloaterStyles.dp(context, 8));

            GradientDrawable bg = FloaterStyles.createSolidBg(context, FloaterStyles.CardBackground, 16);
            container.setBackground(bg);

            TextView titleView = new TextView(context);
            titleView.setText(name);
            titleView.setTextColor(AppColors.TEXT_WHITE);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            titleView.setPadding(0, 0, 0, FloaterStyles.dp(context, 8));
            container.addView(titleView);

            RadioGroup radioGroup = new RadioGroup(context);
            radioGroup.setOrientation(LinearLayout.HORIZONTAL);
            radioGroup.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int i = 0; i < options.length; i++) {
                RadioButton radioButton = new RadioButton(context);
                radioButton.setText(options[i]);
                radioButton.setTextColor(AppColors.TEXT_WHITE);
                radioButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                radioButton.setId(i);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    int[][] states = new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    };
                    int[] colors = new int[]{FloaterStyles.PrimaryColor, Color.GRAY};
                    ColorStateList csl = new ColorStateList(states, colors);
                    radioButton.setButtonTintList(csl);
                }

                RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f
                );
                radioButton.setLayoutParams(params);
                radioButton.setGravity(Gravity.CENTER);
                radioGroup.addView(radioButton);
            }

            radioGroup.check(defaultIndex);
            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    Floater.ChangesID(ID, checkedId);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        group.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                }
            });

            container.addView(radioGroup);
            floater.addWidgetToCurrentCategory(container);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addSeekBar(final Floater floater, final Context context,
                                  final String name, int value, int max,
                                  final String type, final int ID) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> addSeekBar(floater, context, name, value, max, type, ID));
            return;
        }

        try {
            if (floater == null || context == null) return;

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            linearLayout.setPadding(
                    FloaterStyles.dp(context, 12),
                    FloaterStyles.dp(context, 8),
                    FloaterStyles.dp(context, 12),
                    FloaterStyles.dp(context, 8)
            );
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            GradientDrawable bg = FloaterStyles.createSolidBg(context, FloaterStyles.CardBackground, 16);
            linearLayout.setBackground(bg);

            final TextView textView = new TextView(context);
            textView.setTextColor(AppColors.TEXT_WHITE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            textView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            textView.setSingleLine(true);

            WidgetFactory.updateSeekBarText(textView, name, value, type);

            SeekBar seekBar = new SeekBar(context);
            LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            seekBar.setLayoutParams(seekBarParams);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                seekBar.getThumb().setColorFilter(FloaterStyles.PrimaryColor, PorterDuff.Mode.SRC_IN);
                seekBar.getProgressDrawable().setColorFilter(FloaterStyles.PrimaryColor, PorterDuff.Mode.SRC_IN);
            }

            seekBar.setProgress(value);
            seekBar.setMax(max);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    WidgetFactory.updateSeekBarText(textView, name, i, type);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Floater.ChangesID(ID, seekBar.getProgress());
                }
            });

            linearLayout.addView(textView);
            linearLayout.addView(seekBar);
            floater.addWidgetToCurrentCategory(linearLayout);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendFunctionMessage(Context context, Messenger remoteMessenger, Messenger replyTo,
                                           ServiceConnection conn, String pkg, String lib, String offset, String hex) {
        Message message = Message.obtain(null, RootServices.MSG_GETINFO);
        message.getData().putString("pkg", pkg);
        message.getData().putString("fileSo", lib);
        message.getData().putInt("offset", Integer.decode(offset));
        message.getData().putString("hexNumber", hex);
        message.replyTo = replyTo;
        try {
            if (remoteMessenger != null) {
                remoteMessenger.send(message);
            } else if (context != null && conn != null) {
                Intent intent = new Intent(context, RootServices.class);
                RootService.bind(intent, conn);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
