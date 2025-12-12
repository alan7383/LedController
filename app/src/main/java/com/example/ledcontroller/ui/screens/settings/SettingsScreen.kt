package com.example.ledcontroller.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import com.example.ledcontroller.BuildConfig
import com.example.ledcontroller.R
import com.example.ledcontroller.viewmodel.MainViewModel
import java.util.Locale

// --- IMPORTS FROM COMMON UI ---
import com.example.ledcontroller.ui.components.SettingsGroupTitle
import com.example.ledcontroller.ui.components.SettingsGroupCard
import com.example.ledcontroller.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    roomName: String,
    onBack: () -> Unit,
    onRoomNameChange: (String) -> Unit
) {
    val context = LocalContext.current
    // M3 Scroll Behavior for Collapsing Toolbar
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 16.dp)
        ) {

            // --- GENERAL SECTION ---
            item {
                SettingsGroupTitle(stringResource(R.string.settings_category_general))
                SettingsGroupCard {
                    SettingsItem(
                        title = stringResource(R.string.room_name_label),
                        subtitle = roomName,
                        icon = Icons.Rounded.Edit,
                        onClick = { showRenameDialog = true }
                    )
                }
            }

            // --- APPEARANCE SECTION ---
            item {
                SettingsGroupTitle(stringResource(R.string.settings_category_appearance))
                SettingsGroupCard {
                    SettingsItem(
                        title = stringResource(R.string.theme_title),
                        subtitle = when(viewModel.themeMode) {
                            1 -> stringResource(R.string.theme_light)
                            2 -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.system_default)
                        },
                        icon = Icons.Rounded.BrightnessMedium,
                        onClick = { showThemeDialog = true }
                    )

                    // Native M3 ListItem Switch
                    SettingsItem(
                        title = stringResource(R.string.amoled_mode),
                        subtitle = stringResource(R.string.amoled_desc),
                        icon = Icons.Rounded.DarkMode,
                        hasSwitch = true,
                        switchState = viewModel.isAmoledMode,
                        onSwitchChange = { viewModel.toggleAmoled(it, context) }
                    )

                    SettingsItem(
                        title = stringResource(R.string.language),
                        subtitle = getCurrentLanguageName(),
                        icon = Icons.Rounded.Language,
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            // --- ABOUT SECTION ---
            item {
                SettingsGroupTitle(stringResource(R.string.settings_category_about))
                SettingsGroupCard {
                    SettingsItem(
                        title = stringResource(R.string.app_name),
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                        icon = Icons.Rounded.Info,
                        onClick = {
                            versionClickCount++
                            if (versionClickCount >= 7) {
                                versionClickCount = 0
                                showEasterEggDialog = true
                            }
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.dev_passion),
                        subtitle = stringResource(R.string.github_link),
                        icon = Icons.Rounded.Code,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alan7383"))
                            context.startActivity(intent)
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.licenses),
                        subtitle = stringResource(R.string.licenses_desc),
                        icon = Icons.Rounded.Description,
                        onClick = { showLicensesDialog = true }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showRenameDialog) RenameDialog(currentName = roomName, onDismiss = { showRenameDialog = false }) { newName -> onRoomNameChange(newName); showRenameDialog = false }
    if (showLanguageDialog) LanguageSelectionDialog { showLanguageDialog = false }
    if (showThemeDialog) ThemeSelectionDialog(viewModel.themeMode, { showThemeDialog = false }) { viewModel.updateTheme(it, context); showThemeDialog = false }
    if (showEasterEggDialog) EasterEggDialog(onDismiss = { showEasterEggDialog = false })
    if (showLicensesDialog) LicensesScreen(onDismiss = { showLicensesDialog = false })
}

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.room_name_label)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { Button(onClick = { if(text.isNotBlank()) onConfirm(text) }) { Text(stringResource(R.string.finish_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun LanguageSelectionDialog(onDismiss: () -> Unit) {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "system" else currentLocales.get(0)?.language ?: "en"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                LanguageOption(text = stringResource(R.string.system_default), selected = currentTag == "system") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()); onDismiss() }
                LanguageOption(text = "English", selected = currentTag == "en") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")); onDismiss() }
                LanguageOption(text = "Français", selected = currentTag == "fr") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr")); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ThemeSelectionDialog(currentMode: Int, onDismiss: () -> Unit, onThemeSelected: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_title)) },
        text = {
            Column {
                LanguageOption(text = stringResource(R.string.system_default), selected = currentMode == 0) { onThemeSelected(0) }
                LanguageOption(text = stringResource(R.string.theme_light), selected = currentMode == 1) { onThemeSelected(1) }
                LanguageOption(text = stringResource(R.string.theme_dark), selected = currentMode == 2) { onThemeSelected(2) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun EasterEggDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.dev_passion), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.dev_desc), textAlign = TextAlign.Center) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun LanguageOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

data class OpenSourceLibrary(val name: String, val author: String, val license: String = "Apache 2.0", val url: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val libraries = listOf(
        OpenSourceLibrary("Kotlin", "JetBrains", url = "https://kotlinlang.org/"),
        OpenSourceLibrary("AndroidX Core & AppCompat", "Google", url = "https://developer.android.com/jetpack/androidx"),
        OpenSourceLibrary("Jetpack Compose", "Google", url = "https://developer.android.com/jetpack/compose"),
        OpenSourceLibrary("Material Design 3", "Google", url = "https://m3.material.io/"),
        OpenSourceLibrary("Kotlin Coroutines", "JetBrains", url = "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceLibrary("Gson", "Google", url = "https://github.com/google/gson")
    )

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.licenses_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(libraries) { lib ->
                    LicenseItem(lib) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))) }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun LicenseItem(library: OpenSourceLibrary, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = library.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.author, library.author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(text = library.license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(imageVector = Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

fun getCurrentLanguageName(): String {
    val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
    return locale.displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}