package kr.javajoa.geniebreaker

import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.immutable.*
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import org.jf.dexlib2.writer.pool.DexPool
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetflixCompatibilityTest {
    @get:Rule val folder = TemporaryFolder()
    private fun fixture(count: Int = 1, static: Boolean = false, runnable: Boolean = true, opcodes: Opcodes = Opcodes.getDefault()): java.io.File {
        val classes = (0 until count).map { index ->
            val type = "Lfixture/Check$index;"
            val body = ImmutableMethodImplementation(1, listOf(
                ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(NetflixCompatibility.MARKER)),
                ImmutableInstruction10x(Opcode.RETURN_VOID)), emptyList(), emptyList())
            val target = ImmutableMethod(type, "run", emptyList(), "V", AccessFlags.PUBLIC.value or
                (if (static) AccessFlags.STATIC.value else 0), emptySet(), emptySet(), body)
            val untouched = ImmutableMethod(type, "other", emptyList(), "V", AccessFlags.PUBLIC.value,
                emptySet(), emptySet(), ImmutableMethodImplementation(1, listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)), emptyList(), emptyList()))
            ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
                if (runnable) listOf("Ljava/lang/Runnable;") else emptyList(), null, emptySet(), emptyList(), listOf(target, untouched))
        }
        return folder.newFile().also { DexPool.writeTo(it.absolutePath, ImmutableDexFile(opcodes, classes)) }
    }
    @Test fun roundTripReplacesOnlyUniqueRunnableAndPreservesOtherMethods() {
        val input = fixture()
        val before = NetflixCompatibility.read(input)
        val target = NetflixCompatibility.candidates(before).single()
        val output = folder.newFile()
        NetflixCompatibility.patch(input, target, output)
        val result = NetflixCompatibility.read(output)
        assertTrue(NetflixCompatibility.candidates(result).isEmpty())
        assertEquals(2, result.classes.single().methods.count())
        assertEquals(listOf(Opcode.RETURN_VOID), result.classes.single().methods.first { it.name == "run" }.implementation!!.instructions.map { it.opcode })
        assertEquals(before.classes.single().methods.first { it.name == "other" }.implementation!!.instructions.map { it.opcode },
            result.classes.single().methods.first { it.name == "other" }.implementation!!.instructions.map { it.opcode })
    }
    @Test fun modernDexVersionIsRetained() {
        val input = fixture(opcodes = Opcodes.forDexVersion(39))
        val output = folder.newFile()
        NetflixCompatibility.patch(input, NetflixCompatibility.candidates(NetflixCompatibility.read(input)).single(), output)
        assertEquals(NetflixCompatibility.read(input).opcodes.api, NetflixCompatibility.read(output).opcodes.api)
        assertEquals("dex\n039", output.readBytes().take(7).toByteArray().toString(Charsets.US_ASCII))
    }
    @Test fun absentAndAmbiguousPatternsAreRejectedWithoutOutput() {
        for (count in listOf(0, 2)) {
            val input = fixture(count)
            val output = java.io.File(folder.root, "not-created-$count.dex")
            assertThrows(IllegalArgumentException::class.java) { NetflixCompatibility.patch(input, "Lfixture/Check0;->run()V", output) }
            assertFalse(output.exists())
        }
    }
    @Test fun staticAndNonRunnableMatchesAreRejected() {
        for (input in listOf(fixture(static = true), fixture(runnable = false))) {
            assertThrows(IllegalArgumentException::class.java) { NetflixCompatibility.candidates(NetflixCompatibility.read(input)) }
        }
    }
    @Test fun packageIsRequiredButVersionsAreNotRestricted() {
        assertTrue(NetflixCompatibility.supported("com.netflix.ninja", "13.0.1 build 25028", 25028))
        assertFalse(NetflixCompatibility.supported("com.netflix.mediaclient", "13.0.1 build 25028", 25028))
        assertTrue(NetflixCompatibility.supported("com.netflix.ninja", "13.0.1 build 25028", 25029))
        NetflixCompatibility.validate("com.netflix.ninja", "99.0", 99000)
        assertTrue(NetflixCompatibility.supported("com.netflix.ninja", null, 0))
        assertThrows(IllegalArgumentException::class.java) { NetflixCompatibility.validate("com.other", "99.0", 99000) }
    }
    @Test fun profileEnforcesPreservationOnlyWhenEnabled() {
        val generic = CloneOptions()
        assertEquals(generic, NetflixCompatibility.effective(generic))
        val profile = NetflixCompatibility.effective(generic.copy(netflixCompatibility = true, extractLibraries = false))
        assertFalse(profile.renameResources)
        assertFalse(profile.isolateTasks)
        assertTrue(profile.extractLibraries)
        assertEquals(profile, CloneOptions.from(profile.values()))
    }
}
