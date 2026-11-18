package kr.javajoa.tvinstallcheck;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class InstallProbe {
    private static final String PACKAGE = "kr.javajoa.geniebreaker";
    private static final int VERSION = 170;
    private static final String URL =
        "https://github.com/JavaJoaKR/GenieBreaker/releases/download/build-170-ddc2afd/GenieBreaker-170-ddc2afd.apk";
    private static final String APK_SHA256 = "a9d71779df23ceb67da4b8a2607188058f5b760fdc09a74a58cd26f3b772be7a";
    private static final String CERT_SHA256 = "220c3aaef3109278f7a8a8efb3baa8b802880a7c15d988fa7a39a30452fbe069";
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private final Context context;
    private final PackageManager packages;
    final SharedPreferences state;

    InstallProbe(Context context) {
        this.context = context.getApplicationContext();
        packages = context.getPackageManager();
        state = context.getSharedPreferences("install-check", Context.MODE_PRIVATE);
    }

    boolean busy() { return BUSY.get(); }

    boolean isCurrent(String attempt) {
        return attempt != null && attempt.equals(state.getString("attempt", null));
    }

    void complete(String attempt, String result) {
        if (!isCurrent(attempt)) return;
        BUSY.set(false);
        state.edit().remove("attempt").putString("result", result).apply();
    }

    @SuppressWarnings("deprecation")
    private PackageInfo installed() {
        try {
            return packages.getPackageInfo(PACKAGE, Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean officialSigner(PackageInfo info) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
            ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        return signatures != null && signatures.length == 1
            && CERT_SHA256.equals(hex(digest().digest(signatures[0].toByteArray())));
    }

    String installedSummary() {
        PackageInfo info = installed();
        if (info == null) return "기존 GenieBreaker 설치본 없음";
        return "설치된 GenieBreaker " + info.versionName
            + (officialSigner(info) ? " · 공식 APK와 서명 일치" : " · 공식 APK와 서명 불일치");
    }

    @SuppressWarnings("deprecation")
    String conflict() {
        PackageInfo info = installed();
        if (info == null) return null;
        if (!officialSigner(info)) return "설치 실패 원인: 기존 GenieBreaker와 공식 APK의 서명이 다릅니다.";
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return version > VERSION ? "기존 버전이 공식 0.1.170보다 높습니다. Android는 다운그레이드 설치를 거부합니다." : null;
    }

    void begin() {
        if (!BUSY.compareAndSet(false, true)) return;
        String attempt = UUID.randomUUID().toString();
        state.edit().putString("attempt", attempt).putString("result", "공식 APK를 내려받는 중입니다.").apply();
        new Thread(() -> downloadAndSubmit(attempt), "install-check").start();
    }

    private void downloadAndSubmit(String attempt) {
        File apk = null;
        try {
            apk = File.createTempFile("official-", ".apk", context.getCacheDir());
            download(apk);
            submit(apk, attempt);
        } catch (Exception error) {
            complete(attempt, "설치 진단 요청 실패\n" + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (apk != null) apk.delete();
        }
    }

    private void download(File apk) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(URL).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        MessageDigest hash = digest();
        try {
            int response = connection.getResponseCode();
            if (response != 200 || !"https".equals(connection.getURL().getProtocol())) {
                throw new IOException("공식 APK 다운로드 응답: HTTP " + response);
            }
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[32 * 1024];
                long size = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_BYTES) throw new IOException("APK 크기 한도를 초과했습니다.");
                    hash.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                }
            }
            if (!APK_SHA256.equals(hex(hash.digest()))) throw new IOException("공식 APK의 SHA-256과 일치하지 않습니다.");
        } finally {
            connection.disconnect();
        }
    }

    private void submit(File apk, String attempt) throws IOException {
        PackageInstaller installer = packages.getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(PACKAGE);
        params.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        int id = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                session.fsync(out);
            }
            Intent result = new Intent(context, InstallCheckReceiver.class)
                .setAction(InstallCheckReceiver.ACTION).putExtra("attempt", attempt);
            PendingIntent pending = PendingIntent.getBroadcast(context, id, result,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            state.edit().putString("result", "Android 설치 확인을 기다리고 있습니다.").apply();
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            if (!committed) {
                try { installer.abandonSession(id); } catch (RuntimeException ignored) { }
            }
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }
}
