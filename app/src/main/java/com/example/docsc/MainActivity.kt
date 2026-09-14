package com.example.docsc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.docsc.ui.AppScreen
import com.example.docsc.ui.DocScViewModel
import com.example.docsc.ui.screens.CameraScanScreen
import com.example.docsc.ui.screens.DnsInspectorScreen
import com.example.docsc.ui.screens.DocumentDetailScreen
import com.example.docsc.ui.screens.HomeScreen
import com.example.docsc.ui.screens.ScanEditorScreen
import com.example.docsc.ui.screens.VaultScreen
import com.example.docsc.ui.theme.DocSCTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DocScViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DocSCTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DocScApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun DocScApp(viewModel: DocScViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        viewModel.navigateBack()
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            AppScreen.HOME -> {
                HomeScreen(viewModel = viewModel)
            }
            AppScreen.CAMERA_SCAN -> {
                CameraScanScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
            AppScreen.SCAN_EDITOR -> {
                ScanEditorScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
            AppScreen.DOCUMENT_DETAIL -> {
                DocumentDetailScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
            AppScreen.VAULT -> {
                VaultScreen(viewModel = viewModel)
            }
            AppScreen.DNS_INSPECTOR -> {
                DnsInspectorScreen(viewModel = viewModel)
            }
        }
    }
}
