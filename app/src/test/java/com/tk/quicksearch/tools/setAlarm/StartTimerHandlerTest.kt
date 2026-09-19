package com.tk.quicksearch.tools.setAlarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartTimerHandlerTest {

    @Test
    fun `detects minute durations regardless of spacing and suffix`() {
        assertEquals(600, StartTimerHandler.detectTimerSeconds("10min"))
        assertEquals(600, StartTimerHandler.detectTimerSeconds("10 min"))
        assertEquals(600, StartTimerHandler.detectTimerSeconds("10 minutes"))
        assertEquals(600, StartTimerHandler.detectTimerSeconds("10   mins"))
        assertEquals(60, StartTimerHandler.detectTimerSeconds("1 MINUTE"))
    }

    @Test
    fun `detects hour durations regardless of spacing and suffix`() {
        assertEquals(36000, StartTimerHandler.detectTimerSeconds("10h"))
        assertEquals(36000, StartTimerHandler.detectTimerSeconds("10 hrs"))
        assertEquals(36000, StartTimerHandler.detectTimerSeconds("10 hours"))
        assertEquals(3600, StartTimerHandler.detectTimerSeconds(" 1 hr "))
    }

    @Test
    fun `ignores queries that are not standalone durations`() {
        assertNull(StartTimerHandler.detectTimerSeconds("10"))
        assertNull(StartTimerHandler.detectTimerSeconds("0 min"))
        assertNull(StartTimerHandler.detectTimerSeconds("25 hours"))
        assertNull(StartTimerHandler.detectTimerSeconds("timer 10 min"))
        assertNull(StartTimerHandler.detectTimerSeconds("10 minutes from now"))
        // The unit converter already reads a bare "m" as meters.
        assertNull(StartTimerHandler.detectTimerSeconds("10 m"))
    }

    @Test
    fun `does not overlap with alarm time detection`() {
        assertNull(StartTimerHandler.detectTimerSeconds("10am"))
        assertNull(StartTimerHandler.detectTimerSeconds("10:30"))
        assertNull(SetAlarmHandler.detectAlarmTime("10 min"))
        assertNull(SetAlarmHandler.detectAlarmTime("10h"))
    }
}
