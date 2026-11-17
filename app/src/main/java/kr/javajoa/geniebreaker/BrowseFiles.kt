package kr.javajoa.geniebreaker

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

internal object BrowseFiles {
    private data class Grant(val file: File, val expires: Long)
    private val grants = java.util.concurrent.ConcurrentHashMap<String, Grant>()
    fun grant(context: Context, file: File): Uri {
        val now = System.currentTimeMillis(); grants.entries.removeIf { it.value.expires < now }
        val id = UUID.randomUUID().toString(); grants[id] = Grant(file.canonicalFile, now + 3600000)
        return Uri.parse("content://${context.packageName}.updates/browse/$id")
    }
    fun file(uri: Uri): File = grants[uri.lastPathSegment]?.takeIf { it.expires > System.currentTimeMillis() }?.file ?: throw java.io.FileNotFoundException()
    fun type(file: File): String = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
}
