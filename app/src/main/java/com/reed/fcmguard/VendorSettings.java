package com.reed.fcmguard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Vendor-specific settings deep links for Vivo OriginOS / FuntouchOS and Xiaomi HyperOS / MIUI,
 * with standard Android fallbacks.
 */
public final class VendorSettings {

    private VendorSettings() {}

    public static boolean openAutoStartManager(Context context) {
        if (DeviceHelper.isVivo()) {
            if (openVivoAutoStart(context)) return true;
        } else if (DeviceHelper.isXiaomi()) {
            if (openXiaomiAutoStart(context)) return true;
        }

        // Try Vivo first, then Xiaomi, then system settings
        if (openVivoAutoStart(context)) return true;
        if (openXiaomiAutoStart(context)) return true;

        return launch(context, new Intent(Settings.ACTION_APPLICATION_SETTINGS));
    }

    private static boolean openVivoAutoStart(Context context) {
        String[][] vivoComponents = {
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.MainGuideActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"}
        };

        for (String[] target : vivoComponents) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(target[0], target[1]));
            if (launch(context, intent)) return true;
        }
        return false;
    }

    private static boolean openXiaomiAutoStart(Context context) {
        Intent intent = new Intent("miui.intent.action.OP_AUTO_START");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        if (launch(context, intent)) return true;

        Intent explicit = new Intent();
        explicit.setComponent(new ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ));
        return launch(context, explicit);
    }

    /**
     * Opens Vivo OriginOS "High background power consumption" or standard battery optimization settings.
     */
    public static boolean openBackgroundPowerManager(Context context, String packageName) {
        String pkg = packageName != null ? packageName : context.getPackageName();

        // Vivo / OriginOS / iQOO power management activities
        String[][] vivoPowerComponents = {
                {"com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"},
                {"com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"},
                {"com.iqoo.powersaving", "com.iqoo.powersaving.HighPowerConsumptionManagementActivity"},
                {"com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ApplicationPowerManagerActivity"}
        };

        for (String[] target : vivoPowerComponents) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(target[0], target[1]));
            intent.putExtra("package_name", pkg);
            intent.putExtra("packagename", pkg);
            if (launch(context, intent)) return true;
        }

        // Android standard ignore battery optimizations settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent ignoreOpt = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (launch(context, ignoreOpt)) return true;
        }

        // Power usage summary fallback
        Intent powerUsage = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
        if (launch(context, powerUsage)) return true;

        return openAppPermissionEditor(context, pkg);
    }

    /**
     * Requests ignoring battery optimization for FCM Guard on Android 6.0+.
     */
    public static boolean requestIgnoreBatteryOptimization(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
                return true;
            }
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Throwable ignored) {
                // Fallback to settings screen
                Intent settingsIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return launch(context, settingsIntent);
            }
        }
        return false;
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true;
    }

    public static boolean openAppPermissionEditor(Context context, String packageName) {
        // Vivo permission activities
        String[][] vivoActivities = {
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.SoftwareManagerActivity"}
        };
        for (String[] comp : vivoActivities) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(comp[0], comp[1]));
            intent.putExtra("packagename", packageName);
            intent.putExtra("package_name", packageName);
            if (launch(context, intent)) return true;
        }

        // Xiaomi permission activities
        String[] xiaomiActivities = {
                "com.miui.permcenter.permissions.PermissionsEditorActivity",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
        };
        for (String activityName : xiaomiActivities) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.miui.securitycenter", activityName));
            intent.putExtra("extra_pkgname", packageName);
            intent.putExtra("package_name", packageName);
            if (launch(context, intent)) return true;
        }

        // Standard App details fallback
        Intent details = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName)
        );
        details.addCategory(Intent.CATEGORY_DEFAULT);
        return launch(context, details);
    }

    private static boolean launch(Context context, Intent intent) {
        try {
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            if (intent.resolveActivity(context.getPackageManager()) == null) return false;
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
