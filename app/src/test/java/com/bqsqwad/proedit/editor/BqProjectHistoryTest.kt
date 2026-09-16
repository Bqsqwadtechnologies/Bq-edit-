package com.bqsqwad.proedit.editor

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class BqProjectHistoryTest {
    @Test fun commitUndoRedoRestoresProjectState() {
        val history = BqProjectHistory()
        val clip = BqClip("1", mock(Uri::class.java), "clip")
        val next = history.current.copy(clips = listOf(clip), selectedClipId = "1")
        history.commit(next)
        assertEquals(1, history.current.clips.size)
        assertTrue(history.canUndo)
        assertEquals(0, history.undo()!!.clips.size)
        assertTrue(history.canRedo)
        assertEquals(1, history.redo()!!.clips.size)
    }

    @Test fun duplicateCommitIsIgnored() {
        val history = BqProjectHistory()
        val state = history.current
        history.commit(state)
        assertTrue(!history.canUndo)
    }
}
