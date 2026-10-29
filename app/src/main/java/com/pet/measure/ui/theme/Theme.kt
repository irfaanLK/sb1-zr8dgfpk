package com.pet.measure.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pet.measure.data.Team
import com.pet.measure.overlay.PetColors

/** A warm Provence evening: lavender, ochre and olive on a dark, sun-readable background. */
object Pet {
    val Night = Color(0xFF15130F)
    val Stone = Color(0xFF1F1C17)
    val StoneHigh = Color(0xFF2A2620)
    val Lavender = Color(0xFFB8A4F0)
    val Ochre = Color(0xFFE3A857)
    val Olive = Color(0xFFA3B76E)
    val Cream = Color(0xFFF3EEE6)
    val Muted = Color(0xFFC9C1B4)
    val Gold = Color(PetColors.CLOSEST)
    val Glass = Color(0xD915130F)

    fun team(team: Team) = Color(PetColors.team(team))
}

private val colors = darkColorScheme(
    primary = Pet.Lavender,
    onPrimary = Color(0xFF1E1533),
    primaryContainer = Color(0xFF3B3060),
    onPrimaryContainer = Color(0xFFE9DFFF),
    secondary = Pet.Ochre,
    onSecondary = Color(0xFF2A1A05),
    secondaryContainer = Color(0xFF4A3412),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Pet.Olive,
    onTertiary = Color(0xFF1D260A),
    background = Pet.Night,
    onBackground = Pet.Cream,
    surface = Pet.Night,
    onSurface = Pet.Cream,
    surfaceVariant = Pet.StoneHigh,
    onSurfaceVariant = Pet.Muted,
    surfaceContainerLowest = Color(0xFF100E0B),
    surfaceContainerLow = Pet.Stone,
    surfaceContainer = Pet.Stone,
    surfaceContainerHigh = Pet.StoneHigh,
    surfaceContainerHighest = Color(0xFF35302A),
    outline = Color(0xFF5A5248),
    outlineVariant = Color(0xFF3A342D),
    error = Color(0xFFFF8A7A),
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val DistanceStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black)

@Composable
fun PetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
