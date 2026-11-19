package kr.javajoa.tvinstallcheck;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LatestRelease {
    private static final String REPOSITORY = "JavaJoaKR/GenieBreaker";
    private static final String ENDPOINT = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final String APK_NAME = "GenieBreaker.apk";
    private static final Pattern TAG = Pattern.compile("build-([1-9][0-9]*)-[0-9a-f]{7}");
    private static final Pattern SHA256 = Pattern.compile("sha256:([0-9a-f]{64})");
    private static final long MAX_APK_BYTES = 100L * 1024 * 1024;

    final long version;
    final String url;
    final String sha256;
    final long size;

    private LatestRelease(long version, String url, String sha256, long size) {
        this.version = version;
        this.url = url;
        this.sha256 = sha256;
        this.size = size;
    }

    static LatestRelease fetch() throws IOException, JSONException {
        HttpURLConnection connection = connect(ENDPOINT);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            requireSuccess(connection);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > 1024 * 1024) {
                        throw new IOException("릴리스 정보가 크기 한도를 초과했습니다.");
                    }
                    body.write(buffer, 0, count);
                }
                return parse(new JSONObject(new String(body.toByteArray(), StandardCharsets.UTF_8)));
            }
        } finally {
            connection.disconnect();
        }
    }

    static LatestRelease parse(JSONObject release) throws IOException, JSONException {
        String tag = release.optString("tag_name");
        Matcher build = TAG.matcher(tag);
        if (release.optBoolean("draft") || release.optBoolean("prerelease")
            || !release.optString("body").contains("[channel:main]") || !build.matches()) {
            throw new IOException("배포용 릴리스를 확인할 수 없습니다.");
        }
        String url = "https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/" + APK_NAME;
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            Matcher hash = SHA256.matcher(asset.optString("digest"));
            long size = asset.optLong("size");
            if (APK_NAME.equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))
                && url.equals(asset.optString("browser_download_url"))
                && size > 0 && size <= MAX_APK_BYTES && hash.matches()) {
                return new LatestRelease(Long.parseLong(build.group(1)), url, hash.group(1), size);
            }
        }
        throw new IOException("릴리스에 검증할 수 있는 APK가 없습니다.");
    }

    static HttpURLConnection connect(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("User-Agent", "GenieBreakerInstaller/1.2");
        return connection;
    }

    static void requireSuccess(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK || !"https".equals(connection.getURL().getProtocol())) {
            throw new IOException("GitHub 요청 실패 (HTTP " + status + ")");
        }
    }
}
