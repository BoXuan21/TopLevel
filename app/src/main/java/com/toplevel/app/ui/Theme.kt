package com.toplevel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Stat

object C {
    val bg = Color(0xFF070B14)
    val panel = Color(0xFF0E1628)
    val panel2 = Color(0xFF131E36)
    val border = Color(0xFF1F3A66)
    val accent = Color(0xFF3FA9FF)
    val accentSoft = Color(0xFF9BD8FF)
    val text = Color(0xFFE8F1FF)
    val muted = Color(0xFF7D8BA6)
    val good = Color(0xFF4BE3A0)
    val warn = Color(0xFFFFB547)
    val bad = Color(0xFFFF5C7A)

    fun stat(s: Stat) = when (s) {
        Stat.STRENGTH -> Color(0xFFFF6B6B)
        Stat.VITALITY -> Color(0xFF4BE3A0)
        Stat.INTELLECT -> Color(0xFF3FA9FF)
        Stat.DISCIPLINE -> Color(0xFFFFB547)
        Stat.SPIRIT -> Color(0xFFB98CFF)
    }
}

@Composable
fun TopLevelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = Color.Black,
            secondary = C.accentSoft,
            background = C.bg,
            surface = C.panel,
            surfaceVariant = C.panel2,
            onBackground = C.text,
            onSurface = C.text,
            onSurfaceVariant = C.muted,
            outline = C.border,
        ),
        content = content,
    )
}

/** The glowing "system window" panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(C.panel2, C.panel)))
            .border(BorderStroke(1.dp, C.border), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(),
                    color = C.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            Box(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 6) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(C.bg)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)))
        )
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
    placeholder: String? = null,
    password: Boolean = false,
    voice: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder, color = C.muted, fontSize = 13.sp) } } else null,
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        singleLine = singleLine,
        trailingIcon = if (voice) { { VoiceButton { t -> onChange(if (value.isBlank()) t else "$value $t") } } } else null,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent,
            unfocusedBorderColor = C.border,
            focusedLabelColor = C.accent,
            cursorColor = C.accent,
        ),
    )
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.dp, if (enabled) C.accent else C.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = C.accent),
        shape = RoundedCornerShape(10.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

val Gap = Arrangement.spacedBy(12.dp)

/** Simple tap modifier. */
fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/** Mic button: speech-to-text via the phone's speech recognizer. */
@Composable
fun VoiceButton(onText: (String) -> Unit) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val t = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) onText(t)
    }
    androidx.compose.material3.IconButton(onClick = {
        try {
            launcher.launch(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            )
        } catch (_: Exception) { }
    }) { Text("🎙", fontSize = 18.sp) }
}
