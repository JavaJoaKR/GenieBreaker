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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class OfficialInstaller {
    private static final String PACKAGE = "kr.javajoa.geniebreaker";
    private static final String CERT_SHA256 = "220c3aaef3109278f7a8a8efb3baa8b802880a7c15d988fa7a39a30452fbe069";
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private final Context context;
    private final PackageManager packages;
    final SharedPreferences state;

    OfficialInstaller(Context context) {
        this.context = context.getApplicationContext();
        packages = context.getPackageManager();
        state = context.getSharedPreferences("installer", Context.MODE_PRIVATE);
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
            return packages.getPackageInfo(PACKAGE, signingFlags());
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
    private static int signingFlags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private static long versionOf(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    void begin() {
        if (!BUSY.compareAndSet(false, true)) return;
        String attempt = UUID.randomUUID().toString();
        state.edit().putString("attempt", attempt).putString("result", "최신 버전을 확인하는 중입니다.").apply();
        new Thread(() -> downloadAndSubmit(attempt), "official-installer").start();
    }

    private void downloadAndSubmit(String attempt) {
        File apk = new File(context.getCacheDir(), "geniebreaker.apk");
        try {
            LatestRelease release = LatestRelease.fetch();
            PackageInfo current = installed();
            if (current != null) {
                if (!officialSigner(current)) throw new IOException("현재 설치된 앱의 서명이 공식 APK와 다릅니다.");
                if (versionOf(current) >= release.version) {
                    complete(attempt, "현재 버전 " + current.versionName + " · 공개된 버전 0.1." + release.version);
                    return;
                }
            }
            state.edit().putString("result", "GenieBreaker 0.1." + release.version + " 다운로드 중").apply();
            download(apk, release);
            PackageInfo archive = packages.getPackageArchiveInfo(apk.getPath(), signingFlags());
            if (archive == null || !PACKAGE.equals(archive.packageName) || !officialSigner(archive)
                || versionOf(archive) != release.version) {
                throw new IOException("APK 패키지, 서명 또는 버전이 공식 릴리스 정보와 다릅니다.");
            }
            submit(apk, attempt);
        } catch (Exception error) {
            String detail = error.getMessage();
            complete(attempt, "설치 요청을 완료하지 못했습니다.\n" + (detail == null ? error.getClass().getSimpleName() : detail));
        } finally {
            apk.delete();
        }
    }

    private void download(File apk, LatestRelease release) throws IOException {
        HttpURLConnection connection = LatestRelease.connect(release.url);
        MessageDigest hash = digest();
        try {
            LatestRelease.requireSuccess(connection);
            String host = connection.getURL().getHost();
            if (!host.equals("github.com") && !host.equals("release-assets.githubusercontent.com")
                && !host.equals("objects.githubusercontent.com")) {
                throw new IOException("공식 GitHub 다운로드 주소를 확인할 수 없습니다.");
            }
            long size = 0;
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    size += count;
                    if (size > release.size) throw new IOException("APK 크기가 릴리스 정보와 다릅니다.");
                    hash.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                }
            }
            if (size != release.size || !release.sha256.equals(hex(hash.digest()))) {
                throw new IOException("다운로드한 APK가 공식 릴리스의 크기 또는 SHA-256과 일치하지 않습니다.");
            }
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
            Intent result = new Intent(context, InstallerReceiver.class)
                .setAction(InstallerReceiver.ACTION).putExtra("attempt", attempt);
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
