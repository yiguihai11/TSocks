
package com.yiguihai.tun2socks;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String appName;
    public final String packageName;
    public final Drawable icon;
    public boolean isSelected;

    public AppInfo(String appName, String packageName, Drawable icon, boolean isSelected) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = isSelected;
    }
}
