package com.clarity.finance.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.clarity.finance.ui.theme.clarityColorScheme
import org.junit.Assert.*
import org.junit.Test

class ClarityThemeTest {
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun approvedAccentsRemainStableInBothModes() {
        val light = clarityColorScheme(false); val dark = clarityColorScheme(true)
        assertEquals(Color(0xFF237D68), light.primary); assertEquals(Color(0xFFEEF9F3), light.primaryContainer)
        assertEquals(Color(0xFF96DCC4), dark.primary); assertEquals(Color(0xFF234237), dark.primaryContainer)
    }
    @Test fun normalTextIsReadableOnEverySharedSurfaceAndActionRole() {
        listOf(false, true).forEach { dark ->
            val c = clarityColorScheme(dark)
            val pairs = listOf(c.onPrimary to c.primary, c.onSecondary to c.secondary, c.onTertiary to c.tertiary,
                c.onPrimaryContainer to c.primaryContainer, c.primary to c.primaryContainer,
                c.onSurface to c.background, c.onSurfaceVariant to c.surfaceVariant,
                c.inverseOnSurface to c.inverseSurface, c.inversePrimary to c.inverseSurface) +
                listOf(c.surface, c.surfaceDim, c.surfaceBright, c.surfaceContainerLowest, c.surfaceContainerLow,
                    c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest).map { c.onSurface to it }
            pairs.forEach { (fg, bg) -> assertTrue("Text contrast in dark=$dark: ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5) }
        }
    }
    @Test fun controlsHaveVisibleOutlinesAndNoDefaultLavenderSurfaces() {
        listOf(false, true).forEach { dark ->
            val c = clarityColorScheme(dark)
            assertTrue(contrast(c.outline, c.background) >= 3)
            assertTrue(contrast(c.outline, c.surface) >= 3)
            val approvedSurfaces = setOf(c.surface, c.background, c.primaryContainer)
            listOf(c.surfaceDim, c.surfaceBright, c.surfaceVariant, c.surfaceContainerLowest,
                c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)
                .forEach { assertTrue("Surface must belong to the approved palette", it in approvedSurfaces) }
            assertNotEquals(c.error, c.primary)
        }
    }
}
