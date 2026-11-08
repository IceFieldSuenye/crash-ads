package hey.skipad.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hey.skipad.R
import hey.skipad.ui.screens.AboutScreen
import hey.skipad.ui.screens.LogsScreen
import hey.skipad.ui.screens.OverviewScreen
import hey.skipad.ui.screens.RulesScreen
import kotlinx.coroutines.delay

private const val TAB_OVERVIEW = 0
private const val TAB_RULES = 1
private const val TAB_LOGS = 2
private const val TAB_ABOUT = 3

@Composable
fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_OVERVIEW) }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val running by vm.serviceRunning.collectAsStateWithLifecycle()
    val serviceEnabled by vm.serviceEnabled.collectAsStateWithLifecycle()
    val batteryIgnored by vm.batteryIgnored.collectAsStateWithLifecycle()
    val overlayGranted by vm.overlayGranted.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    // Reinstalling the app unbinds the accessibility service; keep the
    // overview status live instead of waiting for the next ON_RESUME.
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshServiceState()
            delay(1500L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = tab == TAB_OVERVIEW,
                    onClick = { tab = TAB_OVERVIEW },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_overview)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_RULES,
                    onClick = { tab = TAB_RULES },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_rules)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_LOGS,
                    onClick = { tab = TAB_LOGS },
                    icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_logs)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_ABOUT,
                    onClick = { tab = TAB_ABOUT },
                    icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_about)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "tab-content",
            ) { index ->
                when (index) {
                    TAB_OVERVIEW -> OverviewScreen(
                        stats = stats,
                        logs = logs,
                        running = running,
                        serviceEnabled = serviceEnabled,
                        batteryIgnored = batteryIgnored,
                        overlayGranted = overlayGranted,
                        paused = paused,
                        onOpenAccessibility = vm::openAccessibilitySettings,
                        onTogglePause = vm::setPaused,
                        onRequestBattery = vm::requestBatteryWhitelist,
                        onOpenOem = vm::openOemBackgroundSettings,
                        onRequestOverlay = vm::requestOverlayPermission,
                    )

                    TAB_RULES -> RulesScreen(
                        settings = settings,
                        onToggleCountdown = vm::setCountdown,
                        onToggleViewId = vm::setViewId,
                        onToggleContentDesc = vm::setContentDescription,
                        onToggleGesture = vm::setGestureFallback,
                        onToggleAggressive = vm::setAggressive,
                        onToggleToast = vm::setShowToast,
                        onToggleClosePopups = vm::setClosePopups,
                        onToggleNotify = vm::setNotifySkip,
                        onToggleOcr = vm::setOcrEnabled,
                        onToggleTapAdLabel = vm::setTapAdLabel,
                        onToggleRunningNotification = vm::setShowRunningNotification,
                        onToggleAutoMute = vm::setAutoMuteVolume,
                        onToggleAutoSwipe = vm::setAutoSwipeFeed,
                        onToggleSplashOnly = vm::setSplashOnly,
                        onToggleFullscreenWebAds = vm::setSkipFullscreenWebAds,
                        onCooldownChange = vm::setCooldown,
                        onAddBlacklist = vm::addBlacklist,
                        onRemoveBlacklist = vm::removeBlacklist,
                        onAddKeyword = vm::addKeyword,
                        onRemoveKeyword = vm::removeKeyword,
                        onRewardModeChange = vm::setRewardMode,
                        overlayGranted = overlayGranted,
                        onRequestOverlay = vm::requestOverlayPermission,
                    )

                    TAB_LOGS -> LogsScreen(
                        logs = logs,
                        onClear = vm::clearLogs,
                        onDisableKeyword = vm::removeKeyword,
                    )

                    else -> AboutScreen()
                }
            }
        }
    }
}
