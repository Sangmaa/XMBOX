package com.fongmi.android.tv.download;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 下载根目录管理：优先使用公共 Download/XMBOX，不可写时回退到应用私有目录。
 */
public final class DownloadStorage {

    private static final String PUBLIC_DIR = "Download";
    private static final String APP_DIR = "XMBOX";
    private static final String PRIVATE_DIR = "Download";
    private static volatile Boolean publicWritable;

    private DownloadStorage() {
    }

    public static File publicRoot() {
        return new File(new File(Environment.getExternalStorageDirectory(), PUBLIC_DIR), APP_DIR);
    }

    public static File privateRoot() {
        File dir = App.get().getExternalFilesDir(null);
        return new File(dir == null ? App.get().getFilesDir() : dir, PRIVATE_DIR);
    }

    public static File root() {
        File dir = isPublicWritable() ? publicRoot() : privateRoot();
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static synchronized boolean isPublicWritable() {
        if (publicWritable != null) return publicWritable;
        boolean result = false;
        try {
            boolean permission = Build.VERSION.SDK_INT < 23
                    || ContextCompat.checkSelfPermission(App.get(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    || (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager());
            if (permission) {
                File dir = publicRoot();
                if (!dir.exists()) dir.mkdirs();
                File probe = new File(dir, ".write_test");
                try (FileOutputStream fos = new FileOutputStream(probe)) {
                    fos.write(1);
                }
                probe.delete();
                result = true;
            }
        } catch (Exception e) {
            result = false;
        }
        publicWritable = result;
        return result;
    }

    public static synchronized void resetWritableCache() {
        publicWritable = null;
    }

    public static long freeSpace() {
        try {
            File dir = root();
            StatFs stat = new StatFs(dir.getAbsolutePath());
            return stat.getAvailableBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    public static File ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("无法创建目录: " + parent);
        return file;
    }
}
