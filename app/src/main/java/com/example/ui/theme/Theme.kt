package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ForestPrimaryDark,
    onPrimary = OnForestPrimaryDark,
    primaryContainer = ForestPrimaryContainerDark,
    onPrimaryContainer = OnForestPrimaryContainerDark,
    secondary = TerracottaSecondaryDark,
    onSecondary = OnTerracottaSecondaryDark,
    secondaryContainer = TerracottaSecondaryContainerDark,
    onSecondaryContainer = OnTerracottaSecondaryContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberTertiaryContainerDark,
    onTertiaryContainer = OnAmberTertiaryContainerDark,
    background = DeepForestBackgroundDark,
    onBackground = OnDeepForestBackgroundDark,
    surface = DeepForestSurfaceDark,
    onSurface = OnDeepForestSurfaceDark,
    surfaceVariant = SageSurfaceVariantDark,
    onSurfaceVariant = OnSageSurfaceVariantDark,
    outline = OutlineSageDark
)

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimaryLight,
    onPrimary = OnForestPrimaryLight,
    primaryContainer = ForestPrimaryContainerLight,
    onPrimaryContainer = OnForestPrimaryContainerLight,
    secondary = TerracottaSecondaryLight,
    onSecondary = OnTerracottaSecondaryLight,
    secondaryContainer = TerracottaSecondaryContainerLight,
    onSecondaryContainer = OnTerracottaSecondaryContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = OnAmberTertiaryLight,
    tertiaryContainer = AmberTertiaryContainerLight,
    onTertiaryContainer = OnAmberTertiaryContainerLight,
    background = CreamBackgroundLight,
    onBackground = OnCreamBackgroundLight,
    surface = WarmSurfaceLight,
    onSurface = OnWarmSurfaceLight,
    surfaceVariant = SageSurfaceVariantLight,
    onSurfaceVariant = OnSageSurfaceVariantLight,
    outline = OutlineSageLight
)

val PantryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PantryPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PantryShapes,
        content = content
    )
}
