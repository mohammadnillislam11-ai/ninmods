package com.ninmods.main.core;

import android.content.Context;
import android.util.TypedValue;

/**
 * Small helpers that are shared inside the core package.
 *
 * The file used to carry a second crypto implementation (a hard coded AES
 * key, a Base64 helper, a cache cleaner and a stream reader) that no caller
 * used any more. Beside being dead code, the embedded key was a second,
 * hard coded secret. Only the parts that are really used are kept now.
 */
public class Utils {

    /**
     * Native accessor of the package name of the running game.
     *
     * The value is resolved by the native layer, because an app may not read
     * the cmdline of a process of another user since Android 9.
     *
     * @return the package name of the game, or an empty string
     */
    public static native String getGamePackage();

    /**
     * Converts a density independent pixel value into a pixel value.
     *
     * @param context context used for the display metrics
     * @param i       value in density independent pixels
     * @return the value in pixels
     */
    public static int dp(Context context, int i) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, (float) i, context.getResources().getDisplayMetrics());
    }
}
