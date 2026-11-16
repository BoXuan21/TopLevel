package com.toplevel.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Popup
import com.toplevel.app.data.SystemEvents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Shows queued [SystemEvents] pop-ups one after another, on top of everything. */
@Composable
fun PopupHost() {
    val queue = remember { mutableStateListOf<Pair<Int, Popup>>() }
    var counter by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        SystemEvents.flow.collect { p ->
            // Don't pile up a wall of "quest complete" windows
            if (p.kind == Popup.Kind.QUEST) queue.removeAll { it.second.kind == Popup.Kind.QUEST && it != queue.firstOrNull() }
            queue.add(counter++ to p)
        }
    }
    val current = queue.firstOrNull() ?: return
    key(current.first) { SystemWindow(current.second) { queue.remove(current) } }
}

private class Spark(val x: Float, val speed: Float, val phase: Float, val r: Float, val wobble: Float)

private fun Popup.color(): Color = when (kind) {
    Popup.Kind.WARNING -> C.bad
    Popup.Kind.ACHIEVEMENT, Popup.Kind.LEVEL_UP -> C.shadow
    Popup.Kind.BOSS -> C.bad
    else -> C.accent
}

@Composable
private fun SystemWindow(p: Popup, onDone: () -> Unit) {
    val color = p.color()
    val scrim = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    val flicker = remember { Animatable(0f) }
    val rewardIn = remember { Animatable(0f) }
    var typed by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val autoClose = p.auto
    val sparks = remember {
        val rnd = Random(p.hashCode())
        List(30) { Spark(rnd.nextFloat(), 0.5f + rnd.nextFloat(), rnd.nextFloat(), 1f + rnd.nextFloat() * 2.2f, rnd.nextFloat()) }
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            open.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
            launch { scrim.animateTo(0f, tween(160)) }
            line.animateTo(0f, tween(140))
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        com.toplevel.app.audio.Sound.play(ctx, com.toplevel.app.audio.Sound.forPopup(p.kind))
        launch { scrim.animateTo(1f, tween(220)) }
        line.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
        launch { open.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f)) }
        launch {
            flicker.animateTo(1f, keyframes {
                durationMillis = 520
                0f at 0; 1f at 70; 0.15f at 140; 1f at 210; 0.4f at 300; 1f at 380
            })
        }
        delay(160)
        val n = p.body.length
        val step = when { n > 160 -> 6L; n > 80 -> 12L; else -> 22L }
        for (i in 1..n) { if (typed >= n) break; typed = i; delay(step) }
        typed = n
        rewardIn.animateTo(1f, tween(380))
        if (autoClose) { delay(2300); close() }
    }

    val inf = rememberInfiniteTransition(label = "popup")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "t")
    val pulse by inf.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val blink by inf.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.72f * scrim.value))
                drawRect(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.22f * scrim.value), Color.Transparent),
                        center = center, radius = size.minDimension * 0.75f,
                    )
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (typed < p.body.length) typed = p.body.length else close()
            },
        contentAlignment = Alignment.Center,
    ) {
        // The line of light the window unfolds from
        Box(
            Modifier
                .fillMaxWidth(0.9f * line.value)
                .height(2.dp)
                .alpha((1f - open.value).coerceIn(0f, 1f))
                .glow(color, androidx.compose.ui.graphics.RectangleShape, radius = 16.dp, strength = 1f)
                .background(Color.White)
        )

        Column(
            Modifier
                .padding(horizontal = 26.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleY = open.value.coerceAtLeast(0.001f)
                    scaleX = 0.96f + 0.04f * open.value.coerceIn(0f, 1f)
                    alpha = (open.value * 1.6f).coerceIn(0f, 1f)
                }
                .glow(color, WindowShape, radius = 22.dp, strength = pulse, width = 1.5.dp)
                .clip(WindowShape)
                .background(Brush.verticalGradient(listOf(Color(0xF00A1A3A), Color(0xF5040A18))))
                .drawBehind {
                    // scanlines
                    val gap = 4.dp.toPx()
                    var y = 0f
                    while (y < size.height) { drawLine(color.copy(alpha = 0.045f), Offset(0f, y), Offset(size.width, y), 1f); y += gap }
                    // sweeping scan bar
                    val sy = size.height * ((t * 1.7f) % 1f)
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.10f), Color.Transparent), startY = sy - 40f, endY = sy + 40f),
                        topLeft = Offset(0f, sy - 40f), size = androidx.compose.ui.geometry.Size(size.width, 80f),
                    )
                    // rising sparks
                    sparks.forEach { s ->
                        val prog = (t * s.speed + s.phase) % 1f
                        val px = size.width * s.x + sin((prog + s.wobble) * 2 * PI).toFloat() * 10.dp.toPx()
                        val py = size.height * (1f - prog)
                        val a = sin(prog * PI).toFloat()
                        drawCircle(color.copy(alpha = 0.18f * a), radius = s.r.dp.toPx() * 3f, center = Offset(px, py))
                        drawCircle(Color.White.copy(alpha = 0.75f * a), radius = s.r.dp.toPx() * 0.6f, center = Offset(px, py))
                    }
                }
                .drawWithContent {
                    drawContent()
                    // glowing corner brackets
                    val l = 18.dp.toPx(); val w = 2.dp.toPx(); val o = 7.dp.toPx()
                    val c = color.copy(alpha = 0.9f)
                    fun br(x: Float, y: Float, dx: Float, dy: Float) {
                        drawLine(c, Offset(x, y), Offset(x + dx * l, y), w)
                        drawLine(c, Offset(x, y), Offset(x, y + dy * l), w)
                    }
                    br(o, o, 1f, 1f); br(size.width - o, o, -1f, 1f)
                    br(o, size.height - o, 1f, -1f); br(size.width - o, size.height - o, -1f, -1f)
                }
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(flicker.value)) {
                Box(
                    Modifier.size(30.dp)
                        .glow(color, SmallShape, radius = 10.dp, strength = pulse)
                        .clip(SmallShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (p.kind == Popup.Kind.LEVEL_UP) "▲" else "!", color = Color.White, fontWeight = FontWeight.Black,
                        fontSize = 17.sp, style = glowStyle(color, 14f))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    p.title, color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Bold,
                    fontSize = if (p.title.length > 18) 15.sp else 18.sp, letterSpacing = 2.sp,
                    style = glowStyle(color, 20f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent)))
            )
            Spacer(Modifier.height(16.dp))

            if (p.kind == Popup.Kind.LEVEL_UP) {
                Text("LEVEL UP", color = Color.White, fontFamily = Fonts.display, fontWeight = FontWeight.Black,
                    fontSize = 38.sp, letterSpacing = 4.sp, style = glowStyle(color, 30f),
                    modifier = Modifier.alpha(0.6f + 0.4f * pulse))
                Spacer(Modifier.height(8.dp))
            }

            if (p.body.isNotEmpty()) {
                // Reserve the full height so the window doesn't jump while typing
                Box {
                    Text(p.body, color = Color.Transparent, fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold)
                    Text(p.body.take(typed) + if (typed < p.body.length) "▍" else "", color = C.text, fontSize = 18.sp,
                        lineHeight = 25.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
            }

            if (p.lines.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().alpha(rewardIn.value), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.lines.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Diamond(color, 7.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(l, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (p.reward.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    p.reward, color = C.warn, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    textAlign = TextAlign.Center, style = glowStyle(C.warn, 12f),
                    modifier = Modifier.alpha(rewardIn.value).graphicsLayer { translationY = (1f - rewardIn.value) * 12f },
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (autoClose) "" else "TAP TO CONTINUE", color = color, fontSize = 11.sp, letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.alpha(blink * rewardIn.value),
            )
        }
    }
}
