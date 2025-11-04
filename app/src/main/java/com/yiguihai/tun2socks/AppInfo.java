
package com.yiguihai.tun2socks;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String appName;
    public final String packageName;
    public final Drawable icon;
    public boolean isSelected;
    public final boolean isSystemApp;

    public AppInfo(String appName, String packageName, Drawable icon, boolean isSelected) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = isSelected;
        this.isSystemApp = false; // Default for backward compatibility
    }

    public AppInfo(String appName, String packageName, Drawable icon, boolean isSelected, boolean isSystemApp) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = isSelected;
        this.isSystemApp = isSystemApp;
    }
}
