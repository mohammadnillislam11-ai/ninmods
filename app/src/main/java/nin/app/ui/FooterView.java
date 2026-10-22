package nin.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import nin.app.R;
import nin.app.core.AppColors;

/**
 * Universal Fixed Screen Footer Component.
 * Encapsulates the official version number directly loaded from version.xml via view_footer.xml.
 * Permanently fixed at the bottom of the screen across all pages.
 */
public class FooterView extends FrameLayout {

    private TextView tvFooterVersion;

    public FooterView(Context context) {
        super(context);
        init(context);
    }

    public FooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FooterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
        LayoutInflater.from(context).inflate(R.layout.view_footer, this, true);
        tvFooterVersion = findViewById(R.id.tvFooterVersion);
        if (tvFooterVersion != null) {
            tvFooterVersion.setText("Version: " + context.getString(R.string.app_version));
            tvFooterVersion.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        }
    }

    public static FooterView create(Context context) {
        FooterView footer = new FooterView(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        footer.setLayoutParams(lp);
        return footer;
    }

    public void setVersionText(String text) {
        if (tvFooterVersion != null) {
            tvFooterVersion.setText(text);
        }
    }
}
