package kr.javajoa.geniebreaker

import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.DexFile
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.rewriter.DexRewriter
import org.jf.dexlib2.rewriter.Rewriter
import org.jf.dexlib2.rewriter.RewriterModule
import org.jf.dexlib2.rewriter.Rewriters
import org.jf.dexlib2.writer.pool.DexPool
import java.io.File

/** Narrow startup interoperability profile, independent of ad/native hook patches. */
internal object NetflixCompatibility {
    const val PACKAGE = "com.netflix.ninja"
    const val MARKER = "CertCheck failed, crash!!!"
    private val versions = mapOf("13.0.1 build 25028" to 25028L,
        "13.2.1 build 27207" to 27207L, "13.2.2 build 27250" to 27250L)
    fun supported(packageName: String, version: String?, code: Long) =
        packageName == PACKAGE && versions[version] == code
    fun validate(packageName: String, version: String?, code: Long) {
        require(supported(packageName, version, code)) {
            "넷플릭스 실행 호환성 미지원 버전: $packageName / $version ($code). 지원: 13.0.1 build 25028, 13.2.1 build 27207, 13.2.2 build 27250."
        }
    }
    fun effective(options: CloneOptions) = if (options.netflixCompatibility)
        options.copy(renameResources = false, isolateTasks = false, extractLibraries = true) else options

    fun read(file: File): DexFile {
        require(file.length() in 1..(48L * 1024 * 1024)) { "DEX가 비어 있거나 32비트 처리 한도(48MB)를 초과했습니다." }
        return file.inputStream().buffered().use { DexBackedDexFile.fromInputStream(Opcodes.getDefault(), it) }
    }
    private fun id(method: Method) = "${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}"
    fun candidates(dex: DexFile): List<String> = dex.classes.flatMap { type -> type.methods.filter { method ->
        method.implementation?.instructions?.any { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == MARKER
        } == true
    }.map { method ->
        require(AccessFlags.PUBLIC.isSet(method.accessFlags) && !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.name == "run" && method.returnType == "V" && method.parameterTypes.isEmpty() &&
            "Ljava/lang/Runnable;" in type.interfaces) { "넷플릭스 검사 코드 형태가 예상과 다릅니다. APK를 수정하지 않았습니다." }
        id(method)
    } }

    /** The caller must establish uniqueness across ALL dex files before invoking this. */
    fun patch(input: File, target: String, output: File) {
        val dex = read(input)
        require(candidates(dex) == listOf(target)) { "넷플릭스 검사 코드가 유일하지 않습니다. APK를 수정하지 않았습니다." }
        val changed = mutableSetOf<String>()
        val rewritten = DexRewriter(object : RewriterModule() {
            override fun getMethodRewriter(rewriters: Rewriters): Rewriter<Method> = Rewriter { method ->
                if (id(method) != target) method else {
                    changed += id(method)
                    ImmutableMethod(method.definingClass, method.name, method.parameters, method.returnType,
                        method.accessFlags, method.annotations, method.hiddenApiRestrictions,
                        ImmutableMethodImplementation(1, listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)), emptyList(), emptyList()))
                }
            }
        }).dexFileRewriter.rewrite(dex)
        DexPool.writeTo(output.absolutePath, rewritten)
        require(changed.size == 1 && candidates(read(output)).isEmpty()) { "넷플릭스 DEX 수정 검증에 실패했습니다." }
    }
}
