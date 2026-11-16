package com.toplevel.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Popup
import com.toplevel.app.data.SystemEvents
import com.toplevel.app.game.Leveling
import com.toplevel.app.ui.AppViewModel
import com.toplevel.app.ui.C
import com.toplevel.app.ui.CheckInDialog
import com.toplevel.app.ui.CoachScreen
import com.toplevel.app.ui.FoodScreen
import com.toplevel.app.ui.Fonts
import com.toplevel.app.ui.HomeScreen
import com.toplevel.app.ui.MoreScreen
import com.toplevel.app.ui.OnboardingScreen
import com.toplevel.app.ui.PopupHost
import com.toplevel.app.ui.SystemBackground
import com.toplevel.app.ui.TopLevelTheme
import com.toplevel.app.ui.TrainScreen
import com.toplevel.app.ui.glow
import com.toplevel.app.ui.glowStyle
import com.toplevel.app.ui.tap

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var openTarget by mutableStateOf<String?>(null)

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshCalendar(); vm.syncSteps() }

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
        // Calendar is read for appointments; write access only if the coach may add events
        permLauncher.launch(listOfNotNull(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR.takeIf { vm.prefs.calendarWrite },
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ).toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTarget = intent?.getStringExtra("open")
        com.toplevel.app.ui.C.applyTheme(vm.prefs.theme)
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
        com.toplevel.app.audio.Sound.startMusic(this)
    }

    override fun onPause() {
        super.onPause()
        com.toplevel.app.audio.Sound.pause()
    }
}

class Actions(
    val requestPerms: () -> Unit,
    val connectHealth: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
)

private data class Tab(val label: String, val icon: String)

private val tabs = listOf(
    Tab("Home", "◆"),
    Tab("Food", "◈"),
    Tab("Train", "⚔"),
    Tab("Journal", "▤"),
    Tab("Coach", "◎"),
)
private const val HOME = 0
private const val FOOD = 1
private const val TRAIN = 2
private const val JOURNAL = 3
private const val COACH = 4

@Composable
private fun Root(vm: AppViewModel, openTarget: String?, consumeTarget: () -> Unit, actions: Actions) {
    var tab by rememberSaveable { mutableIntStateOf(HOME) }
    // Pages opened from the menu, as a back stack: "menu/dungeons"
    var pages by rememberSaveable { mutableStateOf("") }
    val page = pages.substringAfterLast('/').ifBlank { null }
    var checkIn by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val totals by vm.statTotals.collectAsState(initial = null)

    fun open(id: String) {
        if (id in com.toplevel.app.ui.JournalTabs) { pages = ""; tab = JOURNAL; vm.journalTab = id; return }
        pages = if (pages.isBlank()) id else "$pages/$id"
    }
    fun back() { pages = pages.substringBeforeLast('/', "") }

    LaunchedEffect(openTarget) {
        when (openTarget) {
            "checkin" -> { tab = HOME; pages = ""; checkIn = true }
            "food" -> { tab = FOOD; pages = "" }
            "scan" -> { tab = FOOD; pages = ""; vm.pendingScan = true }
            "dungeon" -> { pages = ""; open("dungeons") }
            "status" -> { tab = HOME; pages = "" }
            else -> if (openTarget?.startsWith("more:") == true) { pages = ""; open(openTarget.substringAfter(":")) }
        }
        if (openTarget != null) consumeTarget()
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(totals) {
        val t = totals ?: return@LaunchedEffect
        val level = Leveling.levelFor(Leveling.totals(t).values.sum())
        if (level > vm.prefs.lastSeenLevel && vm.wizardDone) {
            SystemEvents.show(Popup(Popup.Kind.LEVEL_UP, "NOTIFICATION", "LV ${level - 1}  →  LV $level",
                lines = listOf("+${com.toplevel.app.data.Growth.POINTS_PER_LEVEL} stat points to spend", "Rank ${Leveling.rank(level)} · ${Leveling.title(level)}"),
                reward = "Keep going, Player."))
        }
        if (level != vm.prefs.lastSeenLevel) vm.prefs.lastSeenLevel = level
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        SystemBackground()

        if (!vm.wizardDone) {
            OnboardingScreen(vm, actions)
        } else Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = { NavBar(if (page == null) tab else -1) { pages = ""; tab = it } },
        ) { pad ->
            val wide = LocalConfiguration.current.screenWidthDp >= 600
            Box(Modifier.fillMaxSize().padding(pad)) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        if (page != null) {
                            MoreScreen(vm, actions, page, open = ::open, back = ::back)
                        } else AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                            when (t) {
                                HOME -> HomeScreen(vm, openFood = { tab = FOOD }, openCheckIn = { checkIn = true },
                                    openMore = ::open, connectHealth = actions.connectHealth)
                                FOOD -> FoodScreen(vm)
                                TRAIN -> TrainScreen(vm)
                                JOURNAL -> com.toplevel.app.ui.JournalTab(vm)
                                else -> CoachScreen(vm)
                            }
                        }
                    }
                    // Fold unfolded: coach always open on the right
                    if (wide && (tab != COACH || page != null)) {
                        Box(Modifier.width(1.dp).fillMaxHeight()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, C.accent.copy(alpha = 0.6f), Color.Transparent))))
                        Box(Modifier.weight(0.85f)) { CoachScreen(vm) }
                    }
                }
                if (vm.busy != null && tab != COACH) {
                    Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = C.accent, trackColor = C.bg)
                        Text("[ SYSTEM ] ${vm.busy}", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(C.bg.copy(alpha = 0.85f)).padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
        }

        if (checkIn) CheckInDialog(vm) { checkIn = false }

        PopupHost()
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(C.panel.copy(alpha = 0.92f), C.bg)))
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, C.accent.copy(alpha = 0.7f), Color.Transparent))))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).tap { onSelect(i) }.padding(vertical = 4.dp)
                        .semantics { contentDescription = t.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(26.dp).height(2.dp)
                            .then(if (on) Modifier.glow(C.accent, androidx.compose.ui.graphics.RectangleShape, radius = 8.dp, strength = 1f) else Modifier)
                            .background(if (on) C.accent else Color.Transparent)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(t.icon, color = if (on) Color.White else C.muted, fontSize = 20.sp,
                        style = if (on) glowStyle(C.accent, 16f) else glowStyle(Color.Transparent, 0f))
                    Text(t.label.uppercase(), color = if (on) C.accent else C.muted, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = Fonts.body)
                }
            }
        }
    }
}
