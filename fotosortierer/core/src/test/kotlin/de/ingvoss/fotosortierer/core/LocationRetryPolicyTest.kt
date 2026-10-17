package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRetryPolicyTest {
    @Test
    fun wenigeFehlschlaegeWerdenAufgegeben() {
        assertTrue(LocationRetryPolicy.giveUp(failed = 14, attempted = 8597))
        assertTrue(LocationRetryPolicy.giveUp(failed = 1, attempted = 10))
    }

    @Test
    fun vieleFehlschlaegeDeutenAufAllgemeinesProblemHin() {
        assertFalse(LocationRetryPolicy.giveUp(failed = 900, attempted = 8597))
        assertFalse(LocationRetryPolicy.giveUp(failed = 14, attempted = 14))
        assertFalse(LocationRetryPolicy.giveUp(failed = 2, attempted = 10))
    }

    @Test
    fun ohneFehlschlaegeIstNichtsZuTun() {
        assertFalse(LocationRetryPolicy.giveUp(failed = 0, attempted = 100))
        assertFalse(LocationRetryPolicy.giveUp(failed = 0, attempted = 0))
    }
}
