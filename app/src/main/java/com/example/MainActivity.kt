package com.example

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AiShortGenerator
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.repository.ShortProjectRepository
import com.example.media.ShortVideoExporter
import com.example.media.VoiceoverEngine
import com.example.security.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ProjectsLibraryScreen
import com.example.ui.screens.SecurityShieldScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.ClipCraftTheme
import com.example.ui.theme.ElectricCoral
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

enum class StudioTab {
    STUDIO,
    LIBRARY,
    SHIELD
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Render Compose UI gated behind Google Sign-In
        setContent {
            ClipCraftTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClipCraftRoot()
                }
            }
        }
    }
}

@Composable
fun ClipCraftRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    LaunchedEffect(user?.uid) {
        if (user != null) {
            try {
                user.getIdToken(false).await()
            } catch (_: FirebaseAuthInvalidUserException) {
                auth.signOut()
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                auth.signOut()
            } catch (_: Exception) {
                // Keep persisted session on transient offline network state
            }
        }
    }

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            ClipCraftMainWorkspace(
                currentUserId = user.uid,
                userEmail = user.email
            )
        }
    }
}

@Composable
fun ClipCraftMainWorkspace(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(
                    repository = ShortProjectRepository(db),
                    aiGenerator = AiShortGenerator(),
                    voiceoverEngine = VoiceoverEngine(app),
                    videoExporter = ShortVideoExporter(app),
                    currentUserId = currentUserId,
                    appContext = app
                )
            }
        }
    )

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val draft by viewModel.activeDraft.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val attachedPhotos by viewModel.attachedPhotos.collectAsStateWithLifecycle()
    val includeAiSceneVisuals by viewModel.includeAiSceneVisuals.collectAsStateWithLifecycle()
    val voiceoverStatus by viewModel.voiceoverStatus.collectAsStateWithLifecycle()
    val screenShieldEnabled by viewModel.screenShieldEnabled.collectAsStateWithLifecycle()
    val threatsNeutralized by viewModel.threatsNeutralized.collectAsStateWithLifecycle()
    val securityReport by viewModel.securityReport.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(StudioTab.STUDIO) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Apply real FLAG_SECURE protection to Activity window
    DisposableEffect(screenShieldEnabled) {
        val activity = context as? Activity
        if (screenShieldEnabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    if (currentTab != StudioTab.STUDIO) {
        BackHandler {
            currentTab = StudioTab.STUDIO
        }
    }

    val studioLabel = stringResource(R.string.nav_studio)
    val libraryLabel = stringResource(R.string.nav_library)
    val shieldLabel = stringResource(R.string.nav_security)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentTab == StudioTab.STUDIO,
                            onClick = { currentTab = StudioTab.STUDIO },
                            icon = {
                                Icon(
                                    if (currentTab == StudioTab.STUDIO) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                    contentDescription = studioLabel
                                )
                            },
                            label = { Text(studioLabel) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCoral,
                                selectedTextColor = ElectricCoral,
                                indicatorColor = ElectricCoral.copy(alpha = 0.16f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == StudioTab.LIBRARY,
                            onClick = { currentTab = StudioTab.LIBRARY },
                            icon = {
                                Icon(
                                    if (currentTab == StudioTab.LIBRARY) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = libraryLabel
                                )
                            },
                            label = { Text(libraryLabel) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCoral,
                                selectedTextColor = ElectricCoral,
                                indicatorColor = ElectricCoral.copy(alpha = 0.16f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == StudioTab.SHIELD,
                            onClick = {
                                viewModel.refreshSecurityAudit()
                                currentTab = StudioTab.SHIELD
                            },
                            icon = {
                                Icon(
                                    if (currentTab == StudioTab.SHIELD) Icons.Filled.Security else Icons.Outlined.Security,
                                    contentDescription = shieldLabel
                                )
                            },
                            label = { Text(shieldLabel) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCoral,
                                selectedTextColor = ElectricCoral,
                                indicatorColor = ElectricCoral.copy(alpha = 0.16f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationRailItem(
                            selected = currentTab == StudioTab.STUDIO,
                            onClick = { currentTab = StudioTab.STUDIO },
                            icon = {
                                Icon(
                                    if (currentTab == StudioTab.STUDIO) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                    contentDescription = studioLabel
                                )
                            },
                            label = { Text(studioLabel) }
                        )
                        NavigationRailItem(
                            selected = currentTab == StudioTab.LIBRARY,
                            onClick = { currentTab = StudioTab.LIBRARY },
                            icon = {
                                Icon(
                                    if (currentTab == StudioTab.LIBRARY) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = libraryLabel
                                )
                            },
                            label = { Text(libraryLabel) }
                        )
                        NavigationRailItem(
                            selected = currentTab == StudioTab.SHIELD,
                            onClick = {
                                viewModel.refreshSecurityAudit()
                                currentTab = StudioTab.SHIELD
                            },
                            icon = {
                                Icon(
                                    if (currentTab == StudioTab.SHIELD) Icons.Filled.Security else Icons.Outlined.Security,
                                    contentDescription = shieldLabel
                                )
                            },
                            label = { Text(shieldLabel) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        StudioTab.STUDIO -> StudioScreen(
                            draft = draft,
                            generationState = generationState,
                            exportState = exportState,
                            playbackState = playbackState,
                            attachedPhotos = attachedPhotos,
                            includeAiSceneVisuals = includeAiSceneVisuals,
                            voiceoverStatus = voiceoverStatus,
                            threatsNeutralized = threatsNeutralized,
                            onTopicChange = viewModel::updateTopic,
                            onTitleChange = viewModel::updateTitle,
                            onStyleChange = viewModel::updateStyle,
                            onDurationChange = viewModel::updateDuration,
                            onVoiceProfileChange = viewModel::updateVoiceProfile,
                            onCaptionStyleChange = viewModel::updateCaptionStyle,
                            onBackdropThemeChange = viewModel::updateBackdropTheme,
                            onScriptChange = viewModel::updateScript,
                            onSceneCaptionChange = viewModel::updateSceneCaption,
                            onPhotosPicked = viewModel::setAttachedPhotos,
                            onClearPhotos = viewModel::clearAttachedPhotos,
                            onToggleAiSceneVisuals = viewModel::setIncludeAiSceneVisuals,
                            onGenerateClick = viewModel::generateAiShort,
                            onGenerateVideoClipClick = viewModel::generateFull20sVideoClip,
                            onAutoCaptionScript = viewModel::autoCaptionEditedScript,
                            onResynthesizeVoice = viewModel::resynthesizeVoiceover,
                            onTogglePlay = viewModel::togglePlayPreview,
                            onSeekPreview = viewModel::seekPreview,
                            onSaveDraft = viewModel::saveCurrentDraft,
                            onExportMp4 = viewModel::exportVideoMp4,
                            onSaveToGallery = viewModel::saveExportedToGallery,
                            onNewBlankShort = viewModel::createBlankShort
                        )
                        StudioTab.LIBRARY -> ProjectsLibraryScreen(
                            state = projectsState,
                            onSelectProject = { project ->
                                viewModel.loadProject(project)
                                currentTab = StudioTab.STUDIO
                            },
                            onDeleteProject = viewModel::deleteProject,
                            onCreateNew = {
                                viewModel.createBlankShort()
                                currentTab = StudioTab.STUDIO
                            }
                        )
                        StudioTab.SHIELD -> SecurityShieldScreen(
                            report = securityReport,
                            userEmail = userEmail,
                            userId = currentUserId,
                            threatsNeutralized = threatsNeutralized,
                            onToggleScreenShield = viewModel::toggleScreenShield,
                            onRefreshAudit = viewModel::refreshSecurityAudit,
                            onSignOut = {
                                viewModel.stopPlayback()
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
