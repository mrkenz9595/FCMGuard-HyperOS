package com.reed.fcmguard;

import android.content.Context;

/**
 * Backwards compatibility wrapper forwarding to {@link VendorSettings}.
 */
public final class HyperOsSettings {
    private HyperOsSettings() {}

    public static boolean openAutoStartManager(Context context) {
        return VendorSettings.openAutoStartManager(context);
    }

    public static boolean openAppPermissionEditor(Context context, String packageName) {
        return VendorSettings.openAppPermissionEditor(context, packageName);
    }
}
