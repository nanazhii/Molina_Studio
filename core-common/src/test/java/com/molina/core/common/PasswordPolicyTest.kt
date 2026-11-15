// molina-27q
package com.molina.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test fun terlaluPendekDitolak() {
        assertFalse(PasswordPolicy.evaluate("x").acceptable)
        assertFalse(PasswordPolicy.evaluate("").acceptable)
        assertFalse(PasswordPolicy.evaluate("Ab3!xyz").acceptable) // 7 karakter
    }

    @Test fun umumDanBerpolaDitolak() {
        assertFalse(PasswordPolicy.evaluate("password123").acceptable)
        assertFalse(PasswordPolicy.evaluate("abcdefghij").acceptable)
        assertFalse(PasswordPolicy.evaluate("aaaaaaaaaa").acceptable)
    }

    @Test fun barisBaruDitolak() {
        assertFalse(PasswordPolicy.evaluate("Rahasia-Kuat\n9!").acceptable)
    }

    @Test fun kuatDiterima() {
        val v = PasswordPolicy.evaluate("Rahasia-Kuat-9!")
        assertTrue(v.acceptable)
        assertTrue(v.strength >= PasswordPolicy.Strength.FAIR)
    }

    @Test fun generatorSelaluLolos() {
        repeat(20) { assertTrue(PasswordPolicy.evaluate(PasswordPolicy.generate()).acceptable) }
    }
}
