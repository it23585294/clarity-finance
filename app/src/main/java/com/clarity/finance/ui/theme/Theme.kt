package com.clarity.finance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ClarityDesignTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)
private val ClarityDesignShapes = Shapes(
    small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp)
)

/** The single system-following application theme. Wallpaper colours do not replace approved semantic roles. */
@Composable
fun ClarityTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = clarityColorScheme(darkTheme)
    MaterialTheme(colorScheme = colors, typography = ClarityDesignTypography, shapes = ClarityDesignShapes) {
        Surface(color = colors.background, contentColor = colors.onBackground, content = content)
    }
}

internal fun clarityColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme(
        primary = Color(0xFF96DCC4), onPrimary = Color(0xFF0D382B), primaryContainer = Color(0xFF234237), onPrimaryContainer = Color(0xFFECF4F0),
        background = Color(0xFF101B19), onBackground = Color(0xFFECF4F0), surface = Color(0xFF192724), onSurface = Color(0xFFECF4F0),
        secondary = Color(0xFF96DCC4), secondaryContainer = Color(0xFF234237), onSecondaryContainer = Color(0xFFECF4F0),
        surfaceTint = Color(0xFF96DCC4), outlineVariant = Color(0xFF7B9587),
        onSurfaceVariant = Color(0xFFB2C5BD), outline = Color(0xFF7B9587), error = Color(0xFFFFB4AB)
    ) else lightColorScheme(
        primary = Color(0xFF237D68), onPrimary = Color.White, primaryContainer = Color(0xFFEEF9F3), onPrimaryContainer = Color(0xFF182B29),
        background = Color(0xFFF4F7F6), onBackground = Color(0xFF182B29), surface = Color.White, onSurface = Color(0xFF182B29),
        secondary = Color(0xFF237D68), secondaryContainer = Color(0xFFEEF9F3), onSecondaryContainer = Color(0xFF182B29),
        surfaceTint = Color(0xFF237D68), outlineVariant = Color(0xFF748B7F),
        onSurfaceVariant = Color(0xFF506560), outline = Color(0xFF748B7F), error = Color(0xFFA83732)
    )
    // Fill Material surface/accent roles too: dialogs, menus and unconfigured cards must
    // not inherit lavender defaults. Reuse approved colours; keep existing error roles.
    return base.copy(
        onSecondary = base.onPrimary, tertiary = base.primary, onTertiary = base.onPrimary,
        tertiaryContainer = base.primaryContainer, onTertiaryContainer = base.onPrimaryContainer,
        surfaceVariant = base.secondaryContainer,
        surfaceDim = base.background, surfaceBright = base.surface,
        surfaceContainerLowest = base.surface, surfaceContainerLow = base.background,
        surfaceContainer = base.secondaryContainer, surfaceContainerHigh = base.secondaryContainer,
        surfaceContainerHighest = base.secondaryContainer,
        inverseSurface = if (darkTheme) Color(0xFFF4F7F6) else Color(0xFF192724),
        inverseOnSurface = if (darkTheme) Color(0xFF182B29) else Color(0xFFECF4F0),
        inversePrimary = if (darkTheme) Color(0xFF237D68) else Color(0xFF96DCC4)
    )
}
