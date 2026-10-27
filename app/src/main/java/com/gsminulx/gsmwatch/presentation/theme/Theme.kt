package com.gsminulx.gsmwatch.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

// GSM 브랜드 팔레트 (틸/시안). Wear OS 6+ 에서 워치페이스 동적 색상을 쓸 수 없을 때의 폴백.
// Material 3 색 역할 기준, OLED 배경은 순수 검정(배터리 절약)을 유지한다.
val GsmBrandColorScheme = ColorScheme(
    primary = Color(0xFF4CDADA),
    primaryDim = Color(0xFF38BDBD),
    primaryContainer = Color(0xFF004F50),
    onPrimary = Color(0xFF003737),
    onPrimaryContainer = Color(0xFF9CF1F0),
    secondary = Color(0xFFB0CCCA),
    secondaryDim = Color(0xFF95B0AE),
    secondaryContainer = Color(0xFF324B4B),
    onSecondary = Color(0xFF1B3534),
    onSecondaryContainer = Color(0xFFCCE8E6),
    tertiary = Color(0xFFADC6EC),
    tertiaryDim = Color(0xFF92AAD0),
    tertiaryContainer = Color(0xFF2E4665),
    onTertiary = Color(0xFF15304F),
    onTertiaryContainer = Color(0xFFD5E3FF),
    surfaceContainerLow = Color(0xFF141B1A),
    surfaceContainer = Color(0xFF1B2322),
    surfaceContainerHigh = Color(0xFF252D2C),
    onSurface = Color(0xFFDDE4E2),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4948),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFE5978E),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * 워치 테마. Wear OS 6+ 는 워치페이스에서 뽑은 동적 색상을 따르고(Material 3 Expressive 권장),
 * 지원하지 않는 기기에서는 GSM 브랜드 팔레트를 쓴다. 타이포/모양/모션은 Wear M3 기본값(Expressive).
 */
@Composable
fun GsmWatchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) { dynamicColorScheme(context) ?: GsmBrandColorScheme }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
