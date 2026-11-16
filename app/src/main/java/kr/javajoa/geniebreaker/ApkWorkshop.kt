package kr.javajoa.geniebreaker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.UUID
import javax.security.auth.x500.X500Principal

internal data class ApkEditSpec(val label: String, val packageName: String, val versionCode: Int, val freshKey: Boolean = false)
internal data class EditedApk(val id: String, val file: File, val packageName: String, val certificate: String)

/** Local-only APK editor. Never modifies the installed source or deletes its data. */
internal class ApkWorkshop(private val context: Context) {
    fun build(source: ApplicationInfo, spec: ApkEditSpec, progress: (String) -> Unit = {}): EditedApk {
        require(Build.VERSION.SDK_INT >= 26) { "APK 편집은 Android 8 이상에서 지원합니다." }
        require(spec.label.isNotBlank() && spec.label.length <= 80) { "앱 이름을 1~80자로 입력하세요." }
        require(spec.packageName.length < 128 && spec.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "패키지명을 영문으로 입력하세요. 예: com.example.tv" }
        require(spec.versionCode > 0) { "버전 코드는 1 이상이어야 합니다." }
        require(spec.packageName != context.packageName) { "GenieBreaker 자체의 패키지명은 사용할 수 없습니다." }
        if (spec.packageName != source.packageName) {
            val exists = try { context.packageManager.getPackageInfo(spec.packageName, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
            require(!exists) { "이 패키지명의 앱이 이미 설치되어 있습니다. 다른 패키지명을 선택하세요." }
        }
        val paths = listOf(source.sourceDir) + (source.splitSourceDirs?.toList() ?: emptyList())
        require(paths.sumOf { File(it).length() } <= 200L * 1024 * 1024) { "32비트 기기 보호를 위해 원본 합계 200MB 이하만 처리합니다." }
        val id = UUID.randomUUID().toString()
        val work = File(context.cacheDir, "apk-work/$id").apply { mkdirs() }
        val output = File(context.filesDir, "edited/$id.apk").apply { parentFile?.mkdirs() }
        try {
            progress("APK를 읽고 있습니다")
            val bundle = ApkBundle()
            var apk: ApkModule? = null
            try {
                paths.forEachIndexed { index, path -> bundle.addModule(ApkModule.loadApkFile(File(path), if (index == 0) "base" else "split_$index")) }
                progress(if (paths.size > 1) "분할 APK를 병합하고 있습니다" else "앱 정보를 수정하고 있습니다")
                apk = if (paths.size > 1) bundle.mergeModules() else bundle.apkModuleList.first()
                val module = requireNotNull(apk)
                val manifest = module.androidManifest
                val original = manifest.packageName
                val root = manifest.manifestElement
                require(root.searchAttributeByName("sharedUserId") == null) { "공유 시스템 UID 앱은 안전하게 재서명할 수 없습니다." }
                // Components retain their original bytecode namespaces; provider identifiers must be unique.
                val elements = manifest.recursive(ResXmlElement::class.java)
                while (elements.hasNext()) {
                    val element = elements.next()
                    val tag = element.getName(false)
                    val attributes = element.attributes
                    while (attributes.hasNext()) {
                        val attribute = attributes.next()
                        val value = attribute.valueAsString ?: continue
                        val name = attribute.getName(false)
                        val componentName = name in setOf("name", "targetActivity", "backupAgent", "appComponentFactory", "manageSpaceActivity", "parentActivityName") &&
                            (tag in setOf("application", "activity", "activity-alias", "service", "receiver", "provider") || name == "parentActivityName")
                        when {
                            componentName && name != "authorities" -> {
                                val qualified = when { value.startsWith(".") -> original + value; !value.contains('.') -> "$original.$value"; else -> value }
                                attribute.setValueAsString(qualified)
                            }
                            name == "authorities" -> attribute.setValueAsString(value.split(';').joinToString(";") { authority ->
                                if (spec.packageName == original) authority
                                else if (authority.startsWith(original)) spec.packageName + authority.removePrefix(original)
                                else "$authority.gb${id.take(8)}"
                            })
                            tag in setOf("permission", "permission-tree", "permission-group", "uses-permission") && name == "name" && value.startsWith("$original.") ->
                                attribute.setValueAsString(spec.packageName + value.removePrefix(original))
                            name in setOf("permission", "readPermission", "writePermission", "taskAffinity") && value.startsWith(original) ->
                                attribute.setValueAsString(spec.packageName + value.removePrefix(original))
                            name == "label" && tag in setOf("activity", "activity-alias") -> attribute.setValueAsString(spec.label)
                        }
                    }
                }
                module.setPackageName(spec.packageName)
                manifest.setApplicationLabel(spec.label)
                manifest.setVersionCode(spec.versionCode)
                manifest.setExtractNativeLibs(true)
                module.setApkSignatureBlock(null)
                paths.forEach { path -> java.util.zip.ZipFile(path).use { zip ->
                    zip.entries().asSequence().map { it.name }.filter { name ->
                        name.equals("META-INF/MANIFEST.MF", true) || name.matches(Regex("META-INF/[^/]+\\.(SF|RSA|DSA|EC)", RegexOption.IGNORE_CASE))
                    }.forEach { module.removeInputSource(it) }
                } }
                module.refreshManifest(); module.refreshTable()
                val unsigned = File(work, "unsigned.apk")
                module.writeApk(unsigned)
                progress("새 서명으로 APK를 생성하고 있습니다")
                val aliasBase = MessageDigest.getInstance("SHA-256").digest(source.packageName.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
                val alias = "gb-edit-$aliasBase" + if (spec.freshKey) "-$id" else ""
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (!store.containsAlias(alias)) {
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                        initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                            .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                            .setCertificateSubject(X500Principal("CN=GenieBreaker Local APK"))
                            .setCertificateSerialNumber(java.math.BigInteger.valueOf(System.currentTimeMillis()))
                            .setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 20L * 365 * 24 * 60 * 60 * 1000)).build())
                        generateKeyPair()
                    }
                }
                val certificate = store.getCertificate(alias) as X509Certificate
                val signer = ApkSigner.SignerConfig.Builder("GenieBreaker", store.getKey(alias, null) as PrivateKey, listOf(certificate)).build()
                ApkSigner.Builder(listOf(signer)).setInputApk(unsigned).setOutputApk(output).setMinSdkVersion(26)
                    .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign()
                progress("서명과 패키지 정보를 검증하고 있습니다")
                val verification = ApkVerifier.Builder(output).setMinCheckedPlatformVersion(26).build().verify()
                require(verification.isVerified) { "생성한 APK의 서명 검증에 실패했습니다: ${verification.errors}" }
                val parsed = context.packageManager.getPackageArchiveInfo(output.absolutePath, 0)
                require(parsed?.packageName == spec.packageName) { "수정된 패키지 정보를 Android가 읽지 못했습니다." }
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
                File(output.parentFile, "$id.json").writeText(org.json.JSONObject().put("source", source.packageName).put("package", spec.packageName).put("label", spec.label).put("certificate", fingerprint).toString())
                return EditedApk(id, output, spec.packageName, fingerprint)
            } finally {
                apk?.close(); bundle.close()
            }
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally { work.deleteRecursively() }
    }
}
