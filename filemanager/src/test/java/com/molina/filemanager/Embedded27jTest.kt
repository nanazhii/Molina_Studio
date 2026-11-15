// molina-27j
package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test

class Embedded27jTest {
    @Test
    fun crumbsInternalRoot() {
        assertEquals(listOf("Internal"), DrawerFs.crumbs("/storage/emulated/0"))
    }

    @Test
    fun crumbsNestedFolder() {
        assertEquals(listOf("Internal", "Molina", "Film"), DrawerFs.crumbs("/storage/emulated/0/Molina/Film"))
    }

    @Test
    fun crumbsFilesystemRootAndEmpty() {
        assertEquals(listOf("/"), DrawerFs.crumbs("/"))
        assertEquals(listOf("/"), DrawerFs.crumbs(""))
    }

    @Test
    fun crumbsExternalVolume() {
        assertEquals(listOf("storage", "1234-ABCD", "x"), DrawerFs.crumbs("/storage/1234-ABCD/x"))
    }
}
