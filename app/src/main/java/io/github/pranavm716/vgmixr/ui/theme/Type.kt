package io.github.pranavm716.vgmixr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.pranavm716.vgmixr.R

// Shuttle's type: Open Sans SemiBold for titles, tabs and labels; the system Roboto for screen
// headers and body text.
// Open Sans is one variable font (SIL OFL, docs/licenses/OpenSans-OFL.txt), so each weight is a
// variation of the same file.
@OptIn(ExperimentalTextApi::class)
private val OpenSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.SemiBold).map { weight ->
        Font(
            resId = R.font.open_sans,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

private val base = Typography()

private fun TextStyle.openSans() = copy(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold)

val Typography = base.copy(
    titleLarge = base.titleLarge.openSans(),
    titleMedium = base.titleMedium.openSans(),
    titleSmall = base.titleSmall.openSans(),
    labelLarge = base.labelLarge.openSans(),
    labelMedium = base.labelMedium.openSans(),
    labelSmall = base.labelSmall.openSans()
)
