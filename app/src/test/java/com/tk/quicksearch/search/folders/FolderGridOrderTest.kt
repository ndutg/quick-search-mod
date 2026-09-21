package com.tk.quicksearch.search.folders

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderGridOrderTest {

    @Test
    fun creatingFolderTakesTargetPositionAndDropsDraggedTile() {
        assertEquals(
            listOf("a", "folder:f", "c"),
            orderAfterCreatingFolder(listOf("a", "b", "c", "d"), "b", "d", "folder:f"),
        )
    }

    @Test
    fun removingFromTwoMemberFolderRestoresBothMembers() {
        assertEquals(
            listOf("x", "m"),
            membersRestoredAfterRemoval(listOf("x", "m"), "m", repinRemovedMember = true),
        )
    }

    @Test
    fun unpinningFromTwoMemberFolderRestoresOnlyRemainingMember() {
        assertEquals(
            listOf("x"),
            membersRestoredAfterRemoval(listOf("x", "m"), "m", repinRemovedMember = false),
        )
    }

    @Test
    fun removingFromThreeMemberFolderKeepsFolder() {
        assertEquals(
            null,
            membersRestoredAfterRemoval(listOf("x", "m", "y"), "m", repinRemovedMember = true),
        )
    }

    @Test
    fun removedMemberGoesRightAfterFolder() {
        assertEquals(
            listOf("a", "folder:f", "m", "c"),
            orderAfterRemovingMember(listOf("a", "folder:f", "c"), "folder:f", "m", folderRemoved = false),
        )
    }

    @Test
    fun dissolvedFolderMembersTakeFolderPosition() {
        assertEquals(
            listOf("a", "x", "m", "c"),
            orderAfterDeletingFolder(
                listOf("a", "folder:f", "c"),
                "folder:f",
                listOf("x", "m"),
            ),
        )
    }

    @Test
    fun dissolvingAfterUnpinRestoresOnlyRemainingMember() {
        assertEquals(
            listOf("a", "x", "c"),
            orderAfterDeletingFolder(listOf("a", "folder:f", "c"), "folder:f", listOf("x")),
        )
    }

    @Test
    fun deletedFolderMembersTakeItsPositionInFolderOrder() {
        assertEquals(
            listOf("a", "x", "y", "c"),
            orderAfterDeletingFolder(listOf("a", "folder:f", "c"), "folder:f", listOf("x", "y")),
        )
    }

    @Test
    fun reorderedMembersIgnoreStaleKeysAndKeepUnlistedAtEnd() {
        assertEquals(
            listOf("c", "a", "b"),
            reorderedFolderMembers(listOf("a", "b", "c"), listOf("c", "gone", "a")),
        )
    }
}
