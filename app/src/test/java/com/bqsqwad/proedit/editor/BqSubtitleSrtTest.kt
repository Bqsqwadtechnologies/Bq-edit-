package com.bqsqwad.proedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BqSubtitleSrtTest {
    @Test fun roundTrip() {
        val input=listOf(BqCaption("1","Hello",1000,2500),BqCaption("2","Second\nline",3000,5000))
        val restored=BqSubtitleSrt.import(BqSubtitleSrt.export(input))
        assertEquals(2,restored.size)
        assertEquals("Hello",restored[0].text)
        assertEquals(1000,restored[0].startMs)
        assertEquals(5000,restored[1].endMs)
    }
}