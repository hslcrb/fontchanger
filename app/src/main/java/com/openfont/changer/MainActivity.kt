package com.openfont.changer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.openfont.changer.data.model.FontMetadata
import com.openfont.changer.data.model.PresetFont
import com.openfont.changer.engine.FontParser
import com.openfont.changer.ui.screens.ApplyMethodScreen
import com.openfont.changer.ui.screens.CustomFontScreen
import com.openfont.changer.ui.screens.HomeScreen
import com.openfont.changer.ui.screens.SettingsScreen
import com.openfont.changer.ui.theme.FontCraftTheme
import java.io.File
import java.io.FileOutputStream

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "추천 폰트", Icons.Default.Home)
    object Custom : Screen("custom", "커스텀", Icons.Default.Edit)
    object Apply : Screen("apply", "적용 및 배포", Icons.Default.RocketLaunch)
    object Settings : Screen("settings", "설정", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private var initialFontMeta by mutableStateOf<FontMetadata?>(null)
    private var initialFontFile by mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            FontCraftTheme {
                MainAppContainer(
                    initialMeta = initialFontMeta,
                    initialFile = initialFontFile
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            uri?.let {
                try {
                    val tempFile = File(cacheDir, "imported_intent_font.ttf")
                    contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    initialFontFile = tempFile
                    initialFontMeta = FontParser.parse(tempFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    initialMeta: FontMetadata?,
    initialFile: File?
) {
    var selectedScreen by remember { mutableStateOf<Screen>(if (initialMeta != null) Screen.Custom else Screen.Home) }
    var currentMetadata by remember { mutableStateOf(initialMeta) }
    var currentFontFile by remember { mutableStateOf(initialFile) }

    val screens = listOf(Screen.Home, Screen.Custom, Screen.Apply, Screen.Settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedScreen.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedScreen) {
            Screen.Home -> HomeScreen(
                onSelectPreset = { preset: PresetFont ->
                    currentMetadata = FontMetadata(
                        familyName = preset.name,
                        fullName = preset.name,
                        styleName = "Regular"
                    )
                    selectedScreen = Screen.Custom
                },
                modifier = Modifier.padding(innerPadding)
            )
            Screen.Custom -> CustomFontScreen(
                currentMetadata = currentMetadata,
                onMetadataChanged = { meta, file ->
                    currentMetadata = meta
                    currentFontFile = file
                },
                onNavigateToApply = {
                    selectedScreen = Screen.Apply
                },
                modifier = Modifier.padding(innerPadding)
            )
            Screen.Apply -> ApplyMethodScreen(
                metadata = currentMetadata,
                fontFile = currentFontFile,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
