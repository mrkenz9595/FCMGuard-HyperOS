package com.reed.fcmguard;

import android.os.Build;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * OEM and ROM detection helper for Vivo OriginOS / FuntouchOS and Xiaomi HyperOS / MIUI.
 */
public final class DeviceHelper {

    private DeviceHelper() {}

    public static boolean isVivo() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo") ||
                brand.contains("vivo") || brand.contains("iqoo")) {
            return true;
        }
        return !TextUtils.isEmpty(getSystemProperty("ro.vivo.os.build.version")) ||
                !TextUtils.isEmpty(getSystemProperty("ro.vivo.os.version")) ||
                !TextUtils.isEmpty(getSystemProperty("ro.vivo.os.name")) ||
                !TextUtils.isEmpty(getSystemProperty("ro.iqoo.os.name"));
    }

    public static boolean isXiaomi() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ||
                brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            return true;
        }
        return !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name")) ||
                !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.code"));
    }

    public static String getRomName() {
        if (isVivo()) {
            String osName = getSystemProperty("ro.vivo.os.name");
            if (!TextUtils.isEmpty(osName)) {
                return osName;
            }
            String vivoVer = getSystemProperty("ro.vivo.os.build.version");
            if (!TextUtils.isEmpty(vivoVer)) {
                return "OriginOS " + vivoVer;
            }
            return "OriginOS";
        }
        if (isXiaomi()) {
            String miuiVer = getSystemProperty("ro.miui.ui.version.name");
            if (!TextUtils.isEmpty(miuiVer)) {
                if (miuiVer.toLowerCase(Locale.ROOT).contains("hyperos") ||
                        miuiVer.startsWith("OS") || miuiVer.startsWith("V816")) {
                    return "HyperOS";
                }
                return miuiVer;
            }
            return "HyperOS";
        }
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static String getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(c, key, "");
        } catch (Throwable ignored) {
            return "";
        }
    }
}
