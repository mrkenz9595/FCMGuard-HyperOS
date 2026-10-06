package com.reed.fcmguard;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.PowerManager;

import java.lang.reflect.Method;

/**
 * Best-effort, read-only probe for Autostart and Background execution AppOps
 * on Vivo OriginOS and Xiaomi HyperOS.
 *
 * This class never changes AppOps. When the ROM blocks vendor-specific queries,
 * callers receive UNKNOWN rather than guessing.
 */
public final class AutostartStatusReader {
    // Xiaomi/HyperOS vendor AppOps
    private static final int OP_MIUI_AUTOSTART = 10008;
    private static final int OP_MIUI_AUTOSTART_SWITCH = 10053;

    // Standard AOSP / OEM AppOps for background & boot execution
    private static final int OP_RUN_IN_BACKGROUND = 63;
    private static final int OP_BOOT_COMPLETED = 67;
    private static final int OP_RUN_ANY_IN_BACKGROUND = 70;

    public enum Status {
        ENABLED,
        PARTIAL,
        DISABLED,
        UNKNOWN
    }

    private AutostartStatusReader() {}

    public static Status check(Context context, String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);

            if (DeviceHelper.isXiaomi()) {
                Status xiaomiStatus = checkXiaomi(context, info.uid, packageName);
                if (xiaomiStatus != Status.UNKNOWN) {
                    return xiaomiStatus;
                }
            } else if (DeviceHelper.isVivo()) {
                Status vivoStatus = checkVivo(context, info.uid, packageName);
                if (vivoStatus != Status.UNKNOWN) {
                    return vivoStatus;
                }
            }

            // Fallback: try Xiaomi vendor ops first if available
            Status miuiStatus = checkXiaomi(context, info.uid, packageName);
            if (miuiStatus != Status.UNKNOWN) return miuiStatus;

            // Then try standard background / boot ops
            return checkGeneric(context, info.uid, packageName);
        } catch (Throwable ignored) {
            return Status.UNKNOWN;
        }
    }

    private static Status checkXiaomi(Context context, int uid, String packageName) {
        Integer primary = checkOp(context, OP_MIUI_AUTOSTART, uid, packageName);
        Integer switchOp = checkOp(context, OP_MIUI_AUTOSTART_SWITCH, uid, packageName);

        if (isAllowed(primary) && isAllowed(switchOp)) return Status.ENABLED;
        if (isIgnored(primary) && isIgnored(switchOp)) return Status.DISABLED;
        if ((isAllowed(primary) && isIgnored(switchOp)) ||
                (isIgnored(primary) && isAllowed(switchOp))) {
            return Status.PARTIAL;
        }
        return Status.UNKNOWN;
    }

    private static Status checkVivo(Context context, int uid, String packageName) {
        Integer runBg = checkOp(context, OP_RUN_IN_BACKGROUND, uid, packageName);
        Integer bootComp = checkOp(context, OP_BOOT_COMPLETED, uid, packageName);
        Integer runAny = checkOp(context, OP_RUN_ANY_IN_BACKGROUND, uid, packageName);

        boolean batteryIgnored = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    batteryIgnored = pm.isIgnoringBatteryOptimizations(packageName);
                }
            } catch (Throwable ignored) {}
        }

        if (isAllowed(runBg) && (isAllowed(bootComp) || isAllowed(runAny) || batteryIgnored)) {
            return Status.ENABLED;
        }
        if (isIgnored(runBg) || isIgnored(runAny)) {
            return Status.DISABLED;
        }
        if (batteryIgnored || isAllowed(runBg) || isAllowed(bootComp)) {
            return Status.PARTIAL;
        }
        return Status.UNKNOWN;
    }

    private static Status checkGeneric(Context context, int uid, String packageName) {
        Integer runBg = checkOp(context, OP_RUN_IN_BACKGROUND, uid, packageName);
        Integer bootComp = checkOp(context, OP_BOOT_COMPLETED, uid, packageName);

        if (isAllowed(runBg) && isAllowed(bootComp)) return Status.ENABLED;
        if (isIgnored(runBg) && isIgnored(bootComp)) return Status.DISABLED;
        if (isAllowed(runBg) || isAllowed(bootComp)) return Status.PARTIAL;
        return Status.UNKNOWN;
    }

    private static boolean isAllowed(Integer mode) {
        return mode != null && mode == AppOpsManager.MODE_ALLOWED;
    }

    private static boolean isIgnored(Integer mode) {
        return mode != null && mode == AppOpsManager.MODE_IGNORED;
    }

    private static Integer checkOp(Context context, int op, int uid, String packageName) {
        AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (manager == null) return null;

        try {
            Method method = AppOpsManager.class.getMethod(
                    "checkOpNoThrow", int.class, int.class, String.class);
            Object result = method.invoke(manager, op, uid, packageName);
            return result instanceof Integer ? (Integer) result : null;
        } catch (Throwable ignored) {}

        try {
            Method method = AppOpsManager.class.getDeclaredMethod(
                    "checkOpNoThrow", int.class, int.class, String.class);
            method.setAccessible(true);
            Object result = method.invoke(manager, op, uid, packageName);
            return result instanceof Integer ? (Integer) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
