
package com.yiguihai.tun2socks;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String appName;
    public final String packageName;
    public final Drawable icon;
    public boolean isSelected;
    public final boolean isSystemApp;
    public final int uid; // Added UID field

    // Constructor for backward compatibility, calls the main constructor
    public AppInfo(String appName, String packageName, Drawable icon, boolean isSelected) {
        this(appName, packageName, icon, isSelected, false, 0); // Default isSystemApp to false, uid to 0
    }

    // Main constructor with all fields, including uid
    public AppInfo(String appName, String packageName, Drawable icon, boolean isSelected, boolean isSystemApp, int uid) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = isSelected;
        this.isSystemApp = isSystemApp;
        this.uid = uid;
    }
}
