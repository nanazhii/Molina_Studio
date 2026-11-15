// molina-27f
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMenuArchiveTest {
    private fun state(
        targetCount: Int = 1,
        archiveAvailable: Boolean = true,
        allArchives: Boolean = false,
        archiveClipCount: Int = 0,
        selecting: Boolean = false,
        pasteAvailable: Boolean = true,
        global: Boolean = false,
    ) = EmbeddedMenu.State(
        targetCount = targetCount, hasActions = true, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = global, selecting = selecting, transferAvailable = true,
        clipCount = 0, pasteAvailable = pasteAvailable,
        archiveAvailable = archiveAvailable, allArchives = allArchives, archiveClipCount = archiveClipCount,
    )

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun plainFileOnlyOffersCompress() {
        val a = actions(state())
        assertTrue(Action.COMPRESS in a)
        assertFalse(Action.VIEW_ARCHIVE in a || Action.EXTRACT_HERE in a || Action.TEST_ARCHIVE in a || Action.EXTRACT_TO in a)
    }

    @Test
    fun singleArchiveOffersFullSet() {
        val a = actions(state(allArchives = true))
        listOf(
            Action.COMPRESS, Action.VIEW_ARCHIVE, Action.EXTRACT_HERE, Action.EXTRACT_FOLDER,
            Action.EXTRACT_SUBS, Action.EXTRACT_TO, Action.TEST_ARCHIVE,
        ).forEach { assertTrue("$it", it in a) }
    }

    @Test
    fun severalArchivesOfferOnlyBatchExtract() {
        val a = actions(state(targetCount = 3, selecting = true, allArchives = true))
        assertTrue(Action.EXTRACT_HERE in a && Action.EXTRACT_TO in a && Action.COMPRESS in a)
        assertFalse(Action.VIEW_ARCHIVE in a || Action.TEST_ARCHIVE in a || Action.EXTRACT_FOLDER in a || Action.EXTRACT_SUBS in a)
    }

    @Test
    fun selectingMixedItemsOffersCompressOnly() {
        val a = actions(state(targetCount = 2, selecting = true, allArchives = false))
        assertTrue(Action.COMPRESS in a)
        assertFalse(Action.EXTRACT_HERE in a || Action.EXTRACT_TO in a)
    }

    @Test
    fun outsideManagedRootHidesArchiveItems() {
        val a = actions(state(archiveAvailable = false, allArchives = true))
        assertFalse(Action.COMPRESS in a || Action.EXTRACT_HERE in a || Action.VIEW_ARCHIVE in a)
    }

    @Test
    fun headerMenuWithoutTargetHasNoArchiveItems() {
        val a = actions(state(targetCount = 0, global = true, allArchives = false))
        assertFalse(Action.COMPRESS in a)
    }

    @Test
    fun extractPasteFollowsClipAndDestination() {
        assertFalse(Action.EXTRACT_PASTE in actions(state(targetCount = 0, global = true)))
        val on = EmbeddedMenu.build(state(targetCount = 0, global = true, archiveClipCount = 2)).single { it.action == Action.EXTRACT_PASTE }
        assertEquals(R.string.menu_extract_paste, on.labelRes)
        assertEquals(listOf<Any>(2), on.labelArgs)
        assertTrue(on.enabled)
        val off = EmbeddedMenu.build(state(targetCount = 0, global = true, archiveClipCount = 2, pasteAvailable = false))
            .single { it.action == Action.EXTRACT_PASTE }
        assertFalse(off.enabled)
    }

    @Test
    fun extractPasteHiddenWhileSelecting() {
        assertFalse(Action.EXTRACT_PASTE in actions(state(targetCount = 2, selecting = true, archiveClipCount = 1)))
    }
}
