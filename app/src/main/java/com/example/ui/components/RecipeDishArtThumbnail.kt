package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RecipeCategoryFilter
import com.example.data.RecipeSuggestion
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.FreshEmerald

/**
 * Zero-cost on-device stylized culinary dish preview card rendered with Compose Canvas.
 * Gives every generated recipe an editorial cookbook thumbnail without incurring image API spend.
 */
@Composable
fun RecipeDishArtThumbnail(
    recipe: RecipeSuggestion,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondary
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    val seed = recipe.title.hashCode() and Int.MAX_VALUE
    val (bgStart, bgEnd, accentDishColor) = when (recipe.recipeCategory) {
        RecipeCategoryFilter.QUICK_EASY -> Triple(tertiaryContainer, secondaryContainer, secondary)
        RecipeCategoryFilter.HEALTHY_LOW_CAL -> Triple(primaryContainer, tertiaryContainer, primary)
        RecipeCategoryFilter.BUDGET_FRIENDLY -> Triple(secondaryContainer, primaryContainer, tertiary)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = listOf(bgStart, bgEnd)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val plateCenter = Offset(w * 0.78f, h * 0.56f)
            val plateRadius = h * 0.44f

            // Subtle botanical ambient rings
            drawCircle(
                color = surface.copy(alpha = 0.22f),
                radius = plateRadius * 1.48f,
                center = Offset(w * 0.14f, h * 0.2f)
            )
            drawCircle(
                color = accentDishColor.copy(alpha = 0.14f),
                radius = plateRadius * 1.15f,
                center = plateCenter
            )

            // Ceramic stoneware plate + inner rim
            drawCircle(
                color = surface.copy(alpha = 0.94f),
                radius = plateRadius,
                center = plateCenter
            )
            drawCircle(
                color = accentDishColor.copy(alpha = 0.24f),
                radius = plateRadius * 0.78f,
                center = plateCenter,
                style = Stroke(width = 2.5f)
            )

            // Layered culinary garnish & roasted ingredient accents deterministic per dish
            val dotOffsets = listOf(
                Offset(-0.34f, -0.18f),
                Offset(0.28f, -0.22f),
                Offset(-0.08f, 0.24f),
                Offset(0.32f, 0.18f),
                Offset(-0.30f, 0.22f),
                Offset(0.05f, -0.04f)
            )
            val palette = listOf(FreshEmerald, ExpiringAmber, secondary, primary, tertiary)
            dotOffsets.forEachIndexed { idx, rel ->
                val radiusFactor = 0.13f + ((seed + idx * 7) % 5) * 0.02f
                drawCircle(
                    color = palette[(seed + idx) % palette.size].copy(alpha = 0.85f),
                    radius = plateRadius * radiusFactor,
                    center = Offset(
                        plateCenter.x + rel.x * plateRadius,
                        plateCenter.y + rel.y * plateRadius
                    )
                )
            }

            // Delicate rising steam arcs above the dish
            for (i in 0..2) {
                val startX = plateCenter.x - plateRadius * 0.35f + i * (plateRadius * 0.35f)
                val steamPath = Path().apply {
                    moveTo(startX, plateCenter.y - plateRadius * 0.88f)
                    quadraticTo(
                        startX + (if (i % 2 == 0) 9f else -9f),
                        plateCenter.y - plateRadius * 1.10f,
                        startX,
                        plateCenter.y - plateRadius * 1.28f
                    )
                }
                drawPath(
                    path = steamPath,
                    color = surface.copy(alpha = 0.72f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            }
        }

        // Top-left metadata overlay chips
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = surface.copy(alpha = 0.90f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = onSurface,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.recipe_prep_mins_fmt, recipe.prepTimeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface
                    )
                }
            }

            Surface(
                color = surface.copy(alpha = 0.90f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = FreshEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(id = R.string.recipe_dish_art_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = onSurface
                    )
                }
            }
        }
    }
}
