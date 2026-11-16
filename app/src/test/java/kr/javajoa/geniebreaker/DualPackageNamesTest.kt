package kr.javajoa.geniebreaker
import org.junit.Assert.*
import org.junit.Test
class DualPackageNamesTest {
    @Test fun automaticNameIsValidStableAndDistinct() {
        val name = DualPackageNames.candidate("vendor.example.original")
        assertTrue(name.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")))
        assertEquals(name, DualPackageNames.candidate("vendor.example.original"))
        assertNotEquals(name, DualPackageNames.candidate("vendor.example.other"))
        assertNotEquals(name, DualPackageNames.candidate("vendor.example.original", 1))
        assertTrue(name.length < 128)
    }
}
