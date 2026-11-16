package kr.javajoa.geniebreaker

import org.junit.Assert.*
import org.junit.Test

class ApkIdentityTest {
    @Test fun globalPermissionMovesIntoCloneNamespace() {
        val mapped = ApkIdentity.permission("vendor.global.SIGNATURE_PERMISSION", "com.original.app", "com.original.clone")
        assertTrue(mapped.startsWith("com.original.clone.permission."))
        assertEquals(mapped, ApkIdentity.permission("vendor.global.SIGNATURE_PERMISSION", "com.original.app", "com.original.clone"))
    }
    @Test fun providerMappingsStayStableAcrossRebuilds() {
        assertEquals("com.clone.files", ApkIdentity.authority("com.original.files", "com.original", "com.clone"))
        val global = ApkIdentity.authority("vendor.shared.provider", "com.original", "com.clone")
        assertTrue(global.startsWith("com.clone.provider."))
        assertEquals(global, ApkIdentity.authority("vendor.shared.provider", "com.original", "com.clone"))
    }
    @Test fun changingSignatureDoesNotChangePackageIdentity() {
        assertEquals("vendor.global.PERMISSION", ApkIdentity.permission("vendor.global.PERMISSION", "com.original", "com.original"))
        assertNotEquals(ApkIdentity.permission("vendor.global.PERMISSION", "com.original", "com.clone.one"), ApkIdentity.permission("vendor.global.PERMISSION", "com.original", "com.clone.two"))
    }
}
