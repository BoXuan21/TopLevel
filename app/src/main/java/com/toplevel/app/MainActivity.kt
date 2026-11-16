package com.toplevel.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.game.Leveling
import com.toplevel.app.ui.AppViewModel
import com.toplevel.app.ui.C
import com.toplevel.app.ui.CheckInDialog
import com.toplevel.app.ui.CoachScreen
import com.toplevel.app.ui.LogScreen
import com.toplevel.app.ui.SetupScreen
import com.toplevel.app.ui.TrainScreen
import com.toplevel.app.ui.VaultScreen
import com.toplevel.app.ui.StatusScreen
import com.toplevel.app.ui.TopLevelTheme
import com.toplevel.app.ui.tap

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var openTarget by mutableStateOf<String?>(null)

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshCalendar() }

    private val healthLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { vm.onHealthPermissionResult() }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.backupTo(uri) }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.restoreFrom(uri) }

    private fun connectHealth() {
        if (!com.toplevel.app.health.HealthSync.available(this)) {
            vm.message = "Health Connect isn't available. Install/update it from the Play Store."
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")))
            } catch (_: Exception) { }
            return
        }
        healthLauncher.launch(com.toplevel.app.health.HealthSync.requestPermissions)
    }

    private fun backup() {
        backupLauncher.launch("toplevel-backup-${java.time.LocalDate.now()}.json")
    }

    private fun restore() {
        restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    private fun requestPerms() {
        permLauncher.launch(arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.POST_NOTIFICATIONS,
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTarget = intent?.getStringExtra("open")
        if (!vm.prefs.onboarded) {
            vm.prefs.onboarded = true
            requestPerms()
        }
        setContent {
            TopLevelTheme {
                Root(vm, openTarget, { openTarget = null },
                    Actions(::requestPerms, ::connectHealth, ::backup, ::restore))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTarget = intent.getStringExtra("open")
    }

    override fun onResume() {
        super.onResume()
        vm.onStart()
    }
}

class Actions(
    val requestPerms: () -> Unit,
    val connectHealth: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
)

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Status", Icons.Filled.Home),
    Tab("Train", Icons.Filled.Star),
    Tab("Log", Icons.Filled.List),
    Tab("Vault", Icons.Filled.Lock),
    Tab("Coach", Icons.Filled.Face),
)
private const val COACH = 4
private const val SETUP = 5

@Composable
private fun Root(vm: AppViewModel, openTarget: String?, consumeTarget: () -> Unit, actions: Actions) {
    var tab by remember { mutableIntStateOf(if (!vm.prefs.hasKey) SETUP else 0) }
    var checkIn by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val totals by vm.statTotals.collectAsState(initial = null)
    var levelUp by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(openTarget) {
        when (openTarget) {
            "checkin" -> { tab = 0; checkIn = true }
            "status" -> tab = 0
        }
        if (openTarget != null) consumeTarget()
    }
    LaunchedEffect(Unit) {
        com.toplevel.app.data.SystemEvents.flow.collect { snack.showSnackbar("[SYSTEM] $it") }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(totals) {
        val t = totals ?: return@LaunchedEffect
        val level = Leveling.levelFor(Leveling.totals(t).values.sum())
        if (level > vm.prefs.lastSeenLevel) levelUp = level
        if (level != vm.prefs.lastSeenLevel) vm.prefs.lastSeenLevel = level
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = C.bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = C.panel) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.accent,
                            selectedTextColor = C.accent,
                            indicatorColor = C.accent.copy(alpha = 0.15f),
                            unselectedIconColor = C.muted,
                            unselectedTextColor = C.muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        Box(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> StatusScreen(vm, openSetup = { tab = SETUP }) { checkIn = true }
                        1 -> TrainScreen(vm)
                        2 -> LogScreen(vm)
                        3 -> VaultScreen(vm)
                        COACH -> CoachScreen(vm)
                        else -> SetupScreen(vm, actions.requestPerms, actions.connectHealth, actions.backup, actions.restore)
                    }
                }
                // Fold unfolded: coach always open on the right
                if (wide && tab != COACH) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(C.border))
                    Box(Modifier.weight(0.85f)) { CoachScreen(vm) }
                }
            }
            if (vm.busy != null && tab != COACH) {
                Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = C.accent, trackColor = C.bg)
                    Text("[ SYSTEM ] ${vm.busy}", color = C.accent, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(C.bg.copy(alpha = 0.85f)).padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }

    if (checkIn) CheckInDialog(vm) { checkIn = false }

    AnimatedVisibility(visible = levelUp != null, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
        LevelUpOverlay(levelUp ?: 1) { levelUp = null }
    }
    }
}

@Composable
private fun LevelUpOverlay(level: Int, onDismiss: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).safeDrawingPadding().tap(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .border(2.dp, C.accent.copy(alpha = glow), RoundedCornerShape(16.dp))
                .background(C.panel, RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("[ SYSTEM ]", color = C.accent, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Text("LEVEL UP", color = C.text.copy(alpha = 0.5f + glow / 2), fontSize = 38.sp,
                fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Spacer(Modifier.height(8.dp))
            Text("LV ${level - 1}  →  LV $level", color = C.accentSoft, fontSize = 18.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Rank ${Leveling.rank(level)} · ${Leveling.title(level)}", color = C.muted, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            Text("Tap to continue", color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.alpha(glow))
        }
    }
}
