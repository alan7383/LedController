package com.example.ledcontroller.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- Définition des couleurs de base (si tu ne les as pas dans Color.kt) ---
// Si elles sont déjà dans Color.kt, ces lignes sont optionnelles ici,
// mais ça ne fait pas de mal pour la compilation.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun AppTheme(
    themeMode: Int,      // 0: Auto, 1: Light, 2: Dark
    useAmoled: Boolean,  // Vrai si on veut le fond noir pur
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 1. Déterminer si on est en mode sombre
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        1 -> false       // Force Light
        2 -> true        // Force Dark
        else -> systemDark // Suivre le système
    }

    // 2. Choisir les couleurs (Dynamic Color ou couleurs fixes)
    var colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 3. Appliquer le mode AMOLED (Noir pur) si nécessaire
    if (useAmoled && darkTheme) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black, // Important pour M3
            surfaceVariant = Color(0xFF121212), // Légèrement plus clair pour les cartes
            onBackground = Color.White,
            onSurface = Color.White
        )
    }

    // 4. Gérer la couleur de la barre de statut (Status Bar)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // La barre de statut devient transparente (géré par EdgeToEdge dans le Main)
            // Mais on doit dire aux icônes d'être claires ou sombres
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Référence à ta variable dans Type.kt
        content = content
    )
}