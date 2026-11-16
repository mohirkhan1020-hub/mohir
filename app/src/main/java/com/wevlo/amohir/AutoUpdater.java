package com.wevlo.amohir;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AutoUpdater {

    private static boolean dialogShowing = false;

    public static ListenerRegistration listenForUpdates(Activity activity) {
        return FirebaseFirestore.getInstance()
                .collection("app_config")
                .document("update")
                .addSnapshotListener((snap, err) -> {
                    if (snap == null || !snap.exists() || activity.isFinishing() || dialogShowing) return;

                    Long serverVer = snap.getLong("versionCode");
                    String versionName = snap.getString("versionName");
                    String apkUrl = snap.getString("apkUrl");
                    String notes = snap.getString("notes");

                    if (serverVer == null || apkUrl == null || apkUrl.isEmpty()) return;

                    long currentVer = getCurrentVersionCode(activity);
                    if (serverVer > currentVer) {
                        dialogShowing = true;
                        showUpdateDialog(activity, versionName != null ? versionName : "New", apkUrl, notes);
                    }
                });
    }

    private static long getCurrentVersionCode(Activity activity) {
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pInfo.getLongVersionCode() : pInfo.versionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    private static void showUpdateDialog(Activity activity, String versionName, String apkUrl, String notes) {
        String msg = "নতুন ভার্সন (" + versionName + ") এসেছে!\n\n"
                + (notes != null && !notes.isEmpty() ? notes : "নতুন ফিচার ও বাগ ফিক্স যুক্ত করা হয়েছে।");

        new AlertDialog.Builder(activity)
                .setTitle("🚀 App Update Available")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("Update Now", (d, w) -> {
                    dialogShowing = false;
                    downloadAndInstall(activity, apkUrl);
                })
                .setNegativeButton("Later", (d, w) -> dialogShowing = false)
                .show();
    }

    @SuppressWarnings("deprecation")
    private static void downloadAndInstall(Activity activity, String apkUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "অনুগ্রহ করে 'Allow from this source' অন করে আবার চেষ্টা করুন", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return;
        }

        ProgressDialog pd = new ProgressDialog(activity);
        pd.setTitle("Downloading Update...");
        pd.setMessage("0%");
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            try {
                HttpURLConnection conn = openWithRedirects(apkUrl, 0);
                int total = conn.getContentLength();
                File apkFile = new File(activity.getCacheDir(), "update.apk");

                try (InputStream is = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(apkFile)) {
                    byte[] buf = new byte[8192];
                    long downloaded = 0;
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        downloaded += len;
                        if (total > 0) {
                            int pct = (int) ((downloaded * 100) / total);
                            activity.runOnUiThread(() -> pd.setMessage("Downloading... " + pct + "%"));
                        }
                    }
                }

                activity.runOnUiThread(() -> {
                    pd.dismiss();
                    installApk(activity, apkFile);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static HttpURLConnection openWithRedirects(String urlStr, int hops) throws Exception {
        if (hops > 5) throw new Exception("Too many redirects");
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.connect();
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
            String next = conn.getHeaderField("Location");
            conn.disconnect();
            return openWithRedirects(next, hops + 1);
        }
        return conn;
    }

    private static void installApk(Activity activity, File apkFile) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }
}
