package kr.javajoa.geniebreaker

import org.json.JSONArray

internal data class UpdateAsset(val version: Long, val url: String)
internal object ReleaseCatalog {
    private const val PREFIX = "https://github.com/JavaJoaKR/GenieBreaker/releases/download/"
    fun latest(releases: JSONArray): UpdateAsset {
        val candidates = mutableListOf<UpdateAsset>()
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft") || !release.optString("body").contains("[channel:main]")) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                if (asset.optString("state", "uploaded") != "uploaded") continue
                val match = Regex("GenieBreaker-(\\d+)-[0-9a-f]+\\.apk").matchEntire(asset.optString("name")) ?: continue
                val version = match.groupValues[1].toLongOrNull() ?: continue
                val url = asset.optString("browser_download_url")
                if (url.startsWith(PREFIX)) candidates += UpdateAsset(version, url)
            }
        }
        return candidates.maxByOrNull { it.version } ?: error("사용 가능한 main APK 릴리스를 아직 찾지 못했습니다")
    }
}
